package com.readler.core.storage

import com.readler.core.model.BookFormat

data class ImportedBookFile(
    val filePath: String,
    val format: BookFormat,
    val displayName: String
)
