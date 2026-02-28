package com.readler.feature.library

import com.readler.core.model.BookMetadata

data class LibraryUiState(
    val books: List<BookMetadata> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)
