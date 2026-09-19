package com.example.model

import java.util.Locale

/**
 * Represents a supported language in Apna Translator.
 * Supports readable native script, BCP-47 locale tag, and RTL layout support.
 */
data class Language(
    val code: String,          // Short code e.g. "hi", "en", "ur"
    val name: String,          // English name e.g. "Hindi"
    val nativeName: String,    // Native script e.g. "हिन्दी"
    val bcp47Tag: String,      // BCP-47 tag for SpeechRecognizer and TTS e.g. "hi-IN"
    val isRtl: Boolean = false // Right-to-left flag for Arabic, Urdu
) {
    val locale: Locale
        get() = Locale.forLanguageTag(bcp47Tag)

    val displayName: String
        get() = "$name ($nativeName)"
}

object SupportedLanguages {
    val HINDI = Language("hi", "Hindi", "हिन्दी", "hi-IN")
    val ENGLISH = Language("en", "English", "English", "en-US")
    val NEPALI = Language("ne", "Nepali", "नेपाली", "ne-NP")
    val ARABIC = Language("ar", "Arabic", "العربية", "ar", isRtl = true)
    val URDU = Language("ur", "Urdu", "اردو", "ur", isRtl = true)
    val BENGALI = Language("bn", "Bengali", "বাংলা", "bn-IN")
    val MARATHI = Language("mr", "Marathi", "मराठी", "mr-IN")
    val GUJARATI = Language("gu", "Gujarati", "ગુજરાતી", "gu-IN")
    val PUNJABI = Language("pa", "Punjabi", "ਪੰਜਾਬੀ", "pa-IN")
    val TAMIL = Language("ta", "Tamil", "தமிழ்", "ta-IN")
    val TELUGU = Language("te", "Telugu", "తెలుగు", "te-IN")
    val KANNADA = Language("kn", "Kannada", "ಕನ್ನಡ", "kn-IN")
    val MALAYALAM = Language("ml", "Malayalam", "മലയാളം", "ml-IN")

    val ALL: List<Language> = listOf(
        HINDI,
        ENGLISH,
        NEPALI,
        ARABIC,
        URDU,
        BENGALI,
        MARATHI,
        GUJARATI,
        PUNJABI,
        TAMIL,
        TELUGU,
        KANNADA,
        MALAYALAM
    )

    fun findByCode(code: String): Language {
        return ALL.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
    }
}
