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
        const val DEFAULT_NAME = "New Destination"
        const val CURRENT_ACCOUNT_LABEL = "Current Account"
        const val ACCOUNT_NOT_SELECTED_LABEL = "Account not selected"

        fun default(folderPath: String = "/Upload") = UploadDestination(
            id = DEFAULT_ID,
            displayName = displayNameFromPath(folderPath),
            folderPath = folderPath,
            driveAccountId = null,
            driveAccountLabel = ACCOUNT_NOT_SELECTED_LABEL,
            isEnabled = false,
            sortOrder = 0
        )

        private fun displayNameFromPath(folderPath: String): String {
            return folderPath.trim()
                .trim('/')
                .substringAfterLast('/')
                .ifBlank { DEFAULT_NAME }
        }
    }
}
