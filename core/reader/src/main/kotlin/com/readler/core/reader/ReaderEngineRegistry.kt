package com.readler.core.reader

import com.readler.core.model.BookFormat

class ReaderEngineRegistry(
    readerEngines: List<ReaderEngine>
) {
    private val enginesByFormat: Map<BookFormat, ReaderEngine> = readerEngines.associateBy { it.format }

    fun get(format: BookFormat): ReaderEngine {
        return requireNotNull(enginesByFormat[format]) { "No reader engine registered for $format" }
    }
}
