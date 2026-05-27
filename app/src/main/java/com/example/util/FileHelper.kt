package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object FileHelper {
    private const val COPY_ATTEMPTS = 5
    private const val COPY_RETRY_DELAY_MS = 700L

    fun queryDisplayName(context: Context, uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            name = cursor.getString(index)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        if (name.isNullOrBlank()) {
            val lastSegment = uri.lastPathSegment
            name = if (!lastSegment.isNullOrBlank()) {
                lastSegment
            } else {
                val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                "upload_${sdf.format(Date())}"
            }
        }
        return name ?: "upload_file"
    }

    fun queryFileSize(context: Context, uri: Uri): Long {
        var size: Long = -1
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (index != -1) {
                            size = cursor.getLong(index)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        if (size < 0) {
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    size = afd.length
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        return size
    }

    fun getMimeType(context: Context, uri: Uri): String {
        var mimeType: String? = context.contentResolver.getType(uri)
        if (mimeType.isNullOrBlank()) {
            val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            if (!extension.isNullOrBlank()) {
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            }
        }
        return mimeType ?: "application/octet-stream"
    }

    fun sanitizeFileName(fileName: String): String {
        val invalidChars = "[/\\\\:*?\"<>|]"
        var sanitized = fileName.replace(invalidChars.toRegex(), "_")
        // Clean multiple underscores and trailing/leading spaces
        sanitized = sanitized.trim()
        if (sanitized.isBlank()) {
            sanitized = "upload_${UUID.randomUUID().toString().take(6)}"
        }
        return sanitized
    }

    fun encodeGraphPathSegment(segment: String): String {
        return URLEncoder.encode(segment, "UTF-8")
            .replace("+", "%20")
            .replace("%2F", "/")
            .replace("%3A", ":")
    }

    fun encodeGraphFolderPath(folderPath: String): String {
        // Splitting into segments, encoding each, then rejoining with /
        return folderPath.split("/")
            .filter { it.isNotEmpty() }
            .joinToString("/") { encodeGraphPathSegment(it) }
    }

    /**
     * Copy the content of a Uri to a local private cache file
     */
    fun copyUriToCache(context: Context, uri: Uri, sanitizedName: String): File {
        val cacheDir = context.cacheDir
        val uniquePrefix = UUID.randomUUID().toString().take(8)
        val destinationFile = File(cacheDir, "${uniquePrefix}_${sanitizedName}")

        var lastError: Exception? = null
        repeat(COPY_ATTEMPTS) { attempt ->
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(destinationFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: throw Exception("Failed to open input stream for URI: $uri")

                if (destinationFile.length() > 0L || queryFileSize(context, uri) == 0L) {
                    return destinationFile
                }
                throw Exception("Provider returned an empty file stream.")
            } catch (e: Exception) {
                lastError = e
                if (destinationFile.exists()) {
                    destinationFile.delete()
                }
                if (attempt < COPY_ATTEMPTS - 1) {
                    Thread.sleep(COPY_RETRY_DELAY_MS)
                }
            }
        }

        throw Exception(
            unreadableSourceMessage(uri),
            lastError
        )
    }

    fun unreadableSourceMessage(uri: Uri): String {
        val authority = uri.authority.orEmpty().lowercase()
        return if (authority.contains("line") || authority.contains("naver")) {
            "LINE has not downloaded this file yet. Open the file in LINE once, wait for it to finish loading, then share it again."
        } else {
            "File is not readable yet. It may still be downloading in the source app. Try again after the file finishes downloading."
        }
    }
}
