package com.example.translation

import android.util.Log
import com.example.model.Language
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Real on-device translation backed by Google ML Kit.
 *
 * ML Kit downloads language models on first use. Once a required model is
 * present, translation runs on-device without sending the user's text to a server.
 * The existing small phrase packs are only used as a limited fallback when they
 * can translate every token; they are never presented as a full translation model.
 */
class MlKitOfflineTranslationEngine(
    private val phraseFallback: OfflineTranslationEngine
) : TranslationEngine {

    companion object {
        private const val TAG = "MlKitOfflineTranslate"
    }

    override fun isModelInstalled(source: Language, target: Language): Boolean {
        if (source.code.equals(target.code, ignoreCase = true)) return true
        // This synchronous interface cannot query ML Kit's asynchronous model
        // registry. Phrase-pack availability is reported here; translate() checks
        // and downloads the real ML Kit model before translating.
        return phraseFallback.isModelInstalled(source, target)
    }

    override suspend fun translate(
        text: String,
        source: Language,
        target: Language
    ): TranslationResult = withContext(Dispatchers.IO) {
        val input = text.trim()
        if (input.isEmpty() || source.code.equals(target.code, ignoreCase = true)) {
            return@withContext TranslationResult.Success(
                sourceText = input,
                translatedText = input,
                sourceLang = source,
                targetLang = target
            )
        }

        val sourceCode = TranslateLanguage.fromLanguageTag(source.code)
        val targetCode = TranslateLanguage.fromLanguageTag(target.code)
        if (sourceCode == null || targetCode == null) {
            return@withContext TranslationResult.Error(
                "This language pair is not supported by the offline translation engine yet."
            )
        }

        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(sourceCode)
                .setTargetLanguage(targetCode)
                .build()
        )

        try {
            // First use needs a model download. After download, ML Kit translates
            // locally, including when the device is offline.
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).awaitResult()
            val translated = translator.translate(input).awaitResult()
            TranslationResult.Success(
                sourceText = input,
                translatedText = translated,
                sourceLang = source,
                targetLang = target,
                untranslatedTokens = emptyList(),
                isOffline = true
            )
        } catch (error: Exception) {
            Log.w(TAG, "ML Kit translation unavailable for ${source.code}->${target.code}", error)

            // Use the bundled phrase dictionary only if it covers the entire input.
            // Partial dictionary output is not a trustworthy translation.
            val fallback = try {
                phraseFallback.translate(input, source, target)
            } catch (_: Exception) {
                null
            }
            if (fallback is TranslationResult.Success && fallback.untranslatedTokens.isEmpty()) {
                fallback
            } else {
                TranslationResult.Error(
                    "The offline translation model for ${source.name} → ${target.name} is not ready. Connect to the internet once to download the language model, then you can translate offline. Details: ${error.localizedMessage ?: "download or translation failed"}"
                )
            }
        } finally {
            translator.close()
        }
    }

    private suspend fun <T> Task<T>.awaitResult(): T =
        suspendCancellableCoroutine { continuation ->
            addOnCompleteListener { task ->
                if (!continuation.isActive) return@addOnCompleteListener
                if (task.isSuccessful) {
                    continuation.resume(task.result)
                } else {
                    continuation.resumeWithException(
                        task.exception ?: IllegalStateException("ML Kit task failed")
                    )
                }
            }
        }
}
