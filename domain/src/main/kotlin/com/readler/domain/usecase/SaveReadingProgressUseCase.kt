package com.readler.domain.usecase

import com.readler.core.model.ReadingProgress
import com.readler.domain.repository.BookRepository

class SaveReadingProgressUseCase(
    private val bookRepository: BookRepository
) {
    suspend operator fun invoke(progress: ReadingProgress) {
        bookRepository.saveReadingProgress(progress)
    }
}
