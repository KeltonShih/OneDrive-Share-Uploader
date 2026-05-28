package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "upload_jobs")
data class UploadJobEntity(
    @PrimaryKey val id: String,
    val localCachePath: String,
    val originalFileName: String,
    val sanitizedFileName: String,
    val mimeType: String?,
    val fileSize: Long,
    val targetFolder: String,
    val destinationId: String,
    val destinationName: String,
    val driveAccountId: String?,
    val driveAccountLabel: String?,
    val status: UploadStatus,
    val progress: Int,
    val uploadedBytes: Long,
    val totalBytes: Long,
    val errorMessage: String?,
    val uploadedFileName: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val retryCount: Int
)
