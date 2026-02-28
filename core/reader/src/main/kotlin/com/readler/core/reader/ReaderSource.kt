package com.readler.core.reader

import java.io.InputStream

data class ReaderSource(
    val filePath: String,
    val inputStreamProvider: suspend () -> InputStream,
    val initialLocator: String? = null
)
