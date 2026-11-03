package com.englishfluency.ai.audio

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _livePartialTranscript = MutableStateFlow("")
    val livePartialTranscript: StateFlow<String> = _livePartialTranscript.asStateFlow()

    private val _playingAudioPath = MutableStateFlow<String?>(null)
    val playingAudioPath: StateFlow<String?> = _playingAudioPath.asStateFlow()

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
        if (!file.exists()) return
        if (_playingAudioPath.value == path) {
            stopAudioPlayback()
            return
        }
        stopSpeaking()
        runCatching {
            val player = MediaPlayer().apply {
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

    fun startPushToTalkRecording(onTranscriptResult: (String) -> Unit) {
        stopSpeaking()
        _livePartialTranscript.value = ""
        _isRecording.value = true

        // 1. Save recorded voice in persistent recordings folder so user can replay their own voice anytime
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
            recorder.setAudioSamplingRate(16000)
            recorder.setAudioEncodingBitRate(64000)
            recorder.setOutputFile(outFile.absolutePath)
            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
        }.onFailure {
            mediaRecorder = null
        }

        // 2. Also start Android SpeechRecognizer if available on device for instant live transcript preview
        runCatching {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer?.destroy()
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {}

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val best = matches?.firstOrNull().orEmpty()
                        if (best.isNotBlank()) {
                            _livePartialTranscript.value = best
                            onTranscriptResult(best)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull().orEmpty()
                        if (partial.isNotBlank()) {
                            _livePartialTranscript.value = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-GB")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
                recognizer.startListening(intent)
                speechRecognizer = recognizer
            }
        }
    }

    fun stopPushToTalkRecording(): Pair<File?, String> {
        _isRecording.value = false
        runCatching {
            speechRecognizer?.stopListening()
        }
        runCatching {
            mediaRecorder?.stop()
        }
        runCatching {
            mediaRecorder?.release()
        }
        mediaRecorder = null
        return currentAudioFile to _livePartialTranscript.value
    }

    fun release() {
        stopAudioPlayback()
        runCatching { speechRecognizer?.destroy() }
        runCatching { mediaRecorder?.release() }
        runCatching { tts?.shutdown() }
    }
}
