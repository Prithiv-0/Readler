package com.readler.domain.usecase

import com.readler.core.model.BookSearchResult
import com.readler.domain.repository.BookRepository

class SearchInBookUseCase(
    private val bookRepository: BookRepository
) {
    suspend operator fun invoke(bookId: String, query: String): List<BookSearchResult> {
        return bookRepository.searchInBook(bookId, query)
    }
}
