package com.tejeet.carddrop.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads an AI model ourselves rather than via DownloadManager, which struggles with
 * Hugging Face's redirect-to-signed-CDN links when a flaky connection interrupts it.
 *
 * Writes to `<model>.part` and resumes with a Range request, so a dropped connection costs
 * nothing; the file is only renamed into place once its size matches exactly. Progress and
 * errors go to SharedPreferences, which is what the Settings screen reads.
 */
class ModelDownloadWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS) // a multi-hundred-MB download has no overall deadline
        .retryOnConnectionFailure(true)
        .build()

    override suspend fun doWork(): Result {
        val model = AiModel.fromId(inputData.getString(KEY_MODEL_ID))
        val progress = ModelDownloadProgress(applicationContext)
        val target = AiModelFiles.file(applicationContext, model)
        val part = File(target.parentFile, model.fileName + ".part")
        target.parentFile?.mkdirs()

        if (target.exists() && target.length() == model.sizeBytes) {
            progress.markDone(model)
            return Result.success()
        }

        // A foreground notification keeps long downloads alive; without notification permission
        // it just isn't shown, and the download still runs within WorkManager's normal limits.
        runCatching { setForeground(foregroundInfo(model, 0)) }

        return try {
            val alreadyHave = if (part.exists()) part.length() else 0L
            val request = Request.Builder()
                .url(model.downloadUrl)
                .apply { if (alreadyHave > 0) header("Range", "bytes=$alreadyHave-") }
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // 416 means our .part is stale (file changed) - start over next attempt.
                    if (response.code == 416) part.delete()
                    progress.markFailed(model, "Server returned HTTP ${response.code}")
                    return Result.failure()
                }
                val resuming = response.code == 206 && alreadyHave > 0
                if (!resuming) part.delete()
                val body = response.body ?: run {
                    progress.markFailed(model, "Empty response from the server")
                    return Result.failure()
                }

                var written = if (resuming) alreadyHave else 0L
                progress.markRunning(model, written)
                var lastReport = 0L

                body.source().use { source ->
                    part.sink(append = resuming).buffer().use { sink ->
                        val buffer = okio.Buffer()
                        while (true) {
                            val read = source.read(buffer, CHUNK)
                            if (read == -1L) break
                            sink.write(buffer, read)
                            written += read
                            val now = System.currentTimeMillis()
                            if (now - lastReport > 700) {
                                lastReport = now
                                progress.markRunning(model, written)
                                val percent = ((written * 100) / model.sizeBytes).toInt()
                                runCatching { setForeground(foregroundInfo(model, percent)) }
                            }
                            if (isStopped) {
                                // Cancelled or the system reclaimed us: keep .part for the next resume.
                                progress.markRunning(model, written)
                                return Result.failure()
                            }
                        }
                        sink.flush()
                    }
                }
            }

            if (part.length() != model.sizeBytes) {
                progress.markRunning(model, part.length())
                // Short file: the connection dropped. Retry and resume from where we stopped.
                return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else {
                    progress.markFailed(model, "Download stopped early - tap to resume")
                    Result.failure()
                }
            }
            target.delete()
            if (!part.renameTo(target)) {
                progress.markFailed(model, "Couldn't save the model file")
                return Result.failure()
            }
            progress.markDone(model)
            Result.success()
        } catch (e: IOException) {
            progress.markRunning(model, if (part.exists()) part.length() else 0L)
            if (runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                progress.markFailed(model, e.message ?: "Network error")
                Result.failure()
            }
        } catch (e: Exception) {
            progress.markFailed(model, e.message ?: "Unexpected error")
            Result.failure()
        }
    }

    private fun foregroundInfo(model: AiModel, percent: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Model download", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Downloading ${model.displayName}")
            .setContentText("$percent% of ${model.sizeLabel}")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, percent, percent == 0)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val KEY_MODEL_ID = "model_id"
        private const val CHANNEL_ID = "model_download"
        private const val NOTIFICATION_ID = 4301
        private const val MAX_ATTEMPTS = 8
        private const val CHUNK = 128L * 1024L

        private fun workName(model: AiModel) = "model-download-${model.id}"

        fun start(context: Context, model: AiModel, wifiOnly: Boolean) {
            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 20, TimeUnit.SECONDS)
                .setInputData(Data.Builder().putString(KEY_MODEL_ID, model.id).build())
                .build()
            ModelDownloadProgress(context).markRunning(model, ModelDownloadProgress(context).bytes(model))
            WorkManager.getInstance(context).enqueueUniqueWork(workName(model), ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context, model: AiModel) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(model))
        }
    }
}

/** The bit of download state the Settings screen reads: how far along, and why it stopped. */
class ModelDownloadProgress(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("carddrop_model", Context.MODE_PRIVATE)

    fun bytes(model: AiModel): Long = prefs.getLong(key(model, "bytes"), 0L)
    fun status(model: AiModel): String = prefs.getString(key(model, "status"), "").orEmpty()
    fun error(model: AiModel): String = prefs.getString(key(model, "error"), "").orEmpty()

    fun markRunning(model: AiModel, bytes: Long) = prefs.edit()
        .putString(key(model, "status"), RUNNING)
        .putLong(key(model, "bytes"), bytes)
        .remove(key(model, "error"))
        .apply()

    fun markFailed(model: AiModel, message: String) = prefs.edit()
        .putString(key(model, "status"), FAILED)
        .putString(key(model, "error"), message)
        .apply()

    fun markDone(model: AiModel) = prefs.edit()
        .putString(key(model, "status"), DONE)
        .putLong(key(model, "bytes"), model.sizeBytes)
        .remove(key(model, "error"))
        .apply()

    fun clear(model: AiModel) = prefs.edit()
        .remove(key(model, "status"))
        .remove(key(model, "bytes"))
        .remove(key(model, "error"))
        .apply()

    private fun key(model: AiModel, suffix: String) = "dl_${model.id}_$suffix"

    companion object {
        const val RUNNING = "running"
        const val FAILED = "failed"
        const val DONE = "done"
    }
}
