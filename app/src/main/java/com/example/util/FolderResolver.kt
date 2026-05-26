package com.example.util

import com.example.data.model.AppSettings

object FolderResolver {

    /**
     * Resolves the target folder path on OneDrive based on AppSettings rules.
     */
    fun resolveTargetFolder(
        fileName: String,
        mimeType: String?,
        settings: AppSettings
    ): String {
        if (!settings.rulesEnabled) {
            return settings.defaultFolder
        }

        val mime = mimeType?.lowercase() ?: ""
        val lowercaseName = fileName.lowercase()

        // Route routing rules:
        return when {
            mime.startsWith("image/") -> {
                combinePaths(settings.defaultFolder, "Images")
            }
            mime.startsWith("video/") -> {
                combinePaths(settings.defaultFolder, "Videos")
            }
            mime == "application/pdf" || lowercaseName.endsWith(".pdf") -> {
                combinePaths(settings.defaultFolder, "PDF")
            }
            mime == "application/vnd.ms-excel" || 
            mime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ||
            lowercaseName.endsWith(".xls") || 
            lowercaseName.endsWith(".xlsx") -> {
                combinePaths(settings.defaultFolder, "Excel")
            }
            else -> {
                combinePaths(settings.defaultFolder, "Others")
            }
        }
    }

    private fun combinePaths(base: String, sub: String): String {
        val cleanBase = base.trimEnd('/')
        val cleanSub = sub.trim('/')
        return if (cleanBase.isEmpty()) "/$cleanSub" else "$cleanBase/$cleanSub"
    }
}
