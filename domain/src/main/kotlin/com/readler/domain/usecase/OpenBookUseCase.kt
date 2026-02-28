package com.readler.domain.usecase

import com.readler.core.model.OpenedBook
import com.readler.domain.repository.BookRepository

class OpenBookUseCase(
    private val bookRepository: BookRepository
) {
    suspend operator fun invoke(bookId: String): OpenedBook = bookRepository.openBook(bookId)
}
