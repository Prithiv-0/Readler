package com.readler.core.reader.epub

import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal class EpubArchiveParser {
    fun parseCombinedHtml(epubPath: String): ParsedEpub {
        val file = File(epubPath)
        require(file.exists()) { "EPUB file missing: $epubPath" }

        ZipFile(file).use { zip ->
            val rootOpfPath = findRootPackagePath(zip)
            val opfContent = zip.readText(rootOpfPath)
            val baseDir = rootOpfPath.substringBeforeLast('/', missingDelimiterValue = "")

            val manifest = parseManifest(opfContent)
            val spineItemIds = parseSpine(opfContent)

            val chapters = spineItemIds.mapNotNull { itemId ->
                val href = manifest[itemId] ?: return@mapNotNull null
                val entryPath = resolveRelativePath(baseDir, href)
                zip.readTextOrNull(entryPath)
            }

            if (chapters.isEmpty()) {
                throw IOException("No readable EPUB chapters found in $epubPath")
            }

            val html = buildString {
                append("<html><head><meta charset=\"utf-8\"/></head><body>")
                chapters.forEachIndexed { index, chapter ->
                    append("<section data-chapter=\"")
                    append(index)
                    append("\">")
                    append(stripHtmlEnvelope(chapter))
                    append("</section>")
                }
                append("</body></html>")
            }

            return ParsedEpub(htmlContent = html, chapterCount = chapters.size)
        }
    }

    private fun findRootPackagePath(zip: ZipFile): String {
        val containerEntry = zip.getEntry("META-INF/container.xml")
            ?: throw IOException("Invalid EPUB: missing META-INF/container.xml")
        val containerXml = zip.readText(containerEntry)

        val match = Regex("""full-path\s*=\s*\"([^\"]+)\"""")
            .find(containerXml)
            ?.groupValues
            ?.getOrNull(1)

        return requireNotNull(match) { "Invalid EPUB: OPF package path not found" }
    }

    private fun parseManifest(opfContent: String): Map<String, String> {
        val regex = Regex("""<item[^>]*id\s*=\s*\"([^\"]+)\"[^>]*href\s*=\s*\"([^\"]+)\"[^>]*/?>""")
        return regex.findAll(opfContent).associate { match ->
            val id = match.groupValues[1]
            val href = match.groupValues[2]
            id to href
        }
    }

    private fun parseSpine(opfContent: String): List<String> {
        val regex = Regex("""<itemref[^>]*idref\s*=\s*\"([^\"]+)\"[^>]*/?>""")
        return regex.findAll(opfContent).map { it.groupValues[1] }.toList()
    }

    private fun resolveRelativePath(baseDir: String, href: String): String {
        if (href.startsWith('/')) {
            return href.removePrefix("/")
        }

        return if (baseDir.isBlank()) {
            href.substringBefore('#')
        } else {
            "$baseDir/${href.substringBefore('#')}"
        }
    }

    private fun stripHtmlEnvelope(rawChapter: String): String {
        val bodyMatch = Regex("""<body[^>]*>([\s\S]*?)</body>""", RegexOption.IGNORE_CASE)
            .find(rawChapter)
            ?.groupValues
            ?.getOrNull(1)
        return bodyMatch ?: rawChapter
    }

    private fun ZipFile.readText(path: String): String {
        val entry = getEntry(path) ?: throw IOException("Missing EPUB entry: $path")
        return readText(entry)
    }

    private fun ZipFile.readTextOrNull(path: String): String? {
        val entry = getEntry(path) ?: return null
        return readText(entry)
    }

    private fun ZipFile.readText(entry: ZipEntry): String {
        return getInputStream(entry).bufferedReader().use { it.readText() }
    }
}

internal data class ParsedEpub(
    val htmlContent: String,
    val chapterCount: Int
)
