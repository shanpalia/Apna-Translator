package com.example.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "translation_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceLangCode: String,
    val sourceLangName: String,
    val sourceText: String,
    val targetLangCode: String,
    val targetLangName: String,
    val translatedText: String,
    val timestamp: Long = System.currentTimeMillis()
)
