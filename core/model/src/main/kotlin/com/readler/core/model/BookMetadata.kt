package com.readler.core.model

data class BookMetadata(
    val id: String,
    val title: String,
    val author: String?,
    val format: BookFormat,
    val filePath: String,
    val coverImagePath: String? = null,
    val lastOpenedAtEpochMs: Long? = null
)
