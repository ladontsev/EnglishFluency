package com.englishfluency.ai.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale

class VoiceSessionController(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentAudioFile: File? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _livePartialTranscript = MutableStateFlow("")
    val livePartialTranscript: StateFlow<String> = _livePartialTranscript.asStateFlow()

    private val _playingAudioPath = MutableStateFlow<String?>(null)
    val playingAudioPath: StateFlow<String?> = _playingAudioPath.asStateFlow()

    private val _lastRecordedAudioPath = MutableStateFlow<String?>(null)
    val lastRecordedAudioPath: StateFlow<String?> = _lastRecordedAudioPath.asStateFlow()

    private val _speechRate = MutableStateFlow(0.96f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val ukResult = tts?.setLanguage(Locale.UK)
                if (ukResult == TextToSpeech.LANG_MISSING_DATA || ukResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.US)
                }
                tts?.setSpeechRate(_speechRate.value)
                ttsReady = true
            }
        }
    }

    fun setTtsSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.65f, 1.25f)
        _speechRate.value = clamped
        if (ttsReady) {
            tts?.setSpeechRate(clamped)
        }
    }

    fun speakTutorText(text: String, customRate: Float? = null) {
        if (!ttsReady) return
        stopAudioPlayback()
        tts?.setSpeechRate(customRate ?: _speechRate.value)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tutor_utt_${System.currentTimeMillis()}")
    }

    fun stopSpeaking() {
        tts?.stop()
        stopAudioPlayback()
    }

    fun playRecordedFile(path: String) {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return
        if (_playingAudioPath.value == path) {
            stopAudioPlayback()
            return
        }
        stopSpeaking()
        runCatching {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    _playingAudioPath.value = null
                    it.release()
                }
                setOnErrorListener { mp, _, _ ->
                    _playingAudioPath.value = null
                    mp.release()
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = player
            _playingAudioPath.value = path
        }.onFailure {
            _playingAudioPath.value = null
        }
    }

    fun stopAudioPlayback() {
        runCatching {
            mediaPlayer?.stop()
        }
        runCatching {
            mediaPlayer?.release()
        }
        mediaPlayer = null
        _playingAudioPath.value = null
    }

    /**
     * Starts clean exclusive microphone recording to an .m4a file without SpeechRecognizer
     * stealing mic focus on Android devices. Gemini transcribes & evaluates directly from .m4a.
     */
    fun startPushToTalkRecording(onTranscriptResult: (String) -> Unit = {}) {
        stopSpeaking()
        _livePartialTranscript.value = ""

        val recDir = File(context.filesDir, "recordings").apply { mkdirs() }
        val outFile = File(recDir, "ptt_${System.currentTimeMillis()}.m4a")
        currentAudioFile = outFile

        runCatching {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioSamplingRate(44100)
            recorder.setAudioEncodingBitRate(96000)
            recorder.setOutputFile(outFile.absolutePath)
            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
            _isRecording.value = true
        }.onFailure {
            mediaRecorder = null
            _isRecording.value = false
        }
    }

    fun stopPushToTalkRecording(): Pair<File?, String> {
        _isRecording.value = false
        runCatching {
            mediaRecorder?.stop()
        }
        runCatching {
            mediaRecorder?.release()
        }
        mediaRecorder = null

        val recordedFile = currentAudioFile?.takeIf { it.exists() && it.length() > 100L }
        if (recordedFile != null) {
            _lastRecordedAudioPath.value = recordedFile.absolutePath
        }
        return recordedFile to _livePartialTranscript.value
    }

    fun release() {
        stopAudioPlayback()
        runCatching { mediaRecorder?.release() }
        runCatching { tts?.shutdown() }
    }
}
