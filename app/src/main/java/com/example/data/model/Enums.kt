package com.example.data.model

enum class UploadStatus {
    COPYING,
    QUEUED,
    UPLOADING,
    SUCCESS,
    FAILED,
    CANCELED
}

enum class ConflictBehavior {
    RENAME,
    REPLACE,
    FAIL
}

enum class RuleType {
    MIME_PREFIX,
    MIME_EXACT,
    EXTENSION,
    FILE_NAME_CONTAINS,
    SOURCE_APP
}
