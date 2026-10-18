package com.tejeet.carddrop.ai

import android.content.Context
import com.tejeet.carddrop.data.SettingsStore
import java.io.File

/** Where downloaded models live, and whether one is fully downloaded. */
object AiModelFiles {
    private const val DIR = "models"

    fun file(context: Context, model: AiModel): File =
        File(context.getExternalFilesDir(DIR) ?: File(context.filesDir, DIR), model.fileName)

    /** A complete download only - DownloadManager writes to the same path while still in progress. */
    fun isReady(context: Context, model: AiModel): Boolean =
        file(context, model).let { it.exists() && it.length() == model.sizeBytes }

    fun selectedModel(context: Context): AiModel = AiModel.fromId(SettingsStore(context).aiModelId)

    fun selectedIsReady(context: Context): Boolean = isReady(context, selectedModel(context))
}

/** Drives ModelDownloadWorker and reports what the Settings screen should show. */
class AiModelDownloader(context: Context, private val model: AiModel) {
    private val appContext = context.applicationContext
    private val progress = ModelDownloadProgress(appContext)

    sealed class State {
        object NotDownloaded : State()
        data class Downloading(val downloadedBytes: Long, val totalBytes: Long, val paused: Boolean) : State()
        data class Failed(val message: String) : State()
        object Ready : State()
    }

    fun state(): State {
        if (AiModelFiles.isReady(appContext, model)) return State.Ready
        return when (progress.status(model)) {
            ModelDownloadProgress.RUNNING ->
                State.Downloading(progress.bytes(model), model.sizeBytes, paused = false)
            ModelDownloadProgress.FAILED ->
                State.Failed(progress.error(model).ifBlank { "Download failed - please try again." })
            else -> State.NotDownloaded
        }
    }

    /** Resumes from the part-file if one is there, so a dropped connection costs nothing. */
    fun start(wifiOnly: Boolean) {
        ModelDownloadWorker.start(appContext, model, wifiOnly)
    }

    /** Cancels the download and removes this model's files (complete or partial). */
    fun cancelAndDelete() {
        ModelDownloadWorker.cancel(appContext, model)
        progress.clear(model)
        AiModelFiles.file(appContext, model).delete()
        File(AiModelFiles.file(appContext, model).parentFile, model.fileName + ".part").delete()
    }
}
