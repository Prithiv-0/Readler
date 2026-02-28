package com.readler.domain.usecase

import com.readler.core.model.BookMetadata
import com.readler.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow

class GetLibraryBooksUseCase(
    private val bookRepository: BookRepository
) {
    operator fun invoke(): Flow<List<BookMetadata>> = bookRepository.observeLibrary()
}
