package com.readler.core.data

import com.readler.core.data.importer.BookMetadataExtractor
import com.readler.core.database.dao.BookDao
import com.readler.core.database.entity.BookEntity
import com.readler.core.database.mapper.toMetadata
import com.readler.core.database.mapper.toProgressOrNull
import com.readler.core.model.BookMetadata
import com.readler.core.model.BookFormat
import com.readler.core.model.OpenedBook
import com.readler.core.model.BookSearchResult
import com.readler.core.model.ReadingProgress
import com.readler.core.reader.locator.ReaderLocatorCodec
import com.readler.core.storage.BookFileStorage
import com.readler.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile

class BookRepositoryImpl(
    private val bookDao: BookDao,
    private val fileStorage: BookFileStorage,
    private val metadataExtractor: BookMetadataExtractor
) : BookRepository {

    override fun observeLibrary(): Flow<List<BookMetadata>> {
        return bookDao.observeLibrary().map { books -> books.map { it.toMetadata() } }
    }

    override suspend fun importBook(sourceUri: String): BookMetadata {
        val importedFile = fileStorage.importBook(sourceUri)
        val extractedMetadata = metadataExtractor.extract(importedFile)
        val now = System.currentTimeMillis()
        val fallbackTitle = importedFile.displayName

        val entity = BookEntity(
            id = UUID.randomUUID().toString(),
            title = extractedMetadata.title ?: fallbackTitle,
            author = extractedMetadata.author,
            format = importedFile.format,
            filePath = importedFile.filePath,
            coverImagePath = extractedMetadata.coverImagePath,
            lastOpenedAtEpochMs = now,
            progressLocator = null,
            progressPercent = 0f,
            progressUpdatedAtEpochMs = null
        )

        bookDao.upsert(entity)
        return entity.toMetadata()
    }

    override suspend fun openBook(bookId: String): OpenedBook {
        val entity = requireNotNull(bookDao.getBook(bookId)) { "Book not found: $bookId" }
        val now = System.currentTimeMillis()
        bookDao.updateLastOpened(bookId, now)

        return OpenedBook(
            metadata = entity.copy(lastOpenedAtEpochMs = now).toMetadata(),
            startLocator = entity.progressLocator
        )
    }

    override suspend fun saveReadingProgress(progress: ReadingProgress) {
        bookDao.updateProgress(
            bookId = progress.bookId,
            locator = progress.locator,
            percent = progress.percent.coerceIn(0f, 1f),
            updatedAtEpochMs = progress.updatedAtEpochMs
        )
    }

    override suspend fun getReadingProgress(bookId: String): ReadingProgress? {
        return bookDao.getBook(bookId)?.toProgressOrNull()
    }

    override suspend fun searchInBook(bookId: String, query: String): List<BookSearchResult> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return emptyList()

        val book = bookDao.getBook(bookId) ?: return emptyList()
        return when (book.format) {
            BookFormat.EPUB -> searchInEpub(book.filePath, normalizedQuery)
            BookFormat.PDF -> searchInPdf(book.filePath, normalizedQuery)
        }
    }

    private fun searchInEpub(filePath: String, query: String): List<BookSearchResult> {
        val file = File(filePath)
        if (!file.exists()) return emptyList()

        return runCatching {
            val lowerQuery = query.lowercase(Locale.getDefault())
            val plainText = ZipFile(file).use { zip ->
                val chapterTexts = zip.entries().asSequence()
                    .filter { !it.isDirectory && (it.name.endsWith(".xhtml", true) || it.name.endsWith(".html", true) || it.name.endsWith(".htm", true)) }
                    .map { entry ->
                        zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    }
                    .map { html -> html.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim() }
                    .filter { it.isNotBlank() }
                    .toList()

                chapterTexts.joinToString("\n")
            }

            if (plainText.isBlank()) return emptyList()

            val lowerText = plainText.lowercase(Locale.getDefault())
            val results = mutableListOf<BookSearchResult>()
            var fromIndex = 0

            while (results.size < 20) {
                val index = lowerText.indexOf(lowerQuery, startIndex = fromIndex)
                if (index < 0) break

                val percent = if (plainText.length <= 1) 0f else index.toFloat() / (plainText.length - 1).toFloat()
                val start = (index - 50).coerceAtLeast(0)
                val end = (index + query.length + 70).coerceAtMost(plainText.length)
                val snippet = plainText.substring(start, end).trim()

                results.add(
                    BookSearchResult(
                        locator = ReaderLocatorCodec.encodeEpubScrollPercent(percent),
                        snippet = snippet,
                        percent = percent
                    )
                )

                fromIndex = index + query.length
            }

            results
        }.getOrDefault(emptyList())
    }

    private fun searchInPdf(filePath: String, query: String): List<BookSearchResult> {
        val normalized = query.trim().lowercase(Locale.getDefault())
        val pageNumber = when {
            normalized.startsWith("page ") -> normalized.removePrefix("page ").toIntOrNull()
            else -> normalized.toIntOrNull()
        }

        if (pageNumber != null && pageNumber > 0) {
            val pageIndex = pageNumber - 1
            return listOf(
                BookSearchResult(
                    locator = ReaderLocatorCodec.encodePdfPageIndex(pageIndex),
                    snippet = "Jump to page $pageNumber",
                    percent = 0f
                )
            )
        }

        return emptyList()
    }
}
