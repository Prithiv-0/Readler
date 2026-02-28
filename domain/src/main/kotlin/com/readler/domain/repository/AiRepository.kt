package com.readler.domain.repository

interface AiRepository {
    suspend fun isEnabled(): Boolean
    suspend fun setEnabled(enabled: Boolean)
    suspend fun setApiKey(apiKey: String)
    suspend fun getApiKey(): String
    suspend fun getCapability(): AiCapability
    suspend fun askQuestion(bookId: String, bookTitle: String, author: String?, question: String): String
    suspend fun explainSelection(bookId: String, bookTitle: String, selectedText: String): String
    suspend fun translateSelection(bookId: String, bookTitle: String, selectedText: String, targetLanguage: String): String
    suspend fun suggestSimilarBooks(bookId: String, bookTitle: String, author: String?): String
    suspend fun summarizeSection(bookId: String, bookTitle: String, sectionText: String): String
    suspend fun enqueueRequest(request: AiQueuedRequest)
    suspend fun flushQueuedRequests(): Int
}
