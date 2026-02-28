package com.readler.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readler.domain.usecase.GetLibraryBooksUseCase
import com.readler.domain.usecase.ImportBookUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val getLibraryBooks: GetLibraryBooksUseCase,
    private val importBook: ImportBookUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        observeLibrary()
    }

    fun importBookFromUri(sourceUri: String) {
        viewModelScope.launch {
            runCatching { importBook(sourceUri) }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(errorMessage = throwable.message)
                }
        }
    }

    private fun observeLibrary() {
        viewModelScope.launch {
            getLibraryBooks()
                .onStart {
                    _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                }
                .catch { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = throwable.message
                    )
                }
                .collect { books ->
                    _uiState.value = LibraryUiState(
                        books = books,
                        isLoading = false,
                        errorMessage = null
                    )
                }
        }
    }
}
