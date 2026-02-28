package com.readler.app

import android.content.Context
import com.readler.ai.GeminiAiRepository
import com.readler.app.telemetry.FileTelemetryLogger
import com.readler.app.telemetry.TelemetryLogger
import com.readler.core.data.BookRepositoryImpl
import com.readler.core.data.importer.BookImportMetadataExtractor
import com.readler.core.data.importer.BookMetadataExtractor
import com.readler.core.database.AppDatabase
import com.readler.core.reader.EpubReaderEngine
import com.readler.core.reader.PdfReaderEngine
import com.readler.core.reader.ReaderEngineRegistry
import com.readler.core.storage.AppPrivateBookFileStorage
import com.readler.domain.repository.BookRepository
import com.readler.domain.repository.AiRepository
import com.readler.domain.usecase.GetLibraryBooksUseCase
import com.readler.domain.usecase.ImportBookUseCase
import com.readler.domain.usecase.OpenBookUseCase
import com.readler.domain.usecase.SearchInBookUseCase
import com.readler.domain.usecase.SaveReadingProgressUseCase
import com.readler.feature.reader.prefs.SharedReaderPreferencesStore

object AppContainer {
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
        }
    }

    private val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }
    private val fileStorage by lazy { AppPrivateBookFileStorage(appContext) }
    private val metadataExtractor: BookMetadataExtractor by lazy { BookImportMetadataExtractor(appContext) }
    val telemetryLogger: TelemetryLogger by lazy { FileTelemetryLogger(appContext) }
    val aiRepository: AiRepository by lazy {
        GeminiAiRepository(
            context = appContext,
            defaultApiKey = BuildConfig.GEMINI_API_KEY
        )
    }

    val bookRepository: BookRepository by lazy {
        BookRepositoryImpl(
            bookDao = database.bookDao(),
            fileStorage = fileStorage,
            metadataExtractor = metadataExtractor
        )
    }

    val getLibraryBooksUseCase by lazy { GetLibraryBooksUseCase(bookRepository) }
    val importBookUseCase by lazy { ImportBookUseCase(bookRepository) }
    val openBookUseCase by lazy { OpenBookUseCase(bookRepository) }
    val saveReadingProgressUseCase by lazy { SaveReadingProgressUseCase(bookRepository) }
    val searchInBookUseCase by lazy { SearchInBookUseCase(bookRepository) }

    val readerEngineRegistry by lazy {
        ReaderEngineRegistry(
            readerEngines = listOf(
                EpubReaderEngine(),
                PdfReaderEngine()
            )
        )
    }

    val bookFileStorage by lazy { fileStorage }
    val readerPreferencesStore by lazy { SharedReaderPreferencesStore(appContext) }
}
