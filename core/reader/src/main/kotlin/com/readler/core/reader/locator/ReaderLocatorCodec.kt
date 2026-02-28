package com.readler.core.reader.locator

object ReaderLocatorCodec {
    private const val EPUB_SCROLL_PREFIX = "epub-scroll:"
    private const val PDF_PAGE_PREFIX = "pdf-page:"

    fun encodeEpubScrollPercent(percent: Float): String {
        return "$EPUB_SCROLL_PREFIX${percent.coerceIn(0f, 1f)}"
    }

    fun decodeEpubScrollPercent(locator: String?): Float? {
        if (locator == null || !locator.startsWith(EPUB_SCROLL_PREFIX)) {
            return null
        }

        return locator.removePrefix(EPUB_SCROLL_PREFIX).toFloatOrNull()?.coerceIn(0f, 1f)
    }

    fun encodePdfPageIndex(pageIndex: Int): String {
        return "$PDF_PAGE_PREFIX${pageIndex.coerceAtLeast(0)}"
    }

    fun decodePdfPageIndex(locator: String?): Int? {
        if (locator == null || !locator.startsWith(PDF_PAGE_PREFIX)) {
            return null
        }

        return locator.removePrefix(PDF_PAGE_PREFIX).toIntOrNull()?.coerceAtLeast(0)
    }
}
