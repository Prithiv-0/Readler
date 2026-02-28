package com.readler.core.data.importer

import android.content.Context
import com.readler.core.model.BookFormat
import com.readler.core.storage.ImportedBookFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class BookImportMetadataExtractor(
    private val context: Context
) : BookMetadataExtractor {

    override suspend fun extract(importedFile: ImportedBookFile): BookImportMetadata = withContext(Dispatchers.IO) {
        when (importedFile.format) {
            BookFormat.EPUB -> extractFromEpub(importedFile.filePath)
            BookFormat.PDF -> extractFromPdf(importedFile.filePath)
        }
    }

    private fun extractFromPdf(filePath: String): BookImportMetadata {
        val title = File(filePath).nameWithoutExtension
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .ifBlank { null }
        return BookImportMetadata(
            title = title,
            author = null,
            coverImagePath = null
        )
    }

    private fun extractFromEpub(filePath: String): BookImportMetadata {
        val file = File(filePath)
        if (!file.exists()) {
            return BookImportMetadata(title = null, author = null, coverImagePath = null)
        }

        return runCatching {
            ZipFile(file).use { zip ->
                val opfPath = findRootPackagePath(zip)
                val opfEntry = zip.getEntry(opfPath)
                    ?: return@use BookImportMetadata(title = null, author = null, coverImagePath = null)
                val opfContent = zip.readText(opfEntry)
                val baseDir = opfPath.substringBeforeLast('/', "")

                val title = extractXmlTag(opfContent, "dc:title")?.normalizeWhitespace()
                val author = extractXmlTag(opfContent, "dc:creator")?.normalizeWhitespace()

                val manifest = parseManifest(opfContent)
                val coverPath = extractCoverPath(zip, opfContent, manifest, baseDir)

                BookImportMetadata(
                    title = title,
                    author = author,
                    coverImagePath = coverPath
                )
            }
        }.getOrElse {
            BookImportMetadata(title = null, author = null, coverImagePath = null)
        }
    }

    private fun extractCoverPath(
        zip: ZipFile,
        opfContent: String,
        manifest: Map<String, ManifestItem>,
        baseDir: String
    ): String? {
        val coverId = Regex("""<meta[^>]*name\s*=\s*\"cover\"[^>]*content\s*=\s*\"([^\"]+)\"[^>]*/?>""", RegexOption.IGNORE_CASE)
            .find(opfContent)
            ?.groupValues
            ?.getOrNull(1)

        val explicitCoverHref = coverId?.let { manifest[it]?.href }
        val explicitCoverType = coverId?.let { manifest[it]?.mediaType }

        val fallbackCover = manifest.values.firstOrNull { it.mediaType.startsWith("image/", ignoreCase = true) }

        val href = explicitCoverHref ?: fallbackCover?.href ?: return null
        val mediaType = explicitCoverType ?: fallbackCover?.mediaType
        val entryPath = resolveRelativePath(baseDir, href)
        val entry = zip.getEntry(entryPath) ?: return null

        return zip.getInputStream(entry).use { input ->
            val ext = extensionFor(mediaType, href)
            val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
            val target = File(coversDir, "${UUID.randomUUID()}.$ext")
            target.outputStream().use { output -> input.copyTo(output) }
            target.absolutePath
        }
    }

    private fun extensionFor(mediaType: String?, href: String): String {
        val fromHref = href.substringAfterLast('.', "").lowercase().ifBlank { null }
        if (fromHref != null) return fromHref

        return when (mediaType?.lowercase()) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "img"
        }
    }

    private fun extractXmlTag(xml: String, tag: String): String? {
        return Regex("""<$tag[^>]*>([\s\S]*?)</$tag>""", RegexOption.IGNORE_CASE)
            .find(xml)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(Regex("<[^>]+>"), "")
            ?.replace("&amp;", "&")
            ?.replace("&lt;", "<")
            ?.replace("&gt;", ">")
            ?.replace("&quot;", "\"")
            ?.replace("&apos;", "'")
    }

    private fun parseManifest(opfContent: String): Map<String, ManifestItem> {
        val regex = Regex("""<item[^>]*id\s*=\s*\"([^\"]+)\"[^>]*href\s*=\s*\"([^\"]+)\"([^>]*)/?>""", RegexOption.IGNORE_CASE)
        return regex.findAll(opfContent).associate { match ->
            val attrsTail = match.groupValues[3]
            val mediaType = Regex("""media-type\s*=\s*\"([^\"]+)\"""", RegexOption.IGNORE_CASE)
                .find(attrsTail)
                ?.groupValues
                ?.getOrNull(1)
                .orEmpty()
            val item = ManifestItem(
                href = match.groupValues[2],
                mediaType = mediaType
            )
            match.groupValues[1] to item
        }
    }

    private fun findRootPackagePath(zip: ZipFile): String {
        val containerEntry = zip.getEntry("META-INF/container.xml") ?: return ""
        val containerXml = zip.readText(containerEntry)
        return Regex("""full-path\s*=\s*\"([^\"]+)\"""", RegexOption.IGNORE_CASE)
            .find(containerXml)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
    }

    private fun resolveRelativePath(baseDir: String, href: String): String {
        if (href.startsWith('/')) return href.removePrefix("/")
        val sanitized = href.substringBefore('#')
        return if (baseDir.isBlank()) sanitized else "$baseDir/$sanitized"
    }

    private fun ZipFile.readText(entry: ZipEntry): String {
        return getInputStream(entry).bufferedReader().use { it.readText() }
    }

    private data class ManifestItem(
        val href: String,
        val mediaType: String
    )

    private fun String.normalizeWhitespace(): String {
        return replace(Regex("\\s+"), " ").trim()
    }
}
