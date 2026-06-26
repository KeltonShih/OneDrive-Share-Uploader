package com.example.data.model

enum class UploadStatus {
    COPYING,
    QUEUED,
    UPLOADING,
    WAITING_CONFLICT,
    SUCCESS,
    FAILED,
    CANCELED
}

enum class ConflictBehavior {
    ASK,
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
