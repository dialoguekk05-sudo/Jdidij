package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.data.preferences.JarvisLanguageMode
import com.example.data.preferences.VoiceGender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

enum class VoiceState {
    IDLE,
    WAKE_LISTENING,
    LISTENING,
    SPEAKING
}

class JarvisVoiceManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _voiceError = MutableStateFlow<String?>(null)
    val voiceError: StateFlow<String?> = _voiceError.asStateFlow()

    private var currentLanguageMode: JarvisLanguageMode = JarvisLanguageMode.BILINGUAL
    private var currentGender: VoiceGender = VoiceGender.MALE
    private var currentRate: Float = 1.0f
    private var currentPitch: Float = 1.0f
    private var continuousMode: Boolean = false
    private var wakeWordMode: Boolean = false

    private var onCommandRecognized: ((String) -> Unit)? = null
    private var onWakeWordTriggered: ((String?) -> Unit)? = null

    private var isListeningForWakeWord = false

    init {
        mainHandler.post { initTts() }
    }

    fun setCallbacks(
        onCommand: (String) -> Unit,
        onWakeWord: (String?) -> Unit
    ) {
        onCommandRecognized = onCommand
        onWakeWordTriggered = onWakeWord
    }

    fun updateSettings(
        languageMode: JarvisLanguageMode,
        gender: VoiceGender,
        rate: Float,
        pitch: Float,
        continuous: Boolean,
        wakeWord: Boolean
    ) {
        currentLanguageMode = languageMode
        currentGender = gender
        currentRate = rate
        currentPitch = pitch
        continuousMode = continuous
        val wakeChanged = wakeWordMode != wakeWord
        wakeWordMode = wakeWord
        applyTtsVoiceSettings()

        if (wakeChanged) {
            if (wakeWord && _voiceState.value == VoiceState.IDLE) {
                startWakeWordLoop()
            } else if (!wakeWord && _voiceState.value == VoiceState.WAKE_LISTENING) {
                stopListening()
            }
        }
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    applyTtsVoiceSettings()
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            mainHandler.post { _voiceState.value = VoiceState.SPEAKING }
                        }

                        override fun onDone(utteranceId: String?) {
                            mainHandler.post {
                                if (_voiceState.value == VoiceState.SPEAKING) {
                                    _voiceState.value = VoiceState.IDLE
                                }
                                if (continuousMode) {
                                    mainHandler.postDelayed({ startCommandListening() }, 350L)
                                } else if (wakeWordMode) {
                                    mainHandler.postDelayed({ startWakeWordLoop() }, 450L)
                                }
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            mainHandler.post { _voiceState.value = VoiceState.IDLE }
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            ttsReady = false
            _voiceError.value = "Text-to-Speech unavailable: ${t.localizedMessage ?: "error"}"
        }
    }

    private fun applyTtsVoiceSettings() {
        val engine = tts ?: return
        if (!ttsReady) return

        val targetLocale = when (currentLanguageMode) {
            JarvisLanguageMode.HINDI, JarvisLanguageMode.BILINGUAL -> Locale("hi", "IN")
            JarvisLanguageMode.ENGLISH -> Locale("en", "IN")
        }
        val langResult = engine.setLanguage(targetLocale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.US)
        }

        val voices = runCatching { engine.voices?.toList().orEmpty() }.getOrDefault(emptyList())
        val matchedVoice = selectBestVoice(voices, targetLocale, currentGender)
        if (matchedVoice != null) {
            engine.voice = matchedVoice
        }

        val genderPitchBias = if (currentGender == VoiceGender.MALE) 0.88f else 1.12f
        engine.setPitch((currentPitch * genderPitchBias).coerceIn(0.6f, 1.5f))
        engine.setSpeechRate(currentRate.coerceIn(0.6f, 1.6f))
    }

    private fun selectBestVoice(
        voices: List<Voice>,
        locale: Locale,
        gender: VoiceGender
    ): Voice? {
        val langVoices = voices.filter {
            it.locale.language.equals(locale.language, ignoreCase = true)
        }.ifEmpty { voices }

        val genderTokens = if (gender == VoiceGender.FEMALE) {
            listOf("female", "f00", "hia", "hid", "sfg", "iob", "wavenet-a", "wavenet-d")
        } else {
            listOf("male", "m00", "hib", "hie", "iom", "wavenet-b", "wavenet-c")
        }

        return langVoices.firstOrNull { v ->
            val lower = v.name.lowercase()
            genderTokens.any { token -> lower.contains(token) }
        } ?: langVoices.firstOrNull()
    }

    fun speak(text: String) {
        val clean = text
            .replace(Regex("```[\\s\\S]*?```"), "Code block generated.")
            .replace(Regex("[*_`#>]"), "")
            .trim()
        if (clean.isBlank()) return

        // Dynamically switch TTS locale if message has Devanagari characters
        if (ttsReady) {
            val hasHindi = clean.any { it in '\u0900'..'\u097F' }
            val locale = if (hasHindi) Locale("hi", "IN") else Locale("en", "IN")
            val res = tts?.setLanguage(locale)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
            val voices = runCatching { tts?.voices?.toList().orEmpty() }.getOrDefault(emptyList())
            selectBestVoice(voices, locale, currentGender)?.let { tts?.voice = it }
            val genderBias = if (currentGender == VoiceGender.MALE) 0.88f else 1.12f
            tts?.setPitch((currentPitch * genderBias).coerceIn(0.6f, 1.5f))
            tts?.setSpeechRate(currentRate.coerceIn(0.6f, 1.6f))
            tts?.speak(clean.take(1800), TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        }
    }

    fun stopSpeaking() {
        tts?.stop()
        if (_voiceState.value == VoiceState.SPEAKING) {
            _voiceState.value = VoiceState.IDLE
        }
    }

    fun startCommandListening() {
        startRecognizer(forWakeWord = false)
    }

    fun startWakeWordLoop() {
        if (!wakeWordMode) return
        startRecognizer(forWakeWord = true)
    }

    fun stopListening() {
        mainHandler.post {
            isListeningForWakeWord = false
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
            _rmsDb.value = 0f
            _partialTranscript.value = ""
            if (_voiceState.value != VoiceState.SPEAKING) {
                _voiceState.value = VoiceState.IDLE
            }
        }
    }

    private fun startRecognizer(forWakeWord: Boolean) {
        mainHandler.post {
            _voiceError.value = null
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                _voiceError.value = "Speech recognition service is unavailable on this device."
                _voiceState.value = VoiceState.IDLE
                return@post
            }
            stopSpeaking()
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
            isListeningForWakeWord = forWakeWord
            _partialTranscript.value = ""
            _voiceState.value = if (forWakeWord) VoiceState.WAKE_LISTENING else VoiceState.LISTENING

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    _rmsDb.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _rmsDb.value = 0f
                }

                override fun onError(error: Int) {
                    _rmsDb.value = 0f
                    if (isListeningForWakeWord && wakeWordMode) {
                        // Gently restart passive wake-word loop without spamming errors
                        mainHandler.postDelayed({
                            if (wakeWordMode && _voiceState.value == VoiceState.WAKE_LISTENING) {
                                startWakeWordLoop()
                            }
                        }, 600L)
                    } else {
                        _voiceState.value = VoiceState.IDLE
                        if (error != SpeechRecognizer.ERROR_CLIENT && error != SpeechRecognizer.ERROR_NO_MATCH) {
                            _voiceError.value = mapSpeechError(error)
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    _rmsDb.value = 0f
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = matches?.firstOrNull().orEmpty().trim()
                    if (isListeningForWakeWord) {
                        handleWakeWordCandidate(best)
                    } else {
                        _voiceState.value = VoiceState.IDLE
                        _partialTranscript.value = ""
                        if (best.isNotBlank()) {
                            onCommandRecognized?.invoke(best)
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (!isListeningForWakeWord) {
                        _partialTranscript.value = partial
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguageMode.speechLocale)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            speechRecognizer?.startListening(intent)
        }
    }

    private fun handleWakeWordCandidate(spoken: String) {
        val lower = spoken.lowercase()
        val wakeRegex = Regex("(hey\\s+jarvis|ok\\s+jarvis|jarvis|हे\\s+जार्विस|जार्विस)")
        val match = wakeRegex.find(lower)
        if (match != null) {
            val trailingCommand = spoken.substring(match.range.last + 1).trim()
                .takeIf { it.length >= 2 }
            _voiceState.value = VoiceState.IDLE
            onWakeWordTriggered?.invoke(trailingCommand)
            if (trailingCommand == null) {
                mainHandler.postDelayed({ startCommandListening() }, 250L)
            }
        } else if (wakeWordMode) {
            mainHandler.postDelayed({ startWakeWordLoop() }, 350L)
        } else {
            _voiceState.value = VoiceState.IDLE
        }
    }

    private fun mapSpeechError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Speech recognition needs network connectivity."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap mic to retry."
        else -> "Voice recognition unavailable (code $code)."
    }

    fun release() {
        stopListening()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
