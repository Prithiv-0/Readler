package com.readler.feature.reader

import com.readler.core.model.BookFormat
import com.readler.core.model.BookMetadata
import com.readler.core.model.BookSearchResult
import com.readler.core.model.OpenedBook
import com.readler.core.model.ReadingProgress
import com.readler.core.reader.ReaderDocument
import com.readler.core.reader.ReaderEngine
import com.readler.core.reader.ReaderEngineRegistry
import com.readler.core.reader.ReaderSource
import com.readler.core.storage.BookFileStorage
import com.readler.domain.repository.AiCapability
import com.readler.domain.repository.AiQueuedRequest
import com.readler.domain.repository.AiRepository
import com.readler.domain.repository.BookRepository
import com.readler.domain.usecase.OpenBookUseCase
import com.readler.domain.usecase.SaveReadingProgressUseCase
import com.readler.domain.usecase.SearchInBookUseCase
import com.readler.feature.reader.prefs.ReaderPreferences
import com.readler.feature.reader.prefs.ReaderPreferencesStore
import com.readler.feature.reader.prefs.ReaderThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class ReaderViewModelIntegrationTest {

    @Test
    fun loadBookAndSearch_updatesDocumentAndLocator() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)

        val repository = FakeBookRepository()
        val openBook = OpenBookUseCase(repository)
        val saveProgress = SaveReadingProgressUseCase(repository)
        val search = SearchInBookUseCase(repository)

        val viewModel = ReaderViewModel(
            openBook = openBook,
            saveReadingProgress = saveProgress,
            searchInBook = search,
            aiRepository = FakeAiRepository(),
            fileStorage = FakeStorage(),
            readerEngineRegistry = ReaderEngineRegistry(listOf(FakeEpubEngine())),
            readerPreferencesStore = FakeReaderPreferencesStore()
        )

        viewModel.loadBook("book-1")
        advanceUntilIdle()

        assertEquals("book-1", viewModel.uiState.value.currentBook?.id)
        assertTrue(viewModel.uiState.value.currentDocument is ReaderDocument.Epub)

        viewModel.searchInCurrentBook("kotlin")
        advanceUntilIdle()

        assertEquals("epub-scroll:0.42", viewModel.uiState.value.currentLocator)
        assertEquals(1, viewModel.uiState.value.searchResults.size)

        Dispatchers.resetMain()
    }

    @Test
    fun updateTheme_persistsAndReflectsInState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)

        val prefsStore = FakeReaderPreferencesStore()
        val viewModel = ReaderViewModel(
            openBook = OpenBookUseCase(FakeBookRepository()),
            saveReadingProgress = SaveReadingProgressUseCase(FakeBookRepository()),
            searchInBook = SearchInBookUseCase(FakeBookRepository()),
            aiRepository = FakeAiRepository(),
            fileStorage = FakeStorage(),
            readerEngineRegistry = ReaderEngineRegistry(listOf(FakeEpubEngine())),
            readerPreferencesStore = prefsStore
        )

        viewModel.onThemeModeChanged(ReaderThemeMode.DARK)
        advanceUntilIdle()

        assertEquals(ReaderThemeMode.DARK, viewModel.uiState.value.preferences.themeMode)
        assertEquals(ReaderThemeMode.DARK, prefsStore.preferences.value.themeMode)

        Dispatchers.resetMain()
    }

    private class FakeStorage : BookFileStorage {
        override suspend fun importBook(sourceUri: String): com.readler.core.storage.ImportedBookFile {
            error("Not used")
        }

        override suspend fun open(filePath: String): InputStream = ByteArrayInputStream(byteArrayOf(1, 2, 3))
    }

    private class FakeEpubEngine : ReaderEngine {
        override val format: BookFormat = BookFormat.EPUB

        override suspend fun open(source: ReaderSource): ReaderDocument {
            source.inputStreamProvider().use { it.readBytes() }
            return ReaderDocument.Epub(
                filePath = source.filePath,
                initialLocator = source.initialLocator,
                htmlContent = "<p>hello</p>",
                chapterCount = 1
            )
        }
    }

    private class FakeReaderPreferencesStore : ReaderPreferencesStore {
        private val state = MutableStateFlow(ReaderPreferences())

        override val preferences: StateFlow<ReaderPreferences> = state.asStateFlow()

        override suspend fun updateFontScale(fontScale: Float) {
            state.value = state.value.copy(fontScale = fontScale)
        }

        override suspend fun updateThemeMode(themeMode: com.readler.feature.reader.prefs.ReaderThemeMode) {
            state.value = state.value.copy(themeMode = themeMode)
        }

        override suspend fun updateScrollMode(scrollMode: com.readler.feature.reader.prefs.ReaderScrollMode) {
            state.value = state.value.copy(scrollMode = scrollMode)
        }
    }

    private class FakeBookRepository : BookRepository {
        private val booksFlow = MutableStateFlow(
            listOf(
                BookMetadata(
                    id = "book-1",
                    title = "Test Book",
                    author = "Author",
                    format = BookFormat.EPUB,
                    filePath = "/tmp/book.epub",
                    coverImagePath = null,
                    lastOpenedAtEpochMs = null
                )
            )
        )

        override fun observeLibrary(): Flow<List<BookMetadata>> = booksFlow

        override suspend fun importBook(sourceUri: String): BookMetadata = booksFlow.value.first()

        override suspend fun openBook(bookId: String): OpenedBook {
            val metadata = booksFlow.value.first { it.id == bookId }
            return OpenedBook(metadata = metadata, startLocator = "epub-scroll:0.2")
        }

        override suspend fun saveReadingProgress(progress: ReadingProgress) = Unit

        override suspend fun getReadingProgress(bookId: String): ReadingProgress? = null

        override suspend fun searchInBook(bookId: String, query: String): List<BookSearchResult> {
            return listOf(
                BookSearchResult(
                    locator = "epub-scroll:0.42",
                    snippet = "kotlin match",
                    percent = 0.42f
                )
            )
        }
    }

    private class FakeAiRepository : AiRepository {
        private var enabled = false
        private var apiKey = ""

        override suspend fun isEnabled(): Boolean = enabled

        override suspend fun setEnabled(enabled: Boolean) {
            this.enabled = enabled
        }

        override suspend fun setApiKey(apiKey: String) {
            this.apiKey = apiKey
        }

        override suspend fun getApiKey(): String = apiKey

        override suspend fun getCapability(): AiCapability {
            return AiCapability(enabled = enabled, hasApiKey = apiKey.isNotBlank(), hasNetwork = true)
        }

        override suspend fun askQuestion(bookId: String, bookTitle: String, author: String?, question: String): String {
            return "answer"
        }

        override suspend fun explainSelection(bookId: String, bookTitle: String, selectedText: String): String {
            return "explain"
        }

        override suspend fun translateSelection(
            bookId: String,
            bookTitle: String,
            selectedText: String,
            targetLanguage: String
        ): String {
            return "translate"
        }

        override suspend fun suggestSimilarBooks(bookId: String, bookTitle: String, author: String?): String {
            return "similar"
        }

        override suspend fun summarizeSection(bookId: String, bookTitle: String, sectionText: String): String {
            return "summary"
        }

        override suspend fun enqueueRequest(request: AiQueuedRequest) = Unit

        override suspend fun flushQueuedRequests(): Int = 0
    }
}
