package com.readler.core.database.mapper

import com.readler.core.database.entity.BookEntity
import com.readler.core.model.BookMetadata
import com.readler.core.model.ReadingProgress

fun BookEntity.toMetadata(): BookMetadata {
    return BookMetadata(
        id = id,
        title = title,
        author = author,
        format = format,
        filePath = filePath,
        coverImagePath = coverImagePath,
        lastOpenedAtEpochMs = lastOpenedAtEpochMs
    )
}

fun BookEntity.toProgressOrNull(): ReadingProgress? {
    val locatorValue = progressLocator ?: return null
    val updatedAtValue = progressUpdatedAtEpochMs ?: return null
    return ReadingProgress(
        bookId = id,
        locator = locatorValue,
        percent = progressPercent,
        updatedAtEpochMs = updatedAtValue
    )
}
