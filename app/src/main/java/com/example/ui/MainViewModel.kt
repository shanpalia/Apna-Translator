package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.history.AppDatabase
import com.example.data.history.HistoryEntity
import com.example.data.history.HistoryRepository
import com.example.data.network.NetworkMonitor
import com.example.model.Language
import com.example.model.SupportedLanguages
import com.example.speech.WhisperSpeechRecognizerEngine
import com.example.speech.SpeechRecognizerEngine
import com.example.translation.LanguagePackManager
import com.example.translation.ModelPackInfo
import com.example.translation.OfflineTranslationEngine
import com.example.translation.TranslationEngine
import com.example.translation.TranslationResult
import com.example.tts.AndroidOfflineTtsEngine
import com.example.tts.OfflineTtsEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ConversationTurn(
    val speakerId: String, // "A" or "B"
    val sourceLang: Language,
    val targetLang: Language,
    val sourceText: String,
    val translatedText: String,
    val timestamp: Long = System.currentTimeMillis()
)

class MainViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val historyRepository: HistoryRepository
    val languagePackManager: LanguagePackManager
    val translationEngine: TranslationEngine
    val speechEngine: SpeechRecognizerEngine
    val ttsEngine: OfflineTtsEngine
    val networkMonitor: NetworkMonitor

    init {
        val db = AppDatabase.getDatabase(application)
        historyRepository = HistoryRepository(db.historyDao())
        languagePackManager = LanguagePackManager(application)
        translationEngine = OfflineTranslationEngine(languagePackManager)
        speechEngine = WhisperSpeechRecognizerEngine(application)
        ttsEngine = AndroidOfflineTtsEngine(application)
        networkMonitor = NetworkMonitor(application)

        viewModelScope.launch {
            languagePackManager.initialize()
        }

        // Collect final recognized speech results
        viewModelScope.launch {
            speechEngine.finalResult.collect { result ->
                if (result.isNotBlank()) {
                    handleSpeechFinalResult(result)
                }
            }
        }
    }

    // Active Tab Navigation (0: Translate, 1: Conversation, 2: History, 3: Languages, 4: Settings)
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    // Languages
    private val _sourceLanguage = MutableStateFlow(SupportedLanguages.HINDI)
    val sourceLanguage: StateFlow<Language> = _sourceLanguage.asStateFlow()

    private val _targetLanguage = MutableStateFlow(SupportedLanguages.ENGLISH)
    val targetLanguage: StateFlow<Language> = _targetLanguage.asStateFlow()

    // Text & Translation
    private val _sourceText = MutableStateFlow("")
    val sourceText: StateFlow<String> = _sourceText.asStateFlow()

    private val _translatedText = MutableStateFlow("")
    val translatedText: StateFlow<String> = _translatedText.asStateFlow()

    private val _isTranslating = MutableStateFlow(false)
    val isTranslating: StateFlow<Boolean> = _isTranslating.asStateFlow()

    private val _translationError = MutableStateFlow<String?>(null)
    val translationError: StateFlow<String?> = _translationError.asStateFlow()

    private val _isModelMissing = MutableStateFlow(false)
    val isModelMissing: StateFlow<Boolean> = _isModelMissing.asStateFlow()

    private val _missingModelMessage = MutableStateFlow<String?>(null)
    val missingModelMessage: StateFlow<String?> = _missingModelMessage.asStateFlow()

    private val _untranslatedTokens = MutableStateFlow<List<String>>(emptyList())
    val untranslatedTokens: StateFlow<List<String>> = _untranslatedTokens.asStateFlow()

    // Voice / TTS settings
    private val _ttsSpeed = MutableStateFlow(1.0f)
    val ttsSpeed: StateFlow<Float> = _ttsSpeed.asStateFlow()

    private val _ttsPitch = MutableStateFlow(1.0f)
    val ttsPitch: StateFlow<Float> = _ttsPitch.asStateFlow()

    private val _ttsNotice = MutableStateFlow<String?>(null)
    val ttsNotice: StateFlow<String?> = _ttsNotice.asStateFlow()

    // Conversation Mode
    private val _conversationHistory = MutableStateFlow<List<ConversationTurn>>(emptyList())
    val conversationHistory: StateFlow<List<ConversationTurn>> = _conversationHistory.asStateFlow()

    private var activeConversationSpeaker: String? = null // "A" or "B"

    // History Flow from Room
    val historyItems: StateFlow<List<HistoryEntity>> = historyRepository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val installedPacks: StateFlow<List<ModelPackInfo>> = languagePackManager.installedPacks
    val totalStorageBytes: StateFlow<Long> = languagePackManager.totalStorageBytes
    val isOffline: StateFlow<Boolean> = networkMonitor.isOffline
    val simulateOffline: StateFlow<Boolean> = networkMonitor.simulateOffline

    fun setSourceLanguage(language: Language) {
        _sourceLanguage.value = language
        if (sourceText.value.isNotBlank()) {
            translateText(sourceText.value)
        }
    }

    fun setTargetLanguage(language: Language) {
        _targetLanguage.value = language
        if (sourceText.value.isNotBlank()) {
            translateText(sourceText.value)
        }
    }

    fun swapLanguages() {
        val oldSource = _sourceLanguage.value
        val oldTarget = _targetLanguage.value
        val oldSourceText = _sourceText.value
        val oldTranslatedText = _translatedText.value

        _sourceLanguage.value = oldTarget
        _targetLanguage.value = oldSource

        _sourceText.value = oldTranslatedText
        _translatedText.value = oldSourceText

        if (_sourceText.value.isNotBlank()) {
            translateText(_sourceText.value)
        }
    }

    fun onSourceTextChange(text: String) {
        _sourceText.value = text
        if (text.isBlank()) {
            _translatedText.value = ""
            _translationError.value = null
            _isModelMissing.value = false
            _missingModelMessage.value = null
            _untranslatedTokens.value = emptyList()
        }
    }

    fun clearAll() {
        _sourceText.value = ""
        _translatedText.value = ""
        _translationError.value = null
        _isModelMissing.value = false
        _missingModelMessage.value = null
        _untranslatedTokens.value = emptyList()
        speechEngine.cancel()
        ttsEngine.stop()
    }

    fun translateText(input: String) {
        if (input.isBlank()) return
        viewModelScope.launch {
            _isTranslating.value = true
            _translationError.value = null
            _isModelMissing.value = false
            _missingModelMessage.value = null

            val result = translationEngine.translate(
                text = input,
                source = _sourceLanguage.value,
                target = _targetLanguage.value
            )

            when (result) {
                is TranslationResult.Success -> {
                    _translatedText.value = result.translatedText
                    _untranslatedTokens.value = result.untranslatedTokens
                    if (result.translatedText.isNotBlank()) {
                        historyRepository.addHistory(
                            sourceLangCode = _sourceLanguage.value.code,
                            sourceLangName = _sourceLanguage.value.name,
                            sourceText = input,
                            targetLangCode = _targetLanguage.value.code,
                            targetLangName = _targetLanguage.value.name,
                            translatedText = result.translatedText
                        )
                    }
                }
                is TranslationResult.MissingModel -> {
                    _isModelMissing.value = true
                    _missingModelMessage.value = result.message
                    _translatedText.value = ""
                }
                is TranslationResult.Error -> {
                    _translationError.value = result.message
                    _translatedText.value = ""
                }
            }
            _isTranslating.value = false
        }
    }

    fun startListeningForMain() {
        activeConversationSpeaker = null
        _ttsNotice.value = null
        speechEngine.startListening(
            bcp47Tag = _sourceLanguage.value.bcp47Tag,
            preferOfflineOnly = true
        )
    }

    fun startListeningForConversation(speaker: String) {
        activeConversationSpeaker = speaker
        _ttsNotice.value = null
        val lang = if (speaker == "A") _sourceLanguage.value else _targetLanguage.value
        speechEngine.startListening(
            bcp47Tag = lang.bcp47Tag,
            preferOfflineOnly = true
        )
    }

    fun stopListening() {
        speechEngine.stopListening()
    }

    private fun handleSpeechFinalResult(recognizedText: String) {
        val speaker = activeConversationSpeaker
        if (speaker == null) {
            // Main translator mode
            _sourceText.value = recognizedText
            translateText(recognizedText)
        } else {
            // Conversation mode
            val source = if (speaker == "A") _sourceLanguage.value else _targetLanguage.value
            val target = if (speaker == "A") _targetLanguage.value else _sourceLanguage.value

            viewModelScope.launch {
                val result = translationEngine.translate(recognizedText, source, target)
                val translated = when (result) {
                    is TranslationResult.Success -> result.translatedText
                    is TranslationResult.MissingModel -> "[Offline language pack required]"
                    is TranslationResult.Error -> "[Translation failed]"
                }

                val turn = ConversationTurn(
                    speakerId = speaker,
                    sourceLang = source,
                    targetLang = target,
                    sourceText = recognizedText,
                    translatedText = translated
                )
                _conversationHistory.value = _conversationHistory.value + turn

                if (result is TranslationResult.Success && translated.isNotBlank()) {
                    historyRepository.addHistory(
                        sourceLangCode = source.code,
                        sourceLangName = source.name,
                        sourceText = recognizedText,
                        targetLangCode = target.code,
                        targetLangName = target.name,
                        translatedText = translated
                    )
                    // Auto-speak in conversation
                    speakText(translated, target)
                }
            }
        }
    }

    fun speakText(text: String, language: Language) {
        _ttsNotice.value = null
        if (text.isBlank()) return

        val isInstalled = ttsEngine.isOfflineVoiceInstalled(language.bcp47Tag)
        if (!isInstalled) {
            _ttsNotice.value = "Offline voice is not installed for ${language.name}."
            return
        }

        ttsEngine.speak(
            text = text,
            bcp47Tag = language.bcp47Tag,
            speed = _ttsSpeed.value,
            pitch = _ttsPitch.value,
            onError = { err ->
                _ttsNotice.value = err
            }
        )
    }

    fun stopSpeaking() {
        ttsEngine.stop()
    }

    fun setTtsSpeed(speed: Float) {
        _ttsSpeed.value = speed
    }

    fun setTtsPitch(pitch: Float) {
        _ttsPitch.value = pitch
    }

    fun setSimulateOffline(enabled: Boolean) {
        networkMonitor.setSimulateOffline(enabled)
    }

    fun installLanguagePack(pairId: String) {
        viewModelScope.launch {
            val success = languagePackManager.installLanguagePack(pairId)
            if (success && _isModelMissing.value && _sourceText.value.isNotBlank()) {
                translateText(_sourceText.value)
            }
        }
    }

    fun removeLanguagePack(pairId: String) {
        viewModelScope.launch {
            languagePackManager.removeLanguagePack(pairId)
            if (_sourceText.value.isNotBlank()) {
                translateText(_sourceText.value)
            }
        }
    }

    fun clearConversation() {
        _conversationHistory.value = emptyList()
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            historyRepository.deleteHistory(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyRepository.clearAllHistory()
        }
    }

    fun loadHistoryIntoTranslator(item: HistoryEntity) {
        _sourceLanguage.value = SupportedLanguages.findByCode(item.sourceLangCode)
        _targetLanguage.value = SupportedLanguages.findByCode(item.targetLangCode)
        _sourceText.value = item.sourceText
        _translatedText.value = item.translatedText
        _selectedTab.value = 0 // Navigate to translate screen
    }

    override fun onCleared() {
        super.onCleared()
        speechEngine.release()
        ttsEngine.shutdown()
    }
}
