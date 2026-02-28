package com.readler.core.reader

sealed class ReaderDocument {
    abstract val filePath: String
    abstract val initialLocator: String?

    data class Epub(
        override val filePath: String,
        override val initialLocator: String?,
        val htmlContent: String,
        val chapterCount: Int
    ) : ReaderDocument()

    data class Pdf(
        override val filePath: String,
        override val initialLocator: String?,
        val initialPageIndex: Int
    ) : ReaderDocument()
}
