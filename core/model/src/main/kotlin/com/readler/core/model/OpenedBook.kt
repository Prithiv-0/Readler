package com.readler.core.model

data class OpenedBook(
    val metadata: BookMetadata,
    val startLocator: String?
)
