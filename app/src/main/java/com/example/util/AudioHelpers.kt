package com.example.util

import android.content.Context
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
                setAudioEncodingBitRate(128000)
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
            recorder?.release()
            recorder = null
            currentOutputFile = null
            null
        }
    }

    fun stopRecording(): Pair<File?, Int> {
        return try {
            val durationSec = ((System.currentTimeMillis() - startTimeMillis) / 1000).toInt().coerceAtLeast(1)
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            val file = currentOutputFile
            currentOutputFile = null
            Pair(file, durationSec)
        } catch (e: Exception) {
            Log.e("VoiceRecorder", "Failed to stop recording", e)
            recorder?.release()
            recorder = null
            Pair(null, 0)
        }
    }

    fun cancelRecording() {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            // ignore
        } finally {
            recorder = null
            currentOutputFile?.delete()
            currentOutputFile = null
        }
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

    fun playVoice(id: String, filePathOrUrl: String, scope: CoroutineScope) {
        if (_currentPlayingId.value == id && _isPlaying.value) {
            pause()
            return
        }

        stop()
        _currentPlayingId.value = id
        _isPlaying.value = true

        try {
            val player = MediaPlayer()
            player.setDataSource(filePathOrUrl)
            player.prepare()
            player.start()
            mediaPlayer = player

            player.setOnCompletionListener {
                stop()
            }

            progressJob = scope.launch(Dispatchers.Main) {
                while (isActive && mediaPlayer?.isPlaying == true) {
                    val current = mediaPlayer?.currentPosition ?: 0
                    val total = mediaPlayer?.duration ?: 1
                    if (total > 0) {
                        _progress.value = current.toFloat() / total.toFloat()
                    }
                    delay(100)
                }
            }
        } catch (e: Exception) {
            Log.e("VoicePlayer", "Failed to play voice", e)
            stop()
        }
    }

    fun pause() {
        mediaPlayer?.pause()
        _isPlaying.value = false
        progressJob?.cancel()
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        _isPlaying.value = false
        _currentPlayingId.value = null
        _progress.value = 0f
    }
}
