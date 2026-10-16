package com.example.translation

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
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
        ModelPackCatalogEntry("hi_en", "hi", "en", "Hindi ➔ English", "1.0.0", "Small local phrase dictionary only; not a full AI translation model."),
        ModelPackCatalogEntry("en_hi", "en", "hi", "English ➔ Hindi", "1.0.0", "Small local phrase dictionary only; not a full AI translation model."),
        ModelPackCatalogEntry("hi_ne", "hi", "ne", "Hindi ➔ Nepali", "1.0.0", "Small local phrase dictionary only; not a full AI translation model."),
        ModelPackCatalogEntry("ne_hi", "ne", "hi", "Nepali ➔ Hindi", "1.0.0", "Small local phrase dictionary only; not a full AI translation model."),
        ModelPackCatalogEntry("hi_ar", "hi", "ar", "Hindi ➔ Arabic", "1.0.0", "Small local phrase dictionary only; not a full AI translation model."),
        ModelPackCatalogEntry("ar_hi", "ar", "hi", "Arabic ➔ Hindi", "1.0.0", "Small local phrase dictionary only; not a full AI translation model."),
        ModelPackCatalogEntry("hi_ur", "hi", "ur", "Hindi ➔ Urdu", "1.0.0", "Small local phrase dictionary only; not a full AI translation model."),
        ModelPackCatalogEntry("ur_hi", "ur", "hi", "Urdu ➔ Hindi", "1.0.0", "Small local phrase dictionary only; not a full AI translation model."),
        ModelPackCatalogEntry("en_ur", "en", "ur", "English ➔ Urdu", "1.0.0", "Small local phrase dictionary only; not a full AI translation model."),
        ModelPackCatalogEntry("ur_en", "ur", "en", "Urdu ➔ English", "1.0.0", "Small local phrase dictionary only; not a full AI translation model."),
        ModelPackCatalogEntry("en_ar", "en", "ar", "English ➔ Arabic", "1.0.0", "Small local phrase dictionary only; not a full AI translation model."),
        ModelPackCatalogEntry("ar_en", "ar", "en", "Arabic ➔ English", "1.0.0", "Small local phrase dictionary only; not a full AI translation model."),
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
        // Repair/install each core pair independently. One existing JSON file must not
        // prevent the other core pairs from being initialized.
        val corePairs = listOf("hi_en", "en_hi", "hi_ne", "ne_hi", "hi_ar", "ar_hi", "hi_ur", "ur_hi")
        for (pair in corePairs) {
            val file = File(modelsDir, "$pair.json")
            val entry = packCatalog.firstOrNull { it.id == pair }
            if (entry != null && !isUsablePack(file, entry.source, entry.target)) {
                file.delete()
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
            val isInstalled = isUsablePack(file, entry.source, entry.target)
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
        val source = sourceCode.lowercase()
        val target = targetCode.lowercase()
        if (source == target) return true
        if (hasDirectPair(source, target)) return true

        // For pairs without a direct dictionary, the app can use Hindi as a pivot
        // only when both real phrase packs are present and valid.
        if (source != "hi" && target != "hi") {
            return hasDirectPair(source, "hi") && hasDirectPair("hi", target)
        }
        return false
    }

    private fun hasDirectPair(sourceCode: String, targetCode: String): Boolean {
        val pairId = "${sourceCode}_${targetCode}"
        val entry = packCatalog.firstOrNull { it.id == pairId } ?: return false
        return isUsablePack(File(modelsDir, "$pairId.json"), entry.source, entry.target)
    }

    private fun isUsablePack(file: File, source: String, target: String): Boolean {
        if (!file.exists() || file.length() <= 0L) return false
        return try {
            val json = org.json.JSONObject(file.readText(Charsets.UTF_8))
            json.optString("source") == source && json.optString("target") == target &&
                ((json.optJSONObject("phrases")?.length() ?: 0) + (json.optJSONObject("lexicon")?.length() ?: 0) > 0)
        } catch (_: Exception) { false }
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
        val entry = packCatalog.firstOrNull { it.id == pairId } ?: return null
        val file = File(modelsDir, "$pairId.json")
        return file.takeIf { isUsablePack(it, entry.source, entry.target) }
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

            if (!assetExists) {
                // Do not create an empty pack and falsely report that translation is available.
                targetFile.delete()
                refreshInstalledPacks()
                return@withContext false
            }

            val tempFile = File(modelsDir, "$pairId.json.part")
            context.assets.open(assetPath).use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            val entry = packCatalog.firstOrNull { it.id == pairId }
                ?: run { tempFile.delete(); return@withContext false }
            if (!isUsablePack(tempFile, entry.source, entry.target)) {
                tempFile.delete()
                targetFile.delete()
                refreshInstalledPacks()
                return@withContext false
            }
            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            refreshInstalledPacks()
            isUsablePack(targetFile, entry.source, entry.target)
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
        } catch (_: Exception) {
            0L // Never display a fabricated size for a pack that is not bundled.
        }
    }
}
