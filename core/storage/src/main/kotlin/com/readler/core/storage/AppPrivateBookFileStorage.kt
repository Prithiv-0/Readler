package com.readler.core.storage

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.UUID

class AppPrivateBookFileStorage(
    private val context: Context
) : BookFileStorage {

    override suspend fun importBook(sourceUri: String): ImportedBookFile = withContext(Dispatchers.IO) {
        val parsedUri = Uri.parse(sourceUri)
        val extension = detectExtension(parsedUri)
        val format = when (extension) {
            "epub" -> com.readler.core.model.BookFormat.EPUB
            "pdf" -> com.readler.core.model.BookFormat.PDF
            else -> throw IllegalArgumentException("Unsupported format for uri: $parsedUri")
        }

        val rawDisplayName = queryDisplayName(parsedUri)
        val displayName = rawDisplayName
            ?.removeSuffix(".epub")?.removeSuffix(".pdf")
            ?.removeSuffix(".EPUB")?.removeSuffix(".PDF")
            ?.trim()
            ?.ifBlank { null }
            ?: UUID.randomUUID().toString()

        val booksDir = File(context.filesDir, "books").apply { mkdirs() }
        val targetFile = File(booksDir, "${UUID.randomUUID()}.$extension")

        context.contentResolver.openInputStream(parsedUri).use { input ->
            requireNotNull(input) { "Unable to open input stream for $parsedUri" }
            targetFile.outputStream().use { output -> input.copyTo(output) }
        }

        ImportedBookFile(
            filePath = targetFile.absolutePath,
            format = format,
            displayName = displayName
        )
    }

    override suspend fun open(filePath: String): InputStream = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) {
            throw FileNotFoundException("Book file missing at $filePath")
        }
        file.inputStream()
    }

    /**
     * Detect file extension using multiple strategies:
     * 1. ContentResolver MIME type (most reliable for SAF content URIs)
     * 2. File extension from URI path / display name
     */
    private fun detectExtension(uri: Uri): String {
        // Strategy 1: Use ContentResolver to get MIME type
        val mimeType = context.contentResolver.getType(uri)
        val extFromMime = mimeType?.let { mapMimeToExtension(it) }
        if (extFromMime != null) return extFromMime

        // Strategy 2: Check the display name from the content provider
        val displayName = queryDisplayName(uri)
        if (displayName != null) {
            val ext = displayName.substringAfterLast('.', "").lowercase()
            if (ext == "epub" || ext == "pdf") return ext
        }

        // Strategy 3: Fall back to lastPathSegment (works for file:// URIs)
        val path = uri.lastPathSegment.orEmpty().lowercase()
        return when {
            path.endsWith(".epub") -> "epub"
            path.endsWith(".pdf") -> "pdf"
            else -> throw IllegalArgumentException(
                "Unsupported file type: $uri (mime=$mimeType, displayName=$displayName)"
            )
        }
    }

    private fun mapMimeToExtension(mime: String): String? = when (mime.lowercase()) {
        "application/epub+zip" -> "epub"
        "application/pdf" -> "pdf"
        else -> null
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        }
    }.getOrNull()
}
