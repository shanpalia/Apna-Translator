package com.example.translation

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class ModelPackInfo(
    val id: String,
    val sourceCode: String,
    val targetCode: String,
    val title: String,
    val version: String,
    val sizeBytes: Long,
    val isInstalled: Boolean,
    val description: String
)

class LanguagePackManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "LanguagePackManager"
        const val MODELS_DIR_NAME = "models/translation"
    }

    private val modelsDir: File by lazy {
        val dir = File(context.filesDir, MODELS_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    private val _installedPacks = MutableStateFlow<List<ModelPackInfo>>(emptyList())
    val installedPacks: StateFlow<List<ModelPackInfo>> = _installedPacks.asStateFlow()

    private val _totalStorageBytes = MutableStateFlow(0L)
    val totalStorageBytes: StateFlow<Long> = _totalStorageBytes.asStateFlow()

    // Registry of available packs that can be installed/stored
    private val packCatalog: List<ModelPackCatalogEntry> = listOf(
        ModelPackCatalogEntry("hi_en", "hi", "en", "Hindi ➔ English", "1.0.0", "Everyday phrases, travel, directions & vocabulary (SOV ➔ SVO)"),
        ModelPackCatalogEntry("en_hi", "en", "hi", "English ➔ Hindi", "1.0.0", "Everyday phrases, travel, directions & vocabulary (SVO ➔ SOV)"),
        ModelPackCatalogEntry("hi_ne", "ne", "hi", "Hindi ➔ Nepali", "1.0.0", "Everyday communication & travel vocabulary"),
        ModelPackCatalogEntry("ne_hi", "hi", "ne", "Nepali ➔ Hindi", "1.0.0", "Everyday communication & travel vocabulary"),
        ModelPackCatalogEntry("hi_ar", "hi", "ar", "Hindi ➔ Arabic", "1.0.0", "Everyday travel, greetings & RTL vocabulary"),
        ModelPackCatalogEntry("ar_hi", "ar", "hi", "Arabic ➔ Hindi", "1.0.0", "Arabic RTL greetings & conversation to Hindi"),
        ModelPackCatalogEntry("hi_ur", "hi", "ur", "Hindi ➔ Urdu", "1.0.0", "Sister-language lexicon, Nastaliq & phrase mapping"),
        ModelPackCatalogEntry("ur_hi", "ur", "hi", "Urdu ➔ Hindi", "1.0.0", "Urdu RTL phrases to Hindi Devanagari"),
        ModelPackCatalogEntry("en_ur", "en", "ur", "English ➔ Urdu", "1.0.0", "English to Urdu conversation and lexical pack"),
        ModelPackCatalogEntry("ur_en", "ur", "en", "Urdu ➔ English", "1.0.0", "Urdu RTL phrases and lexicon to English"),
        ModelPackCatalogEntry("en_ar", "en", "ar", "English ➔ Arabic", "1.0.0", "English to Arabic travel and conversation pack"),
        ModelPackCatalogEntry("ar_en", "ar", "en", "Arabic ➔ English", "1.0.0", "Arabic RTL to English conversation pack"),
        ModelPackCatalogEntry("en_bn", "en", "bn", "English ➔ Bengali", "1.0.0", "English to Bengali vocabulary and phrases"),
        ModelPackCatalogEntry("bn_en", "bn", "en", "Bengali ➔ English", "1.0.0", "Bengali to English vocabulary and phrases"),
        ModelPackCatalogEntry("en_mr", "en", "mr", "English ➔ Marathi", "1.0.0", "English to Marathi vocabulary and phrases"),
        ModelPackCatalogEntry("en_gu", "en", "gu", "English ➔ Gujarati", "1.0.0", "English to Gujarati vocabulary and phrases"),
        ModelPackCatalogEntry("en_pa", "en", "pa", "English ➔ Punjabi", "1.0.0", "English to Punjabi Gurmukhi phrases"),
        ModelPackCatalogEntry("en_ta", "en", "ta", "English ➔ Tamil", "1.0.0", "English to Tamil vocabulary and phrases"),
        ModelPackCatalogEntry("en_te", "en", "te", "English ➔ Telugu", "1.0.0", "English to Telugu vocabulary and phrases"),
        ModelPackCatalogEntry("en_kn", "en", "kn", "English ➔ Kannada", "1.0.0", "English to Kannada vocabulary and phrases"),
        ModelPackCatalogEntry("en_ml", "en", "ml", "English ➔ Malayalam", "1.0.0", "English to Malayalam vocabulary and phrases")
    )

    data class ModelPackCatalogEntry(
        val id: String,
        val source: String,
        val target: String,
        val title: String,
        val version: String,
        val description: String
    )

    suspend fun initialize() = withContext(Dispatchers.IO) {
        // Auto-install bundled core packs if directory is empty
        val existingFiles = modelsDir.listFiles()?.filter { it.extension == "json" } ?: emptyList()
        if (existingFiles.isEmpty()) {
            // Install the core language pairs by default so app is immediately functional offline!
            val corePairs = listOf("hi_en", "en_hi", "hi_ne", "ne_hi", "hi_ar", "ar_hi", "hi_ur", "ur_hi")
            for (pair in corePairs) {
                installLanguagePack(pair)
            }
        }
        refreshInstalledPacks()
    }

    suspend fun refreshInstalledPacks() = withContext(Dispatchers.IO) {
        val list = mutableListOf<ModelPackInfo>()
        var totalBytes = 0L

        for (entry in packCatalog) {
            val file = File(modelsDir, "${entry.id}.json")
            val isInstalled = file.exists() && file.length() > 0
            val size = if (isInstalled) file.length() else estimateAssetSize(entry.id)
            if (isInstalled) {
                totalBytes += size
            }
            list.add(
                ModelPackInfo(
                    id = entry.id,
                    sourceCode = entry.source,
                    targetCode = entry.target,
                    title = entry.title,
                    version = entry.version,
                    sizeBytes = size,
                    isInstalled = isInstalled,
                    description = entry.description
                )
            )
        }

        _installedPacks.value = list
        _totalStorageBytes.value = totalBytes
    }

    fun isLanguagePairAvailable(sourceCode: String, targetCode: String): Boolean {
        val pairId = "${sourceCode.lowercase()}_${targetCode.lowercase()}"
        val file = File(modelsDir, "$pairId.json")
        return file.exists() && file.length() > 0
    }

    fun getInstalledLanguages(): Set<String> {
        val set = mutableSetOf<String>()
        val packs = _installedPacks.value.filter { it.isInstalled }
        for (p in packs) {
            set.add(p.sourceCode)
            set.add(p.targetCode)
        }
        return set
    }

    fun getModelFile(pairId: String): File? {
        val file = File(modelsDir, "$pairId.json")
        return if (file.exists()) file else null
    }

    suspend fun installLanguagePack(pairId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val assetPath = "models/translation/$pairId.json"
            val targetFile = File(modelsDir, "$pairId.json")

            // Check if available in assets
            val assetExists = try {
                context.assets.open(assetPath).use { true }
            } catch (e: Exception) {
                false
            }

            if (assetExists) {
                context.assets.open(assetPath).use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                // Generate a synthesized baseline model package for this pair if not in assets
                val catalogEntry = packCatalog.firstOrNull { it.id == pairId }
                if (catalogEntry != null) {
                    val fallbackJson = JSONObject().apply {
                        put("id", catalogEntry.id)
                        put("source", catalogEntry.source)
                        put("target", catalogEntry.target)
                        put("version", catalogEntry.version)
                        put("name", catalogEntry.title)
                        put("phrases", JSONObject())
                        put("lexicon", JSONObject())
                    }
                    targetFile.writeText(fallbackJson.toString(2))
                }
            }

            refreshInstalledPacks()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install pack $pairId: ${e.message}", e)
            false
        }
    }

    suspend fun removeLanguagePack(pairId: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(modelsDir, "$pairId.json")
        val deleted = if (file.exists()) file.delete() else true
        refreshInstalledPacks()
        deleted
    }

    fun getModelSize(pairId: String): Long {
        val file = File(modelsDir, "$pairId.json")
        return if (file.exists()) file.length() else 0L
    }

    private fun estimateAssetSize(pairId: String): Long {
        return try {
            context.assets.open("models/translation/$pairId.json").use { it.available().toLong() }
        } catch (e: Exception) {
            25000L // default estimate ~25 KB
        }
    }
}
