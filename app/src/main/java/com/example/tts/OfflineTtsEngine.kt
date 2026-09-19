package com.example.tts

import kotlinx.coroutines.flow.StateFlow

interface OfflineTtsEngine {
    val isSpeaking: StateFlow<Boolean>
    val isInitialized: StateFlow<Boolean>

    fun isLanguageAvailable(bcp47Tag: String): Boolean
    fun isOfflineVoiceInstalled(bcp47Tag: String): Boolean
    fun speak(
        text: String,
        bcp47Tag: String,
        speed: Float = 1.0f,
        pitch: Float = 1.0f,
        onDone: () -> Unit = {},
        onError: (String) -> Unit = {}
    )
    fun stop()
    fun shutdown()
}
