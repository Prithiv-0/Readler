package com.readler.core.data

import com.readler.core.data.importer.BookImportMetadata
import com.readler.core.data.importer.BookMetadataExtractor
import com.readler.core.database.dao.BookDao
import com.readler.core.database.entity.BookEntity
import com.readler.core.model.BookFormat
import com.readler.core.model.BookSearchResult
import com.readler.core.model.ReadingProgress
import com.readler.core.storage.BookFileStorage
import com.readler.core.storage.ImportedBookFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BookRepositoryImplTest {

    @Test
    fun importBook_usesExtractedMetadata() = runTest {
        val dao = FakeBookDao()
        val storage = FakeBookFileStorage(
            importedBookFile = ImportedBookFile(
                filePath = "book.epub",
                format = BookFormat.EPUB,
                displayName = "fallback-title"
            )
        )
        val extractor = FakeMetadataExtractor(
            BookImportMetadata(
                title = "Deep Work",
                author = "Cal Newport",
                coverImagePath = "/tmp/cover.png"
            )
        )

        val repository = BookRepositoryImpl(dao, storage, extractor)

        val imported = repository.importBook("content://books/deep-work.epub")

        assertEquals("Deep Work", imported.title)
        assertEquals("Cal Newport", imported.author)
        assertEquals("/tmp/cover.png", imported.coverImagePath)
        assertEquals(1, dao.lastSnapshot().size)
    }

    @Test
    fun saveReadingProgress_clampsPercentToOne() = runTest {
        val dao = FakeBookDao()
        val bookId = "book-1"
        dao.upsert(
            BookEntity(
                id = bookId,
                title = "Book",
                author = null,
                format = BookFormat.PDF,
                filePath = "book.pdf",
                coverImagePath = null,
                lastOpenedAtEpochMs = null,
                progressLocator = null,
                progressPercent = 0f,
                progressUpdatedAtEpochMs = null
            )
        )

        val repository = BookRepositoryImpl(
            bookDao = dao,
            fileStorage = FakeBookFileStorage(ImportedBookFile("book.pdf", BookFormat.PDF, "Book")),
            metadataExtractor = FakeMetadataExtractor(BookImportMetadata(null, null, null))
        )

        repository.saveReadingProgress(
            ReadingProgress(
                bookId = bookId,
                locator = "pdf-page:2",
                percent = 3.5f,
                updatedAtEpochMs = 99L
            )
        )

        val saved = dao.getBook(bookId)
        assertNotNull(saved)
        assertEquals(1f, saved?.progressPercent)
        assertEquals("pdf-page:2", saved?.progressLocator)
    }

    @Test
    fun searchInBook_pdfQueryAsPageNumber_returnsLocator() = runTest {
        val dao = FakeBookDao()
        val bookId = "pdf-1"
        dao.upsert(
            BookEntity(
                id = bookId,
                title = "PDF",
                author = null,
                format = BookFormat.PDF,
                filePath = "a.pdf",
                coverImagePath = null,
                lastOpenedAtEpochMs = null,
                progressLocator = null,
                progressPercent = 0f,
                progressUpdatedAtEpochMs = null
            )
        )

        val repository = BookRepositoryImpl(
            dao,
            FakeBookFileStorage(ImportedBookFile("a.pdf", BookFormat.PDF, "PDF")),
            FakeMetadataExtractor(BookImportMetadata(null, null, null))
        )

        val results: List<BookSearchResult> = repository.searchInBook(bookId, "page 12")

        assertEquals(1, results.size)
        assertEquals("pdf-page:11", results.first().locator)
    }

    @Test
    fun searchInBook_epubTextMatch_returnsSnippetAndLocator() = runTest {
        val tempEpub = createTempFile(suffix = ".epub")
        createSimpleEpub(tempEpub, "A sample chapter with Kotlin coroutines and flows.")

        val dao = FakeBookDao()
        val bookId = "epub-1"
        dao.upsert(
            BookEntity(
                id = bookId,
                title = "EPUB",
                author = null,
                format = BookFormat.EPUB,
                filePath = tempEpub.absolutePath,
                coverImagePath = null,
                lastOpenedAtEpochMs = null,
                progressLocator = null,
                progressPercent = 0f,
                progressUpdatedAtEpochMs = null
            )
        )

        val repository = BookRepositoryImpl(
            dao,
            FakeBookFileStorage(ImportedBookFile(tempEpub.absolutePath, BookFormat.EPUB, "EPUB")),
            FakeMetadataExtractor(BookImportMetadata(null, null, null))
        )

        val results = repository.searchInBook(bookId, "coroutines")

        assertFalse(results.isEmpty())
        assertTrue(results.first().locator.startsWith("epub-scroll:"))
        assertTrue(results.first().snippet.contains("coroutines", ignoreCase = true))
    }

    private fun createSimpleEpub(target: File, bodyText: String) {
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("chapter1.xhtml"))
            zip.write("<html><body><p>$bodyText</p></body></html>".toByteArray())
            zip.closeEntry()
        }
    }

    private class FakeMetadataExtractor(
        private val metadata: BookImportMetadata
    ) : BookMetadataExtractor {
        override suspend fun extract(importedFile: ImportedBookFile): BookImportMetadata = metadata
    }

    private class FakeBookFileStorage(
        private val importedBookFile: ImportedBookFile
    ) : BookFileStorage {
        override suspend fun importBook(sourceUri: String): ImportedBookFile = importedBookFile

        override suspend fun open(filePath: String): InputStream = ByteArrayInputStream(byteArrayOf())
    }

    private class FakeBookDao : BookDao {
        private val books = linkedMapOf<String, BookEntity>()
        private val flow = MutableStateFlow<List<BookEntity>>(emptyList())

        override fun observeLibrary(): Flow<List<BookEntity>> = flow

        override suspend fun getBook(bookId: String): BookEntity? = books[bookId]

        override suspend fun upsert(book: BookEntity) {
            books[book.id] = book
            flow.value = books.values.toList()
        }

        override suspend fun updateProgress(bookId: String, locator: String, percent: Float, updatedAtEpochMs: Long) {
            val existing = books[bookId] ?: return
            books[bookId] = existing.copy(
                progressLocator = locator,
                progressPercent = percent,
                progressUpdatedAtEpochMs = updatedAtEpochMs
            )
            flow.value = books.values.toList()
        }

        override suspend fun updateLastOpened(bookId: String, openedAtEpochMs: Long) {
            val existing = books[bookId] ?: return
            books[bookId] = existing.copy(lastOpenedAtEpochMs = openedAtEpochMs)
            flow.value = books.values.toList()
        }

        fun lastSnapshot(): List<BookEntity> = books.values.toList()
    }
}
