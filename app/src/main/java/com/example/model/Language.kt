package com.example.model

import java.util.Locale

/**
 * Translation languages supported by Google ML Kit's on-device Translation API.
 * Speech recognition and text-to-speech availability can differ by device and language.
 */
data class Language(
    val code: String,
    val name: String,
    val nativeName: String,
    val bcp47Tag: String,
    val isRtl: Boolean = false
) {
    val locale: Locale
        get() = Locale.forLanguageTag(bcp47Tag)

    val displayName: String
        get() = "$name ($nativeName)"
}

object SupportedLanguages {
    val AFRIKAANS = Language("af", "Afrikaans", "Afrikaans", "af-ZA")
    val ARABIC = Language("ar", "Arabic", "العربية", "ar", isRtl = true)
    val BELARUSIAN = Language("be", "Belarusian", "Беларуская", "be-BY")
    val BULGARIAN = Language("bg", "Bulgarian", "Български", "bg-BG")
    val BENGALI = Language("bn", "Bengali", "বাংলা", "bn-IN")
    val CATALAN = Language("ca", "Catalan", "Català", "ca-ES")
    val CZECH = Language("cs", "Czech", "Čeština", "cs-CZ")
    val WELSH = Language("cy", "Welsh", "Cymraeg", "cy-GB")
    val DANISH = Language("da", "Danish", "Dansk", "da-DK")
    val GERMAN = Language("de", "German", "Deutsch", "de-DE")
    val GREEK = Language("el", "Greek", "Ελληνικά", "el-GR")
    val ENGLISH = Language("en", "English", "English", "en-US")
    val ESPERANTO = Language("eo", "Esperanto", "Esperanto", "eo")
    val SPANISH = Language("es", "Spanish", "Español", "es-ES")
    val ESTONIAN = Language("et", "Estonian", "Eesti", "et-EE")
    val PERSIAN = Language("fa", "Persian", "فارسی", "fa-IR", isRtl = true)
    val FINNISH = Language("fi", "Finnish", "Suomi", "fi-FI")
    val FRENCH = Language("fr", "French", "Français", "fr-FR")
    val IRISH = Language("ga", "Irish", "Gaeilge", "ga-IE")
    val GALICIAN = Language("gl", "Galician", "Galego", "gl-ES")
    val GUJARATI = Language("gu", "Gujarati", "ગુજરાતી", "gu-IN")
    val HEBREW = Language("he", "Hebrew", "עברית", "he-IL", isRtl = true)
    val HINDI = Language("hi", "Hindi", "हिन्दी", "hi-IN")
    val CROATIAN = Language("hr", "Croatian", "Hrvatski", "hr-HR")
    val HAITIAN = Language("ht", "Haitian Creole", "Kreyòl ayisyen", "ht")
    val HUNGARIAN = Language("hu", "Hungarian", "Magyar", "hu-HU")
    val INDONESIAN = Language("id", "Indonesian", "Bahasa Indonesia", "id-ID")
    val ICELANDIC = Language("is", "Icelandic", "Íslenska", "is-IS")
    val ITALIAN = Language("it", "Italian", "Italiano", "it-IT")
    val JAPANESE = Language("ja", "Japanese", "日本語", "ja-JP")
    val GEORGIAN = Language("ka", "Georgian", "ქართული", "ka-GE")
    val KANNADA = Language("kn", "Kannada", "ಕನ್ನಡ", "kn-IN")
    val KOREAN = Language("ko", "Korean", "한국어", "ko-KR")
    val LITHUANIAN = Language("lt", "Lithuanian", "Lietuvių", "lt-LT")
    val LATVIAN = Language("lv", "Latvian", "Latviešu", "lv-LV")
    val MACEDONIAN = Language("mk", "Macedonian", "Македонски", "mk-MK")
    val MARATHI = Language("mr", "Marathi", "मराठी", "mr-IN")
    val MALAY = Language("ms", "Malay", "Bahasa Melayu", "ms-MY")
    val MALTESE = Language("mt", "Maltese", "Malti", "mt-MT")
    val DUTCH = Language("nl", "Dutch", "Nederlands", "nl-NL")
    val NORWEGIAN = Language("no", "Norwegian", "Norsk", "no-NO")
    val POLISH = Language("pl", "Polish", "Polski", "pl-PL")
    val PORTUGUESE = Language("pt", "Portuguese", "Português", "pt-PT")
    val ROMANIAN = Language("ro", "Romanian", "Română", "ro-RO")
    val RUSSIAN = Language("ru", "Russian", "Русский", "ru-RU")
    val SLOVAK = Language("sk", "Slovak", "Slovenčina", "sk-SK")
    val SLOVENIAN = Language("sl", "Slovenian", "Slovenščina", "sl-SI")
    val ALBANIAN = Language("sq", "Albanian", "Shqip", "sq-AL")
    val SWEDISH = Language("sv", "Swedish", "Svenska", "sv-SE")
    val SWAHILI = Language("sw", "Swahili", "Kiswahili", "sw-KE")
    val TAMIL = Language("ta", "Tamil", "தமிழ்", "ta-IN")
    val TELUGU = Language("te", "Telugu", "తెలుగు", "te-IN")
    val THAI = Language("th", "Thai", "ไทย", "th-TH")
    val TAGALOG = Language("tl", "Tagalog", "Tagalog", "tl-PH")
    val TURKISH = Language("tr", "Turkish", "Türkçe", "tr-TR")
    val UKRAINIAN = Language("uk", "Ukrainian", "Українська", "uk-UA")
    val URDU = Language("ur", "Urdu", "اردو", "ur-PK", isRtl = true)
    val VIETNAMESE = Language("vi", "Vietnamese", "Tiếng Việt", "vi-VN")
    val CHINESE = Language("zh", "Chinese", "中文", "zh-CN")

    // Retained as named constants for compatibility with older history and code.
    // These are not listed in ALL because ML Kit Translation does not support them.
    val NEPALI = Language("ne", "Nepali", "नेपाली", "ne-NP")
    val PUNJABI = Language("pa", "Punjabi", "ਪੰਜਾਬੀ", "pa-IN")
    val MALAYALAM = Language("ml", "Malayalam", "മലയാളം", "ml-IN")

    val ALL: List<Language> = listOf(
        AFRIKAANS, ARABIC, BELARUSIAN, BULGARIAN, BENGALI, CATALAN, CZECH, WELSH,
        DANISH, GERMAN, GREEK, ENGLISH, ESPERANTO, SPANISH, ESTONIAN, PERSIAN,
        FINNISH, FRENCH, IRISH, GALICIAN, GUJARATI, HEBREW, HINDI, CROATIAN,
        HAITIAN, HUNGARIAN, INDONESIAN, ICELANDIC, ITALIAN, JAPANESE, GEORGIAN,
        KANNADA, KOREAN, LITHUANIAN, LATVIAN, MACEDONIAN, MARATHI, MALAY,
        MALTESE, DUTCH, NORWEGIAN, POLISH, PORTUGUESE, ROMANIAN, RUSSIAN,
        SLOVAK, SLOVENIAN, ALBANIAN, SWEDISH, SWAHILI, TAMIL, TELUGU, THAI,
        TAGALOG, TURKISH, UKRAINIAN, URDU, VIETNAMESE, CHINESE
    )

    fun findByCode(code: String): Language {
        return ALL.firstOrNull { it.code.equals(code, ignoreCase = true) }
            ?: when (code.lowercase()) {
                "ne" -> NEPALI
                "pa" -> PUNJABI
                "ml" -> MALAYALAM
                else -> ENGLISH
            }
    }
}
