package com.example.translation

import com.example.model.Language

interface TranslationEngine {
    suspend fun translate(
        text: String,
        source: Language,
        target: Language
    ): TranslationResult

    fun isModelInstalled(source: Language, target: Language): Boolean
}
