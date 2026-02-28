package com.readler.core.model

data class BookSearchResult(
    val locator: String,
    val snippet: String,
    val percent: Float
)
