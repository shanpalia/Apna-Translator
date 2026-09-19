package com.example.translation

import com.example.model.Language

sealed class TranslationResult {
    data class Success(
        val sourceText: String,
        val translatedText: String,
        val sourceLang: Language,
        val targetLang: Language,
        val untranslatedTokens: List<String> = emptyList(),
        val isOffline: Boolean = true
    ) : TranslationResult()

    data class MissingModel(
        val sourceLang: Language,
        val targetLang: Language,
        val message: String = "Offline language pack required"
    ) : TranslationResult()

    data class Error(
        val message: String
    ) : TranslationResult()
}
