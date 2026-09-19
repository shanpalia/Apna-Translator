package com.example.data.history

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class HistoryRepository(private val dao: HistoryDao) {

    val allHistory: Flow<List<HistoryEntity>> = dao.getAllHistory()

    suspend fun addHistory(
        sourceLangCode: String,
        sourceLangName: String,
        sourceText: String,
        targetLangCode: String,
        targetLangName: String,
        translatedText: String
    ): Long = withContext(Dispatchers.IO) {
        if (sourceText.isBlank() || translatedText.isBlank()) return@withContext -1L
        val item = HistoryEntity(
            sourceLangCode = sourceLangCode,
            sourceLangName = sourceLangName,
            sourceText = sourceText.trim(),
            targetLangCode = targetLangCode,
            targetLangName = targetLangName,
            translatedText = translatedText.trim(),
            timestamp = System.currentTimeMillis()
        )
        dao.insert(item)
    }

    suspend fun deleteHistory(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }

    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        dao.deleteAll()
    }
}
