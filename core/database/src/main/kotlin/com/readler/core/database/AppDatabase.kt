package com.readler.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.readler.core.database.dao.BookDao
import com.readler.core.database.dao.HighlightDao
import com.readler.core.database.dao.NoteDao
import com.readler.core.database.entity.BookEntity
import com.readler.core.database.entity.HighlightEntity
import com.readler.core.database.entity.NoteEntity

@Database(
    entities = [BookEntity::class, NoteEntity::class, HighlightEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun noteDao(): NoteDao
    abstract fun highlightDao(): HighlightDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "readler.db"
                ).fallbackToDestructiveMigration().build().also { created ->
                    instance = created
                }
            }
        }
    }
}
