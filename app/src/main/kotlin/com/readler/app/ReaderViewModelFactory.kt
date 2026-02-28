package com.readler.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.readler.feature.reader.ReaderViewModel

class ReaderViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReaderViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReaderViewModel(
                openBook = AppContainer.openBookUseCase,
                saveReadingProgress = AppContainer.saveReadingProgressUseCase,
                searchInBook = AppContainer.searchInBookUseCase,
                aiRepository = AppContainer.aiRepository,
                fileStorage = AppContainer.bookFileStorage,
                readerEngineRegistry = AppContainer.readerEngineRegistry,
                readerPreferencesStore = AppContainer.readerPreferencesStore
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
