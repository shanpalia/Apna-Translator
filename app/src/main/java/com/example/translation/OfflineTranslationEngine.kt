package com.example.translation

import android.util.Log
import com.example.model.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class OfflineTranslationEngine(
    private val languagePackManager: LanguagePackManager
) : TranslationEngine {

    companion object {
        private const val TAG = "OfflineTranslation"
    }

    private data class CachedModel(
        val id: String,
        val source: String,
        val target: String,
        val phrases: Map<String, String>,
        val lexicon: Map<String, String>,
        val wordOrder: String
    )

    private val modelCache = ConcurrentHashMap<String, CachedModel>()

    override fun isModelInstalled(source: Language, target: Language): Boolean {
        if (source.code.equals(target.code, ignoreCase = true)) return true
        return languagePackManager.isLanguagePairAvailable(source.code, target.code)
    }

    override suspend fun translate(
        text: String,
        source: Language,
        target: Language
    ): TranslationResult = withContext(Dispatchers.Default) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return@withContext TranslationResult.Success(
                sourceText = "",
                translatedText = "",
                sourceLang = source,
                targetLang = target
            )
        }

        if (source.code.equals(target.code, ignoreCase = true)) {
            return@withContext TranslationResult.Success(
                sourceText = trimmed,
                translatedText = trimmed,
                sourceLang = source,
                targetLang = target
            )
        }

        val pairId = "${source.code.lowercase()}_${target.code.lowercase()}"
        val modelFile = languagePackManager.getModelFile(pairId)

        if (modelFile == null || !modelFile.exists() || modelFile.length() == 0L) {
            return@withContext TranslationResult.MissingModel(
                sourceLang = source,
                targetLang = target,
                message = "Offline translation model for ${source.name} → ${target.name} is not installed."
            )
        }

        try {
            val model = loadOrGetModel(pairId, modelFile)
            val translated = translateWithModel(trimmed, model)
            return@withContext TranslationResult.Success(
                sourceText = trimmed,
                translatedText = translated.resultText,
                sourceLang = source,
                targetLang = target,
                untranslatedTokens = translated.untranslated
            )
        } catch (e: Exception) {
            Log.e(TAG, "Translation error for $pairId: ${e.message}", e)
            return@withContext TranslationResult.Error("Offline translation failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    private fun loadOrGetModel(pairId: String, file: File): CachedModel {
        modelCache[pairId]?.let { return it }

        val jsonStr = file.readText(Charsets.UTF_8)
        val json = JSONObject(jsonStr)
        val phrasesObj = json.optJSONObject("phrases") ?: JSONObject()
        val lexiconObj = json.optJSONObject("lexicon") ?: JSONObject()
        val wordOrder = json.optString("wordOrder", "SAME")

        val phrasesMap = mutableMapOf<String, String>()
        val pKeys = phrasesObj.keys()
        while (pKeys.hasNext()) {
            val key = pKeys.next()
            phrasesMap[key.trim().lowercase()] = phrasesObj.getString(key)
        }

        val lexiconMap = mutableMapOf<String, String>()
        val lKeys = lexiconObj.keys()
        while (lKeys.hasNext()) {
            val key = lKeys.next()
            lexiconMap[key.trim().lowercase()] = lexiconObj.getString(key)
        }

        val model = CachedModel(
            id = pairId,
            source = json.optString("source", ""),
            target = json.optString("target", ""),
            phrases = phrasesMap,
            lexicon = lexiconMap,
            wordOrder = wordOrder
        )

        modelCache[pairId] = model
        return model
    }

    private data class TranslationOutput(
        val resultText: String,
        val untranslated: List<String>
    )

    private fun translateWithModel(input: String, model: CachedModel): TranslationOutput {
        val normalizedInput = input.trim()
        val lookupKey = normalizedInput.lowercase()

        // 1. Direct phrase lookup
        model.phrases[lookupKey]?.let {
            return TranslationOutput(it, emptyList())
        }

        // Check without trailing punctuation
        val cleanKey = lookupKey.trimEnd('.', '?', '!', '।', '؟')
        model.phrases[cleanKey]?.let { match ->
            val ending = when {
                input.endsWith('?') || input.endsWith('؟') -> if (model.target == "ur" || model.target == "ar") "؟" else "?"
                input.endsWith('!') -> "!"
                input.endsWith('।') || input.endsWith('.') -> if (model.target == "hi" || model.target == "ne") "।" else "."
                else -> ""
            }
            return TranslationOutput(match + ending, emptyList())
        }

        // 2. Token-by-token and multi-token greedy translation
        val tokens = tokenize(normalizedInput)
        val translatedTokens = mutableListOf<String>()
        val untranslated = mutableListOf<String>()

        var i = 0
        while (i < tokens.size) {
            var matched = false

            // Try 3-word phrase
            if (i + 2 < tokens.size) {
                val phrase3 = "${tokens[i]} ${tokens[i+1]} ${tokens[i+2]}".lowercase()
                val t3 = model.phrases[phrase3] ?: model.lexicon[phrase3]
                if (t3 != null) {
                    translatedTokens.add(t3)
                    i += 3
                    matched = true
                }
            }

            // Try 2-word phrase
            if (!matched && i + 1 < tokens.size) {
                val phrase2 = "${tokens[i]} ${tokens[i+1]}".lowercase()
                val t2 = model.phrases[phrase2] ?: model.lexicon[phrase2]
                if (t2 != null) {
                    translatedTokens.add(t2)
                    i += 2
                    matched = true
                }
            }

            // Single word translation
            if (!matched) {
                val token = tokens[i]
                val cleanToken = token.lowercase().trimEnd('.', '?', '!', '।', '؟', ',')
                val punctuation = token.takeLastWhile { it in ".,!?।؟" }

                val single = model.lexicon[cleanToken]
                if (single != null) {
                    val adjustedPunctuation = when {
                        punctuation.contains('?') || punctuation.contains('؟') -> if (model.target == "ur" || model.target == "ar") "؟" else "?"
                        punctuation.contains('!') -> "!"
                        punctuation.contains('.') || punctuation.contains('।') -> if (model.target == "hi" || model.target == "ne") "।" else "."
                        else -> punctuation
                    }
                    translatedTokens.add(single + adjustedPunctuation)
                } else {
                    // Unknown to model dictionary: keep original token and track
                    translatedTokens.add(token)
                    untranslated.add(token)
                }
                i++
            }
        }

        // Word order realignment if needed (e.g., SOV to SVO or SVO to SOV)
        val orderedTokens = applyWordOrder(translatedTokens, model.wordOrder)
        val result = orderedTokens.joinToString(" ")
        return TranslationOutput(result, untranslated)
    }

    private fun applyWordOrder(tokens: List<String>, order: String): List<String> {
        if (tokens.size <= 2) return tokens
        // For basic sentence structures when converting SOV to SVO:
        // In Hindi/Urdu, the auxiliary/verb is at the end (e.g., Subject + Object + Verb).
        // In English, it is Subject + Verb + Object.
        return when (order) {
            "SOV_TO_SVO" -> {
                // If last token is a verb/auxiliary (e.g. is, are, am, want), move after subject
                val verbs = setOf("is", "are", "am", "was", "were", "want", "like", "need", "go", "come")
                val lastIndex = tokens.size - 1
                val last = tokens[lastIndex].lowercase().trimEnd('.', '?', '!')
                if (last in verbs && tokens.size >= 3) {
                    val list = tokens.toMutableList()
                    val verbToken = list.removeAt(lastIndex)
                    list.add(1, verbToken)
                    list
                } else {
                    tokens
                }
            }
            "SVO_TO_SOV" -> {
                // If second token is a verb/auxiliary (e.g. है, हूँ, हैं), move to end
                val hindiVerbs = setOf("है", "हूँ", "हैं", "था", "थी", "थे", "चाहिए", "पसंद")
                val second = tokens.getOrNull(1)?.lowercase()?.trimEnd('.', '?', '!')
                if (second != null && second in hindiVerbs && tokens.size >= 3) {
                    val list = tokens.toMutableList()
                    val verbToken = list.removeAt(1)
                    list.add(verbToken)
                    list
                } else {
                    tokens
                }
            }
            else -> tokens
        }
    }

    private fun tokenize(text: String): List<String> {
        return text.split("\\s+".toRegex()).filter { it.isNotBlank() }
    }
}
