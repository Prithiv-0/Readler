package com.readler.core.data.importer

import com.readler.core.storage.ImportedBookFile

interface BookMetadataExtractor {
    suspend fun extract(importedFile: ImportedBookFile): BookImportMetadata
}
