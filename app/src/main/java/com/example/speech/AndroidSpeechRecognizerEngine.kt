package com.example.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidSpeechRecognizerEngine(
    private val context: Context
) : SpeechRecognizerEngine {

    companion object {
        private const val TAG = "SpeechEngine"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialResult = MutableStateFlow("")
    override val partialResult: StateFlow<String> = _partialResult.asStateFlow()

    private val _finalResult = MutableStateFlow("")
    override val finalResult: StateFlow<String> = _finalResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    override val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    override fun isOfflineRecognitionAvailable(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            } catch (e: Exception) {
                false
            }
        } else {
            SpeechRecognizer.isRecognitionAvailable(context)
        }
    }

    override fun startListening(bcp47Tag: String, preferOfflineOnly: Boolean) {
        mainHandler.post {
            try {
                _errorMessage.value = null
                _partialResult.value = ""
                _finalResult.value = ""
                _rmsDb.value = 0f

                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    _errorMessage.value = "Speech recognition service is not available on this device."
                    _isListening.value = false
                    return@post
                }

                // Cleanup any existing recognizer before creating a new session.
                speechRecognizer?.destroy()
                speechRecognizer = null

                val onDeviceAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

                // Offline-only mode must not silently fall back to a network-backed recognizer.
                // EXTRA_PREFER_OFFLINE is only a preference; the standard recognizer may still
                // use a remote service.
                if (preferOfflineOnly && !onDeviceAvailable) {
                    _errorMessage.value =
                        "Offline speech recognition is not available on this device. Install the offline language model in your system speech settings."
                    _isListening.value = false
                    return@post
                }

                speechRecognizer = if (onDeviceAvailable) {
                    try {
                        SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed creating on-device recognizer", e)
                        _errorMessage.value =
                            "Unable to start on-device speech recognition. Please check your device's speech recognition service."
                        _isListening.value = false
                        return@post
                    }
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, bcp47Tag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, bcp47Tag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                speechRecognizer?.setRecognitionListener(createListener())
                speechRecognizer?.startListening(intent)
                _isListening.value = true
            }catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognition: ${e.message}", e)
                _errorMessage.value = "Failed to start speech recognition: ${e.localizedMessage ?: "Unknown error"}"
                _isListening.value = false
            }
        }
    }

    override fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping speech recognition: ${e.message}")
            }
            _isListening.value = false
            _rmsDb.value = 0f
        }
    }

    override fun cancel() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "Error canceling speech recognition: ${e.message}")
            }
            _isListening.value = false
            _rmsDb.value = 0f
        }
    }

    override fun release() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying speech recognizer: ${e.message}")
            }
            speechRecognizer = null
            _isListening.value = false
            _rmsDb.value = 0f
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _isListening.value = true
            }

            override fun onBeginningOfSpeech() {
                _isListening.value = true
            }

            override fun onRmsChanged(rmsdB: Float) {
                _rmsDb.value = rmsdB
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _isListening.value = false
                _rmsDb.value = 0f
            }

            override fun onError(errorCode: Int) {
                _isListening.value = false
                _rmsDb.value = 0f
                val message = mapErrorCodeToMessage(errorCode)
                Log.w(TAG, "Recognition error ($errorCode): $message")
                _errorMessage.value = message

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    errorCode == SpeechRecognizer.ERROR_SERVER_DISCONNECTED
                ) {
                    try {
                        speechRecognizer?.destroy()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error destroying disconnected recognizer: ${e.message}")
                    }
                    speechRecognizer = null
                }
            }

            override fun onResults(results: Bundle?) {
                _isListening.value = false
                _rmsDb.value = 0f
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    _finalResult.value = text
                    _partialResult.value = text
                } else {
                    _errorMessage.value = "No speech detected. Please try again."
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    _partialResult.value = matches[0]
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun mapErrorCodeToMessage(code: Int): String {
        return when (code) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Please check your microphone."
            SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
            SpeechRecognizer.ERROR_NETWORK -> "Offline speech model is unavailable for this language. Install the offline language's speech model and try again."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech model loading timed out. Please try again."
            SpeechRecognizer.ERROR_NO_MATCH -> "Speech could not be recognized. Please try again."\n            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "This language is not supported by the device's speech recognizer."\n            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "This language's offline speech model is not downloaded. Download it in system speech settings and try again."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition engine is busy. Please try again."
            SpeechRecognizer.ERROR_SERVER -> "On-device speech recognition service error."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Please try again."
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "Recognition service disconnected."
            else -> "Speech recognition error ($code). Please try again."
        }
    }
}
