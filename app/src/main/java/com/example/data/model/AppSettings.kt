package com.example.data.model

data class AppSettings(
    val defaultFolder: String = "/Upload",
    val conflictBehavior: ConflictBehavior = ConflictBehavior.RENAME,
    val wifiOnly: Boolean = false,
    val rulesEnabled: Boolean = false,
    val languageCode: String = AppLanguage.SYSTEM.code,
    val uploadDestinations: List<UploadDestination> = listOf(UploadDestination.default(defaultFolder))
) {
    val enabledDestinations: List<UploadDestination>
        get() = uploadDestinations
            .filter { it.isEnabled && !it.driveAccountId.isNullOrBlank() }
            .sortedBy { it.sortOrder }

    val defaultDestination: UploadDestination
        get() = enabledDestinations.firstOrNull()
            ?: uploadDestinations.sortedBy { it.sortOrder }.firstOrNull()
            ?: UploadDestination.default(defaultFolder)
}
