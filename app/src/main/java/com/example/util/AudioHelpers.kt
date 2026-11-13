package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var startTimeMillis: Long = 0L

    fun startRecording(): File? {
        cancelRecording()
        return try {
            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            currentOutputFile = file

            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mr
            startTimeMillis = System.currentTimeMillis()
            file
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "Failed to start recording", e)
            runCatching { recorder?.release() }
            recorder = null
            currentOutputFile?.delete()
            currentOutputFile = null
            null
        }
    }

    fun stopRecording(): Pair<File?, Int> {
        val mr = recorder ?: return Pair(null, 0)
        val file = currentOutputFile
        val elapsedMs = System.currentTimeMillis() - startTimeMillis
        recorder = null
        currentOutputFile = null

        return try {
            if (elapsedMs < 400L) {
                runCatching { mr.stop() }
                runCatching { mr.release() }
                file?.delete()
                return Pair(null, 0)
            }
            mr.stop()
            mr.release()
            val durationSec = (elapsedMs / 1000L).toInt().coerceAtLeast(1)
            if (file != null && file.exists() && file.length() > 0L) {
                Pair(file, durationSec)
            } else {
                file?.delete()
                Pair(null, 0)
            }
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "Failed to stop recording", e)
            runCatching { mr.release() }
            file?.delete()
            Pair(null, 0)
        }
    }

    fun cancelRecording() {
        val mr = recorder
        val file = currentOutputFile
        recorder = null
        currentOutputFile = null
        if (mr != null) {
            runCatching { mr.stop() }
            runCatching { mr.release() }
        }
        runCatching { file?.delete() }
    }
}

class VoicePlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPlayingId = MutableStateFlow<String?>(null)
    val currentPlayingId: StateFlow<String?> = _currentPlayingId.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    fun playVoice(
        id: String,
        filePathOrUrl: String,
        scope: CoroutineScope,
        onError: (String) -> Unit = {}
    ) {
        if (filePathOrUrl.isBlank()) {
            onError("ملف الصوت غير متوفر")
            return
        }

        if (_currentPlayingId.value == id && _isPlaying.value) {
            pause()
            return
        }

        if (_currentPlayingId.value == id && !_isPlaying.value && mediaPlayer != null) {
            try {
                mediaPlayer?.start()
                _isPlaying.value = true
                startProgressLoop(scope)
                return
            } catch (_: Exception) {
                stop()
            }
        }

        stop()
        _currentPlayingId.value = id
        _isPlaying.value = true
        _progress.value = 0f

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(filePathOrUrl)
                setOnPreparedListener { mp ->
                    if (_currentPlayingId.value == id) {
                        mp.start()
                        _isPlaying.value = true
                        startProgressLoop(scope)
                    } else {
                        runCatching { mp.release() }
                    }
                }
                setOnCompletionListener {
                    stop()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("VoicePlayer", "MediaPlayer error what=$what extra=$extra url=$filePathOrUrl")
                    stop()
                    onError("تعذر تشغيل الرسالة الصوتية")
                    true
                }
            }
            mediaPlayer = player
            player.prepareAsync()
        } catch (e: Exception) {
            Log.e("VoicePlayer", "Failed to prepare voice playback", e)
            stop()
            onError("تعذر تشغيل الرسالة الصوتية")
        }
    }

    private fun startProgressLoop(scope: CoroutineScope) {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val mp = mediaPlayer ?: break
                val playing = runCatching { mp.isPlaying }.getOrDefault(false)
                if (playing) {
                    val current = runCatching { mp.currentPosition }.getOrDefault(0)
                    val total = runCatching { mp.duration }.getOrDefault(1)
                    if (total > 0) {
                        _progress.value = (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    }
                } else if (!_isPlaying.value) {
                    break
                }
                delay(100)
            }
        }
    }

    fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (e: Exception) {
            Log.w("VoicePlayer", "Pause failed", e)
        }
        _isPlaying.value = false
        progressJob?.cancel()
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        val mp = mediaPlayer
        mediaPlayer = null
        if (mp != null) {
            runCatching {
                if (mp.isPlaying) mp.stop()
            }
            runCatching { mp.release() }
        }
        _isPlaying.value = false
        _currentPlayingId.value = null
        _progress.value = 0f
    }
}
