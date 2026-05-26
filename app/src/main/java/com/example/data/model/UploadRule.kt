package com.example.data.model

data class UploadRule(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val ruleType: RuleType,
    val pattern: String,
    val targetFolder: String,
    val priority: Int
)
