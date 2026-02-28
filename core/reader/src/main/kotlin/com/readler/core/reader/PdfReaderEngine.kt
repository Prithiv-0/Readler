package com.readler.core.reader

import com.readler.core.model.BookFormat
import com.readler.core.reader.locator.ReaderLocatorCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

class PdfReaderEngine : ReaderEngine {
    override val format: BookFormat = BookFormat.PDF

    override suspend fun open(source: ReaderSource): ReaderDocument = withContext(Dispatchers.IO) {
        val file = File(source.filePath)
        if (!file.exists()) {
            throw FileNotFoundException("PDF file missing: ${source.filePath}")
        }

        ReaderDocument.Pdf(
            filePath = source.filePath,
            initialLocator = source.initialLocator,
            initialPageIndex = ReaderLocatorCodec.decodePdfPageIndex(source.initialLocator) ?: 0
        )
    }
}
