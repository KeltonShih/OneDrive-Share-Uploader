package com.example.data.model

data class AppSettings(
    val defaultFolder: String = "/Upload",
    val conflictBehavior: ConflictBehavior = ConflictBehavior.RENAME,
    val wifiOnly: Boolean = false,
    val rulesEnabled: Boolean = false
)
