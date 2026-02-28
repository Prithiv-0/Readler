package com.readler.domain.repository

import com.readler.core.model.BookMetadata
import com.readler.core.model.OpenedBook
import com.readler.core.model.BookSearchResult
import com.readler.core.model.ReadingProgress
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun observeLibrary(): Flow<List<BookMetadata>>
    suspend fun importBook(sourceUri: String): BookMetadata
    suspend fun openBook(bookId: String): OpenedBook
    suspend fun saveReadingProgress(progress: ReadingProgress)
    suspend fun getReadingProgress(bookId: String): ReadingProgress?
    suspend fun searchInBook(bookId: String, query: String): List<BookSearchResult>
}
