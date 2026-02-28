package com.readler.core.reader

import com.readler.core.model.BookFormat

interface ReaderEngine {
    val format: BookFormat
    suspend fun open(source: ReaderSource): ReaderDocument
}
