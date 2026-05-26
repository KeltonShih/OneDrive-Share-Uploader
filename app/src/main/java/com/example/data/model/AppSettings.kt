package com.example.data.model

data class AppSettings(
    val defaultFolder: String = "/手機快速上傳",
    val conflictBehavior: ConflictBehavior = ConflictBehavior.RENAME,
    val wifiOnly: Boolean = false,
    val rulesEnabled: Boolean = false
)
