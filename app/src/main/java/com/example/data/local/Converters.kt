package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.ConflictBehavior
import com.example.data.model.UploadStatus

class Converters {
    @TypeConverter
    fun fromUploadStatus(value: UploadStatus): String {
        return value.name
    }

    @TypeConverter
    fun toUploadStatus(value: String): UploadStatus {
        return try {
            UploadStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            UploadStatus.FAILED
        }
    }

    @TypeConverter
    fun fromConflictBehavior(value: ConflictBehavior?): String? {
        return value?.name
    }

    @TypeConverter
    fun toConflictBehavior(value: String?): ConflictBehavior? {
        if (value.isNullOrBlank()) return null
        return try {
            ConflictBehavior.valueOf(value)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
