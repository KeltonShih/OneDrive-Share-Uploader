package com.example.data.local

import androidx.room.TypeConverter
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
}
