package com.readler.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "highlights")
data class HighlightEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val locator: String,
    val quote: String,
    val colorHex: String,
    val createdAtEpochMs: Long
)
