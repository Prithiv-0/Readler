package com.readler.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val locator: String,
    val text: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)
