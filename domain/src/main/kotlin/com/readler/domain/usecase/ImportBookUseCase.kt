package com.readler.domain.usecase

import com.readler.core.model.BookMetadata
import com.readler.domain.repository.BookRepository

class ImportBookUseCase(
    private val bookRepository: BookRepository
) {
    suspend operator fun invoke(sourceUri: String): BookMetadata = bookRepository.importBook(sourceUri)
}
