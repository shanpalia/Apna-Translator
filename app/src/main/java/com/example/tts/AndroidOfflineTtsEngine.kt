package com.example.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class AndroidOfflineTtsEngine(
    private val context: Context
) : OfflineTtsEngine, TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "OfflineTtsEngine"
    }

    private var tts: TextToSpeech? = null

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var onDoneCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing TextToSpeech: ${e.message}", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            _isInitialized.value = true
            setupProgressListener()
            Log.d(TAG, "TextToSpeech successfully initialized")
        } else {
            _isInitialized.value = false
            Log.e(TAG, "TextToSpeech initialization failed with status $status")
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                onDoneCallback?.invoke()
                onDoneCallback = null
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                onErrorCallback?.invoke("TTS playback encountered an error.")
                onErrorCallback = null
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
                val msg = when (errorCode) {
                    TextToSpeech.ERROR_NETWORK -> "Network required but app is in offline mode."
                    TextToSpeech.ERROR_NETWORK_TIMEOUT -> "Voice data network timeout."
                    TextToSpeech.ERROR_NOT_INSTALLED_YET -> "Offline voice data is not installed yet."
                    TextToSpeech.ERROR_SYNTHESIS -> "Speech synthesis failed."
                    else -> "TTS error (code $errorCode)"
                }
                onErrorCallback?.invoke(msg)
                onErrorCallback = null
            }
        })
    }

    override fun isLanguageAvailable(bcp47Tag: String): Boolean {
        val engine = tts ?: return false
        val locale = Locale.forLanguageTag(bcp47Tag)
        return try {
            val res = engine.isLanguageAvailable(locale)
            res >= TextToSpeech.LANG_AVAILABLE
        } catch (e: Exception) {
            false
        }
    }

    override fun isOfflineVoiceInstalled(bcp47Tag: String): Boolean {
        val engine = tts ?: return false
        val targetLocale = Locale.forLanguageTag(bcp47Tag)
        val targetLang = targetLocale.language

        try {
            val voices: Set<Voice>? = engine.voices
            if (!voices.isNullOrEmpty()) {
                val matchingVoice = voices.firstOrNull { voice ->
                    val voiceLangMatches = voice.locale.language.equals(targetLang, ignoreCase = true)
                    val isOffline = !voice.isNetworkConnectionRequired
                    voiceLangMatches && isOffline
                }
                if (matchingVoice != null) {
                    return true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking voices: ${e.message}")
        }

        // Fallback: check standard isLanguageAvailable status
        val status = engine.isLanguageAvailable(targetLocale)
        return status == TextToSpeech.LANG_AVAILABLE ||
                status == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                status == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
    }

    override fun speak(
        text: String,
        bcp47Tag: String,
        speed: Float,
        pitch: Float,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        val engine = tts
        if (engine == null || !_isInitialized.value) {
            onError("Speech synthesizer is not ready yet.")
            return
        }

        if (text.isBlank()) {
            onError("No text provided to speak.")
            return
        }

        val locale = Locale.forLanguageTag(bcp47Tag)
        val availability = engine.isLanguageAvailable(locale)
        if (availability < TextToSpeech.LANG_AVAILABLE) {
            onError("Offline voice is not installed for this language.")
            return
        }

        // Select offline voice if available
        try {
            val voices = engine.voices
            val offlineVoice = voices?.firstOrNull { voice ->
                voice.locale.language.equals(locale.language, ignoreCase = true) && !voice.isNetworkConnectionRequired
            }
            if (offlineVoice != null) {
                engine.voice = offlineVoice
            } else {
                engine.language = locale
            }
        } catch (e: Exception) {
            engine.language = locale
        }

        engine.setSpeechRate(speed.coerceIn(0.5f, 2.0f))
        engine.setPitch(pitch.coerceIn(0.5f, 2.0f))

        this.onDoneCallback = onDone
        this.onErrorCallback = onError

        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result == TextToSpeech.ERROR) {
            _isSpeaking.value = false
            onError("Failed to start speech output.")
        }
    }

    override fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS: ${e.message}")
        }
        _isSpeaking.value = false
    }

    override fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS: ${e.message}")
        }
        tts = null
        _isInitialized.value = false
        _isSpeaking.value = false
    }
}
