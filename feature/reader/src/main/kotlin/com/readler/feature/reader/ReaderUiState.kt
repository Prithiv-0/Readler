package com.readler.feature.reader

import com.readler.core.model.BookMetadata
import com.readler.core.model.BookSearchResult
import com.readler.core.reader.ReaderDocument
import com.readler.domain.repository.AiCapability
import com.readler.domain.repository.AiQueuedRequest
import com.readler.feature.reader.prefs.ReaderPreferences

data class ReaderUiState(
    val currentBook: BookMetadata? = null,
    val currentDocument: ReaderDocument? = null,
    val currentLocator: String? = null,
    val searchQuery: String = "",
    val searchResults: List<BookSearchResult> = emptyList(),
    val aiEnabled: Boolean = false,
    val aiCapability: AiCapability = AiCapability(),
    val aiResponse: String? = null,
    val isAiLoading: Boolean = false,
    val pendingQueueRequest: AiQueuedRequest? = null,
    val preferences: ReaderPreferences = ReaderPreferences(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
