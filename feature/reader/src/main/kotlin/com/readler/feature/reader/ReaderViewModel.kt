package com.readler.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readler.core.model.ReadingProgress
import com.readler.core.reader.ReaderDocument
import com.readler.core.reader.ReaderEngineRegistry
import com.readler.core.reader.ReaderSource
import com.readler.core.storage.BookFileStorage
import com.readler.domain.repository.AiQueuedRequest
import com.readler.domain.repository.AiRepository
import com.readler.domain.repository.AiRequestType
import com.readler.domain.usecase.OpenBookUseCase
import com.readler.domain.usecase.SearchInBookUseCase
import com.readler.domain.usecase.SaveReadingProgressUseCase
import com.readler.feature.reader.prefs.ReaderPreferencesStore
import com.readler.feature.reader.prefs.ReaderScrollMode
import com.readler.feature.reader.prefs.ReaderThemeMode
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReaderViewModel(
    private val openBook: OpenBookUseCase,
    private val saveReadingProgress: SaveReadingProgressUseCase,
    private val searchInBook: SearchInBookUseCase,
    private val aiRepository: AiRepository,
    private val fileStorage: BookFileStorage,
    private val readerEngineRegistry: ReaderEngineRegistry,
    private val readerPreferencesStore: ReaderPreferencesStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        observePreferences()
        refreshAiCapability()
    }

    fun loadBook(bookId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            runCatching {
                val openedBook = openBook(bookId)
                val engine = readerEngineRegistry.get(openedBook.metadata.format)
                val document = engine.open(
                    ReaderSource(
                        filePath = openedBook.metadata.filePath,
                        inputStreamProvider = { fileStorage.open(openedBook.metadata.filePath) },
                        initialLocator = openedBook.startLocator
                    )
                )
                openedBook to document
            }.onSuccess { openedBook ->
                _uiState.value = _uiState.value.copy(
                    currentBook = openedBook.first.metadata,
                    currentDocument = openedBook.second,
                    currentLocator = openedBook.first.startLocator,
                    isLoading = false,
                    errorMessage = null
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = throwable.message)
            }
        }
    }

    fun onReadingPositionChanged(locator: String, percent: Float) {
        val currentBook = _uiState.value.currentBook ?: return
        val progress = ReadingProgress(
            bookId = currentBook.id,
            locator = locator,
            percent = percent,
            updatedAtEpochMs = System.currentTimeMillis()
        )

        viewModelScope.launch {
            saveReadingProgress(progress)
            _uiState.value = _uiState.value.copy(currentLocator = locator)
        }
    }

    fun onFontScaleChanged(fontScale: Float) {
        viewModelScope.launch {
            readerPreferencesStore.updateFontScale(fontScale)
        }
    }

    fun onThemeModeChanged(themeMode: ReaderThemeMode) {
        viewModelScope.launch {
            readerPreferencesStore.updateThemeMode(themeMode)
        }
    }

    fun onScrollModeChanged(scrollMode: ReaderScrollMode) {
        viewModelScope.launch {
            readerPreferencesStore.updateScrollMode(scrollMode)
        }
    }

    fun onFontFamilyChanged(fontFamily: com.readler.feature.reader.prefs.ReaderFontFamily) {
        viewModelScope.launch {
            readerPreferencesStore.updateFontFamily(fontFamily)
        }
    }

    fun onLineSpacingChanged(lineSpacing: com.readler.feature.reader.prefs.ReaderLineSpacing) {
        viewModelScope.launch {
            readerPreferencesStore.updateLineSpacing(lineSpacing)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun onAiEnabledChanged(enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                aiRepository.setEnabled(enabled)
            }.onSuccess {
                refreshAiCapability()
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(errorMessage = throwable.message)
            }
        }
    }

    fun saveAiApiKey(apiKey: String) {
        viewModelScope.launch {
            val normalized = apiKey.trim()
            runCatching {
                aiRepository.setApiKey(normalized)
            }.onSuccess {
                refreshAiCapability()
                _uiState.value = _uiState.value.copy(
                    aiResponse = if (normalized.isBlank()) {
                        "Gemini API key cleared."
                    } else {
                        "Gemini API key saved."
                    }
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(errorMessage = throwable.message)
            }
        }
    }

    fun refreshAiCapability() {
        viewModelScope.launch {
            runCatching {
                aiRepository.getCapability()
            }.onSuccess { capability ->
                var aiResponse = _uiState.value.aiResponse
                if (capability.canRun) {
                    val flushed = runCatching { aiRepository.flushQueuedRequests() }.getOrDefault(0)
                    if (flushed > 0) {
                        aiResponse = "Processed $flushed queued AI request(s)."
                    }
                }

                _uiState.value = _uiState.value.copy(
                    aiEnabled = capability.enabled,
                    aiCapability = capability,
                    aiResponse = aiResponse
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(errorMessage = throwable.message)
            }
        }
    }

    fun askBookQuestion(question: String) {
        performAiAction(
            type = AiRequestType.QUESTION,
            prompt = question,
            selectedText = null,
            targetLanguage = null,
            sectionContext = null
        ) { book ->
            aiRepository.askQuestion(book.id, book.title, book.author, question)
        }
    }

    fun askSimilarBooks() {
        val prompt = "Suggest books similar to current book"
        performAiAction(
            type = AiRequestType.SIMILAR_BOOKS,
            prompt = prompt,
            selectedText = null,
            targetLanguage = null,
            sectionContext = null
        ) { book ->
            aiRepository.suggestSimilarBooks(book.id, book.title, book.author)
        }
    }

    fun explainSelectedText(selectedText: String) {
        performAiAction(
            type = AiRequestType.EXPLAIN_SELECTION,
            prompt = "Explain this selected text",
            selectedText = selectedText,
            targetLanguage = null,
            sectionContext = null
        ) { book ->
            aiRepository.explainSelection(book.id, book.title, selectedText)
        }
    }

    fun translateSelectedText(selectedText: String, targetLanguage: String) {
        performAiAction(
            type = AiRequestType.TRANSLATE_SELECTION,
            prompt = "Translate selected text",
            selectedText = selectedText,
            targetLanguage = targetLanguage,
            sectionContext = null
        ) { book ->
            aiRepository.translateSelection(book.id, book.title, selectedText, targetLanguage)
        }
    }

    fun summarizeCurrentSection(sectionText: String) {
        performAiAction(
            type = AiRequestType.SECTION_SUMMARY,
            prompt = "Summarize current section",
            selectedText = null,
            targetLanguage = null,
            sectionContext = sectionText
        ) { book ->
            aiRepository.summarizeSection(book.id, book.title, sectionText)
        }
    }

    fun confirmQueuePending(shouldQueue: Boolean) {
        val pending = _uiState.value.pendingQueueRequest ?: return

        viewModelScope.launch {
            if (shouldQueue) {
                runCatching {
                    aiRepository.enqueueRequest(pending)
                }.onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        pendingQueueRequest = null,
                        errorMessage = throwable.message,
                        isAiLoading = false
                    )
                    return@launch
                }
            }

            _uiState.value = _uiState.value.copy(
                pendingQueueRequest = null,
                isAiLoading = false,
                errorMessage = null,
                aiResponse = if (shouldQueue) {
                    "Request queued. It will run when network is available."
                } else {
                    _uiState.value.aiResponse
                }
            )
        }
    }

    fun clearAiResponse() {
        _uiState.value = _uiState.value.copy(aiResponse = null)
    }

    fun searchInCurrentBook(query: String) {
        val currentBook = _uiState.value.currentBook ?: return
        val trimmed = query.trim()

        if (trimmed.isBlank()) {
            _uiState.value = _uiState.value.copy(searchQuery = "", searchResults = emptyList())
            return
        }

        viewModelScope.launch {
            runCatching {
                searchInBook(currentBook.id, trimmed)
            }.onSuccess { results ->
                _uiState.value = _uiState.value.copy(
                    searchQuery = trimmed,
                    searchResults = results,
                    errorMessage = if (results.isEmpty()) "No matches for '$trimmed'" else null
                )
                results.firstOrNull()?.let { jumpToLocator(it.locator) }
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(errorMessage = throwable.message)
            }
        }
    }

    private fun jumpToLocator(locator: String) {
        val currentDocument = _uiState.value.currentDocument ?: return
        val relocatedDocument = when (currentDocument) {
            is ReaderDocument.Epub -> currentDocument.copy(initialLocator = locator)
            is ReaderDocument.Pdf -> currentDocument.copy(initialLocator = locator)
        }

        _uiState.value = _uiState.value.copy(
            currentDocument = relocatedDocument,
            currentLocator = locator
        )
    }

    private fun observePreferences() {
        viewModelScope.launch {
            readerPreferencesStore.preferences.collectLatest { prefs ->
                _uiState.value = _uiState.value.copy(preferences = prefs)
            }
        }
    }

    private fun performAiAction(
        type: AiRequestType,
        prompt: String,
        selectedText: String?,
        targetLanguage: String?,
        sectionContext: String?,
        request: suspend (book: com.readler.core.model.BookMetadata) -> String
    ) {
        val currentBook = _uiState.value.currentBook
        if (currentBook == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Open a book before using AI")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAiLoading = true, errorMessage = null)

            val capability = runCatching { aiRepository.getCapability() }.getOrElse { throwable ->
                _uiState.value = _uiState.value.copy(isAiLoading = false, errorMessage = throwable.message)
                return@launch
            }

            _uiState.value = _uiState.value.copy(aiCapability = capability, aiEnabled = capability.enabled)

            if (!capability.enabled) {
                _uiState.value = _uiState.value.copy(isAiLoading = false, errorMessage = "AI is disabled")
                return@launch
            }
            if (!capability.hasApiKey) {
                _uiState.value = _uiState.value.copy(isAiLoading = false, errorMessage = "Gemini API key missing")
                return@launch
            }
            if (!capability.hasNetwork) {
                _uiState.value = _uiState.value.copy(
                    isAiLoading = false,
                    pendingQueueRequest = AiQueuedRequest(
                        bookId = currentBook.id,
                        bookTitle = currentBook.title,
                        author = currentBook.author,
                        type = type,
                        prompt = prompt,
                        selectedText = selectedText,
                        targetLanguage = targetLanguage,
                        sectionContext = sectionContext
                    ),
                    errorMessage = "No network. Queue this AI request?"
                )
                return@launch
            }

            runCatching {
                request(currentBook)
            }.onSuccess { response ->
                _uiState.value = _uiState.value.copy(
                    isAiLoading = false,
                    pendingQueueRequest = null,
                    aiResponse = response,
                    errorMessage = null
                )
            }.onFailure { throwable ->
                _uiState.value = _uiState.value.copy(
                    isAiLoading = false,
                    errorMessage = throwable.message
                )
            }
        }
    }
}
