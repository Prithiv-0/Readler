package com.readler.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.readler.core.model.BookFormat

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String?,
    val format: BookFormat,
    val filePath: String,
    val coverImagePath: String?,
    val lastOpenedAtEpochMs: Long?,
    val progressLocator: String?,
    val progressPercent: Float,
    val progressUpdatedAtEpochMs: Long?
)
