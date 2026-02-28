package com.readler.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.readler.core.database.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY COALESCE(lastOpenedAtEpochMs, 0) DESC, title ASC")
    fun observeLibrary(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    suspend fun getBook(bookId: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(book: BookEntity)

    @Query(
        """
        UPDATE books
        SET progressLocator = :locator,
            progressPercent = :percent,
            progressUpdatedAtEpochMs = :updatedAtEpochMs
        WHERE id = :bookId
        """
    )
    suspend fun updateProgress(bookId: String, locator: String, percent: Float, updatedAtEpochMs: Long)

    @Query("UPDATE books SET lastOpenedAtEpochMs = :openedAtEpochMs WHERE id = :bookId")
    suspend fun updateLastOpened(bookId: String, openedAtEpochMs: Long)
}
