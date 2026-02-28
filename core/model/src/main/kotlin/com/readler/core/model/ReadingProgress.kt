package com.readler.core.model

data class ReadingProgress(
    val bookId: String,
    val locator: String,
    val percent: Float,
    val updatedAtEpochMs: Long
)
