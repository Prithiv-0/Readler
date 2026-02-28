package com.readler.core.database

import androidx.room.TypeConverter
import com.readler.core.model.BookFormat

class DatabaseConverters {
    @TypeConverter
    fun toBookFormat(raw: String): BookFormat = BookFormat.valueOf(raw)

    @TypeConverter
    fun fromBookFormat(format: BookFormat): String = format.name
}
