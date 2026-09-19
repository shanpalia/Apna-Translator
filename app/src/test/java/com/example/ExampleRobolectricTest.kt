package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.history.AppDatabase
import com.example.data.history.HistoryEntity
import com.example.model.SupportedLanguages
import com.example.translation.LanguagePackManager
import com.example.translation.OfflineTranslationEngine
import com.example.translation.TranslationResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `test app name matches Apna Translator`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Apna Translator", appName)
    }

    @Test
    fun `test supported languages defined correctly`() {
        val languages = SupportedLanguages.ALL
        assertTrue(languages.isNotEmpty())
        assertNotNull(SupportedLanguages.findByCode("hi"))
        assertNotNull(SupportedLanguages.findByCode("en"))
        assertNotNull(SupportedLanguages.findByCode("ur"))
        assertNotNull(SupportedLanguages.findByCode("ne"))
        assertNotNull(SupportedLanguages.findByCode("ar"))
        assertTrue(SupportedLanguages.URDU.isRtl)
        assertTrue(SupportedLanguages.ARABIC.isRtl)
    }

    @Test
    fun `test offline translation Hindi to English`() = runBlocking {
        val packManager = LanguagePackManager(context)
        packManager.initialize()
        val engine = OfflineTranslationEngine(packManager)

        val hindi = SupportedLanguages.HINDI
        val english = SupportedLanguages.ENGLISH

        // Test phrase translation: "मुझे स्टेशन जाना है" -> "I want to go to the station"
        val result1 = engine.translate("मुझे स्टेशन जाना है", hindi, english)
        assertTrue(result1 is TranslationResult.Success)
        val success1 = result1 as TranslationResult.Success
        assertEquals("I want to go to the station", success1.translatedText)

        // Test phrase: "धन्यवाद" -> "Thank you"
        val result2 = engine.translate("धन्यवाद", hindi, english)
        assertTrue(result2 is TranslationResult.Success)
        val success2 = result2 as TranslationResult.Success
        assertEquals("Thank you", success2.translatedText)
    }

    @Test
    fun `test offline translation English to Hindi`() = runBlocking {
        val packManager = LanguagePackManager(context)
        packManager.initialize()
        val engine = OfflineTranslationEngine(packManager)

        val english = SupportedLanguages.ENGLISH
        val hindi = SupportedLanguages.HINDI

        val result = engine.translate("where is the station?", english, hindi)
        assertTrue(result is TranslationResult.Success)
        val success = result as TranslationResult.Success
        assertEquals("स्टेशन कहाँ है?", success.translatedText)
    }

    @Test
    fun `test offline translation Hindi to Urdu and Urdu to Hindi`() = runBlocking {
        val packManager = LanguagePackManager(context)
        packManager.initialize()
        val engine = OfflineTranslationEngine(packManager)

        val resultHiToUr = engine.translate("धन्यवाद", SupportedLanguages.HINDI, SupportedLanguages.URDU)
        assertTrue(resultHiToUr is TranslationResult.Success)
        assertEquals("شکریہ", (resultHiToUr as TranslationResult.Success).translatedText)

        val resultUrToHi = engine.translate("شکریہ", SupportedLanguages.URDU, SupportedLanguages.HINDI)
        assertTrue(resultUrToHi is TranslationResult.Success)
        assertEquals("धन्यवाद", (resultUrToHi as TranslationResult.Success).translatedText)
    }

    @Test
    fun `test missing model returns MissingModel result`() = runBlocking {
        val packManager = LanguagePackManager(context)
        val engine = OfflineTranslationEngine(packManager)

        // Tamil to Malayalam has no installed pack initially
        val result = engine.translate("வணக்கம்", SupportedLanguages.TAMIL, SupportedLanguages.MALAYALAM)
        assertTrue(result is TranslationResult.MissingModel)
    }

    @Test
    fun `test Room database history persistence`() = runBlocking {
        val dao = db.historyDao()
        val item = HistoryEntity(
            sourceLangCode = "hi",
            sourceLangName = "Hindi",
            sourceText = "नमस्ते",
            targetLangCode = "en",
            targetLangName = "English",
            translatedText = "Hello",
            timestamp = System.currentTimeMillis()
        )
        val id = dao.insert(item)
        assertTrue(id > 0)

        val all = dao.getAllHistory().first()
        assertEquals(1, all.size)
        assertEquals("नमस्ते", all[0].sourceText)
        assertEquals("Hello", all[0].translatedText)

        dao.deleteById(id)
        val afterDelete = dao.getAllHistory().first()
        assertTrue(afterDelete.isEmpty())
    }
}

