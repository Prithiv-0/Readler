package com.readler.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.readler.domain.repository.AiCapability
import com.readler.domain.repository.AiQueuedRequest
import com.readler.domain.repository.AiRepository
import com.readler.domain.repository.AiRequestType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class GeminiAiRepository(
    context: Context,
    private val defaultApiKey: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AiRepository {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val storageDir = File(appContext.filesDir, "ai").apply { mkdirs() }
    private val cacheFile = File(storageDir, "cache.jsonl")
    private val queueFile = File(storageDir, "queue.jsonl")

    override suspend fun isEnabled(): Boolean = withContext(ioDispatcher) {
        prefs.getBoolean(KEY_ENABLED, false)
    }

    override suspend fun setEnabled(enabled: Boolean) {
        withContext(ioDispatcher) {
            prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        }
    }

    override suspend fun setApiKey(apiKey: String) {
        withContext(ioDispatcher) {
            prefs.edit().putString(KEY_API_KEY, apiKey.trim()).apply()
        }
    }

    override suspend fun getApiKey(): String = withContext(ioDispatcher) {
        currentApiKey()
    }

    override suspend fun getCapability(): AiCapability = withContext(ioDispatcher) {
        AiCapability(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            hasApiKey = currentApiKey().isNotBlank(),
            hasNetwork = isNetworkAvailable()
        )
    }

    override suspend fun askQuestion(
        bookId: String,
        bookTitle: String,
        author: String?,
        question: String
    ): String {
        val normalized = question.trim()
        return generateWithCaching(
            request = AiQueuedRequest(
                bookId = bookId,
                bookTitle = bookTitle,
                author = author,
                type = AiRequestType.QUESTION,
                prompt = normalized
            ),
            promptText = buildQuestionPrompt(bookId, bookTitle, author, normalized)
        )
    }

    override suspend fun explainSelection(
        bookId: String,
        bookTitle: String,
        selectedText: String
    ): String {
        val normalized = selectedText.trim()
        return generateWithCaching(
            request = AiQueuedRequest(
                bookId = bookId,
                bookTitle = bookTitle,
                author = null,
                type = AiRequestType.EXPLAIN_SELECTION,
                prompt = "Explain this selected text.",
                selectedText = normalized
            ),
            promptText = buildExplainPrompt(bookTitle, normalized)
        )
    }

    override suspend fun translateSelection(
        bookId: String,
        bookTitle: String,
        selectedText: String,
        targetLanguage: String
    ): String {
        val normalized = selectedText.trim()
        val target = targetLanguage.trim()
        return generateWithCaching(
            request = AiQueuedRequest(
                bookId = bookId,
                bookTitle = bookTitle,
                author = null,
                type = AiRequestType.TRANSLATE_SELECTION,
                prompt = "Translate selected text.",
                selectedText = normalized,
                targetLanguage = target
            ),
            promptText = buildTranslatePrompt(bookTitle, normalized, target)
        )
    }

    override suspend fun suggestSimilarBooks(
        bookId: String,
        bookTitle: String,
        author: String?
    ): String {
        return generateWithCaching(
            request = AiQueuedRequest(
                bookId = bookId,
                bookTitle = bookTitle,
                author = author,
                type = AiRequestType.SIMILAR_BOOKS,
                prompt = "Suggest similar books"
            ),
            promptText = buildSimilarBooksPrompt(bookTitle, author)
        )
    }

    override suspend fun summarizeSection(
        bookId: String,
        bookTitle: String,
        sectionText: String
    ): String {
        val normalized = sectionText.trim()
        return generateWithCaching(
            request = AiQueuedRequest(
                bookId = bookId,
                bookTitle = bookTitle,
                author = null,
                type = AiRequestType.SECTION_SUMMARY,
                prompt = "Summarize current section.",
                sectionContext = normalized
            ),
            promptText = buildSectionSummaryPrompt(bookTitle, normalized)
        )
    }

    override suspend fun enqueueRequest(request: AiQueuedRequest) {
        withContext(ioDispatcher) {
            appendJsonLine(queueFile, requestToJson(request))
        }
    }

    override suspend fun flushQueuedRequests(): Int = withContext(ioDispatcher) {
        val capability = getCapability()
        if (!capability.canRun || !queueFile.exists()) return@withContext 0

        val queued = queueFile.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { line -> runCatching { jsonToRequest(JSONObject(line)) }.getOrNull() }

        if (queued.isEmpty()) {
            queueFile.writeText("")
            return@withContext 0
        }

        var processed = 0
        val remaining = mutableListOf<AiQueuedRequest>()
        queued.forEach { request ->
            val result = runCatching {
                val prompt = buildPromptForRequest(request)
                generateRaw(prompt, request.bookId)
            }
            if (result.isSuccess) {
                processed += 1
            } else {
                remaining += request
            }
        }

        queueFile.writeText("")
        remaining.forEach { appendJsonLine(queueFile, requestToJson(it)) }
        processed
    }

    private suspend fun generateWithCaching(request: AiQueuedRequest, promptText: String): String {
        val capability = getCapability()
        require(capability.enabled) { "AI is disabled" }
        require(capability.hasApiKey) { "Gemini API key missing" }
        require(capability.hasNetwork) { "Network unavailable" }

        val cacheKey = cacheKey(request.bookId, promptText)
        readCached(cacheKey)?.let { return it }

        val response = generateRaw(promptText, request.bookId)
        writeCache(cacheKey, request.bookId, response)
        return response
    }

    private suspend fun generateRaw(promptText: String, bookId: String): String = withContext(ioDispatcher) {
        val apiKey = currentApiKey()
        require(apiKey.isNotBlank()) { "Gemini API key missing" }

        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 45_000
            setRequestProperty("Content-Type", "application/json")
        }

        val requestBody = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", promptText))
                    )
                )
            )
            .toString()

        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(requestBody)
        }

        val body = runCatching {
            connection.inputStream.bufferedReader().use { it.readText() }
        }.getOrElse {
            connection.errorStream?.bufferedReader()?.use { stream ->
                stream.readText()
            } ?: "AI call failed"
        }

        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("Gemini API error: ${connection.responseCode} $body")
        }

        val json = JSONObject(body)
        val answer = json.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text")
            ?.trim()
            .orEmpty()

        if (answer.isBlank()) {
            throw IllegalStateException("Gemini returned an empty response")
        }

        appendConversation(bookId, promptText, answer)
        answer
    }

    private fun appendConversation(bookId: String, prompt: String, response: String) {
        val conversationFile = File(storageDir, "conversation_$bookId.jsonl")
        val line = JSONObject()
            .put("timestamp", System.currentTimeMillis())
            .put("prompt", prompt)
            .put("response", response)
            .toString()
        appendJsonLine(conversationFile, line)

        val lines = conversationFile.readLines()
        if (lines.size > MAX_CONVERSATION_ENTRIES) {
            conversationFile.writeText(lines.takeLast(MAX_CONVERSATION_ENTRIES).joinToString("\n") + "\n")
        }
    }

    private fun buildPromptForRequest(request: AiQueuedRequest): String {
        return when (request.type) {
            AiRequestType.QUESTION -> buildQuestionPrompt(request.bookId, request.bookTitle, request.author, request.prompt)
            AiRequestType.EXPLAIN_SELECTION -> buildExplainPrompt(request.bookTitle, request.selectedText.orEmpty())
            AiRequestType.TRANSLATE_SELECTION -> buildTranslatePrompt(
                request.bookTitle,
                request.selectedText.orEmpty(),
                request.targetLanguage.orEmpty()
            )
            AiRequestType.SIMILAR_BOOKS -> buildSimilarBooksPrompt(request.bookTitle, request.author)
            AiRequestType.SECTION_SUMMARY -> buildSectionSummaryPrompt(request.bookTitle, request.sectionContext.orEmpty())
        }
    }

    private fun buildQuestionPrompt(bookId: String, bookTitle: String, author: String?, question: String): String {
        val context = readConversationContext(bookId)
        return """
            You are assisting a reader.
            Book title: $bookTitle
            Author: ${author ?: "Unknown"}
            Keep answers concise and grounded in the book context.
            Previous conversation context:
            $context

            User question:
            $question
        """.trimIndent()
    }

    private fun buildExplainPrompt(bookTitle: String, selectedText: String): String {
        return """
            Explain the selected passage from "$bookTitle" in clear, simple language.
            Include short clarification of hard words.

            Selected text:
            $selectedText
        """.trimIndent()
    }

    private fun buildTranslatePrompt(bookTitle: String, selectedText: String, targetLanguage: String): String {
        return """
            Translate the selected passage from "$bookTitle" into $targetLanguage.
            Keep the original tone and meaning.

            Selected text:
            $selectedText
        """.trimIndent()
    }

    private fun buildSimilarBooksPrompt(bookTitle: String, author: String?): String {
        return """
            Recommend 5 books similar to "$bookTitle" by ${author ?: "the same genre"}.
            Provide title, author, and one-line reason each.
        """.trimIndent()
    }

    private fun buildSectionSummaryPrompt(bookTitle: String, sectionText: String): String {
        return """
            Summarize the following section from "$bookTitle".
            Keep it under 8 bullet points and include key events/concepts.

            Section text:
            $sectionText
        """.trimIndent()
    }

    private fun readConversationContext(bookId: String): String {
        val file = File(storageDir, "conversation_$bookId.jsonl")
        if (!file.exists()) return "(none)"
        return file.readLines()
            .takeLast(5)
            .joinToString("\n")
            .ifBlank { "(none)" }
    }

    private fun requestToJson(request: AiQueuedRequest): String {
        return JSONObject()
            .put("requestId", request.requestId)
            .put("bookId", request.bookId)
            .put("bookTitle", request.bookTitle)
            .put("author", request.author)
            .put("type", request.type.name)
            .put("prompt", request.prompt)
            .put("selectedText", request.selectedText)
            .put("targetLanguage", request.targetLanguage)
            .put("sectionContext", request.sectionContext)
            .put("createdAtEpochMs", request.createdAtEpochMs)
            .toString()
    }

    private fun jsonToRequest(json: JSONObject): AiQueuedRequest {
        return AiQueuedRequest(
            requestId = json.optString("requestId"),
            bookId = json.optString("bookId"),
            bookTitle = json.optString("bookTitle"),
            author = json.optString("author").ifBlank { null },
            type = AiRequestType.valueOf(json.optString("type")),
            prompt = json.optString("prompt"),
            selectedText = json.optString("selectedText").ifBlank { null },
            targetLanguage = json.optString("targetLanguage").ifBlank { null },
            sectionContext = json.optString("sectionContext").ifBlank { null },
            createdAtEpochMs = json.optLong("createdAtEpochMs", System.currentTimeMillis())
        )
    }

    private fun cacheKey(bookId: String, prompt: String): String = "$bookId::${prompt.trim()}"

    private fun readCached(cacheKey: String): String? {
        if (!cacheFile.exists()) return null
        return cacheFile.readLines()
            .asReversed()
            .mapNotNull { line ->
                runCatching {
                    val json = JSONObject(line)
                    if (json.optString("cacheKey") == cacheKey) json.optString("response") else null
                }.getOrNull()
            }
            .firstOrNull { !it.isNullOrBlank() }
    }

    private fun writeCache(cacheKey: String, bookId: String, response: String) {
        val line = JSONObject()
            .put("cacheKey", cacheKey)
            .put("bookId", bookId)
            .put("response", response)
            .put("timestamp", System.currentTimeMillis())
            .toString()
        appendJsonLine(cacheFile, line)
    }

    private fun appendJsonLine(file: File, line: String) {
        file.appendText(line + "\n")
    }

    private fun isNetworkAvailable(): Boolean {
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun currentApiKey(): String {
        val saved = prefs.getString(KEY_API_KEY, null)?.trim().orEmpty()
        if (saved.isNotBlank()) {
            return saved
        }
        return defaultApiKey.trim()
    }

    companion object {
        private const val PREFS_NAME = "readler_ai"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_API_KEY = "api_key"
        private const val MAX_CONVERSATION_ENTRIES = 20
    }
}
