package com.example.speech

import kotlinx.coroutines.flow.StateFlow

interface SpeechRecognizerEngine {
    val isListening: StateFlow<Boolean>
    val partialResult: StateFlow<String>
    val finalResult: StateFlow<String>
    val errorMessage: StateFlow<String?>
    val rmsDb: StateFlow<Float> // for microphone wave animation

    fun isOfflineRecognitionAvailable(): Boolean
    fun startListening(bcp47Tag: String, preferOfflineOnly: Boolean = true)
    fun stopListening()
    fun cancel()
    fun release()
}
