package com.readler.core.storage

import java.io.InputStream

interface BookFileStorage {
    suspend fun importBook(sourceUri: String): ImportedBookFile
    suspend fun open(filePath: String): InputStream
}
