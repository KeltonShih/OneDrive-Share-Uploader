package com.example.data.model

data class UploadDestination(
    val id: String,
    val displayName: String,
    val folderPath: String,
    val driveAccountId: String? = null,
    val driveAccountLabel: String? = null,
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0
) {
    companion object {
        const val DEFAULT_ID = "default-upload"
        const val DEFAULT_NAME = "Upload"
        const val CURRENT_ACCOUNT_LABEL = "Current Account"

        fun default(folderPath: String = "/Upload") = UploadDestination(
            id = DEFAULT_ID,
            displayName = DEFAULT_NAME,
            folderPath = folderPath,
            driveAccountId = null,
            driveAccountLabel = CURRENT_ACCOUNT_LABEL,
            isEnabled = true,
            sortOrder = 0
        )
    }
}
