package com.tejeet.carddrop.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Records the conversation at a stand. One recording at a time, written straight to an m4a in
 * app storage: 16 kHz mono AAC, which is what speech models want and costs about 4 KB a second,
 * so a ten-minute conversation is a couple of megabytes.
 *
 * The recording is kept with the card so there is always something to go back to, and Gemma can
 * summarise it on the phone (see CardAiVerifier.summariseConversation).
 */
object VoiceRecorder {

    data class Recording(val path: String, val durationMs: Long)

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    /** Milliseconds since recording started, for the timer in the UI. */
    fun elapsedMs(): Long = if (isRecording) System.currentTimeMillis() - startedAt else 0L

    @SuppressLint("MissingPermission")
    fun start(context: Context): Boolean {
        if (isRecording) return true
        val dir = File(context.filesDir, "recordings").apply { mkdirs() }
        val file = File(dir, "visit_${System.currentTimeMillis()}.m4a")
        return try {
            val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            created.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(16_000)
                setAudioEncodingBitRate(32_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = created
            currentFile = file
            startedAt = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            runCatching { recorder?.release() }
            recorder = null
            file.delete()
            currentFile = null
            false
        }
    }

    /** Stops and returns the finished recording, or null if it failed or was too short to keep. */
    fun stop(): Recording? {
        val active = recorder ?: return null
        val file = currentFile
        recorder = null
        currentFile = null
        val ok = runCatching {
            active.stop()
        }.isSuccess
        runCatching { active.release() }
        if (!ok || file == null || !file.exists() || file.length() < 1024) {
            file?.delete()
            return null
        }
        return Recording(file.absolutePath, durationOf(file.absolutePath))
    }

    /** Throws the in-progress recording away, e.g. when the user cancels the visit. */
    fun cancel() {
        val active = recorder ?: return
        recorder = null
        runCatching { active.stop() }
        runCatching { active.release() }
        currentFile?.delete()
        currentFile = null
    }

    fun durationOf(path: String): Long = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        }
    }.getOrDefault(0L)

    fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }
}

/** Plays one recording back on the review screen. */
object VoicePlayer {
    private var player: MediaPlayer? = null
    private var playingPath: String? = null

    fun isPlaying(path: String): Boolean = playingPath == path && player?.isPlaying == true

    fun toggle(path: String, onFinished: () -> Unit) {
        if (isPlaying(path)) {
            stop()
            return
        }
        stop()
        player = MediaPlayer().apply {
            runCatching {
                setDataSource(path)
                setOnCompletionListener {
                    stop()
                    onFinished()
                }
                prepare()
                start()
            }.onFailure {
                stop()
                onFinished()
            }
        }
        playingPath = path
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        playingPath = null
    }
}
