package com.readler.domain.repository

import java.util.UUID

enum class AiRequestType {
    QUESTION,
    EXPLAIN_SELECTION,
    TRANSLATE_SELECTION,
    SIMILAR_BOOKS,
    SECTION_SUMMARY
}

data class AiQueuedRequest(
    val requestId: String = UUID.randomUUID().toString(),
    val bookId: String,
    val bookTitle: String,
    val author: String?,
    val type: AiRequestType,
    val prompt: String,
    val selectedText: String? = null,
    val targetLanguage: String? = null,
    val sectionContext: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)
