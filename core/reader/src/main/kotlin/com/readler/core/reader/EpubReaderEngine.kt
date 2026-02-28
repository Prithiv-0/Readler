package com.readler.core.reader

import com.readler.core.model.BookFormat
import com.readler.core.reader.epub.EpubArchiveParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EpubReaderEngine : ReaderEngine {
    override val format: BookFormat = BookFormat.EPUB
    private val parser = EpubArchiveParser()

    override suspend fun open(source: ReaderSource): ReaderDocument = withContext(Dispatchers.IO) {
        val parsed = parser.parseCombinedHtml(source.filePath)
        ReaderDocument.Epub(
            filePath = source.filePath,
            initialLocator = source.initialLocator,
            htmlContent = parsed.htmlContent,
            chapterCount = parsed.chapterCount
        )
    }
}
