package com.readler.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.readler.feature.library.LibraryViewModel

class LibraryViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LibraryViewModel(
                getLibraryBooks = AppContainer.getLibraryBooksUseCase,
                importBook = AppContainer.importBookUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
