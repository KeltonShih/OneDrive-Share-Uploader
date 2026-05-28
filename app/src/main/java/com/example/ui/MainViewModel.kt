package com.example.ui

import android.app.Activity
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.R
import com.example.auth.AuthState
import com.example.auth.MsalAuthManager
import com.example.data.local.AppDatabase
import com.example.data.local.DataStoreManager
import com.example.data.model.AppSettings
import com.example.data.model.ConflictBehavior
import com.example.data.model.UploadDestination
import com.example.data.model.UploadJobEntity
import com.example.data.model.UploadStatus
import com.example.data.repository.UploadRepository
import com.example.util.DestinationShortcutPublisher
import com.example.util.FileHelper
import com.example.util.FolderResolver
import com.example.util.LocaleHelper
import com.example.worker.UploadWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

data class OneDriveFolder(
    val id: String,
    val name: String,
    val path: String
)

data class FolderPickerUiState(
    val isOpen: Boolean = false,
    val isLoading: Boolean = false,
    val folders: List<OneDriveFolder> = emptyList(),
    val currentItemId: String? = null,
    val currentPath: String = "",
    val errorMessage: String? = null
)

data class AddFilesResult(
    val addedCount: Int,
    val failedCount: Int
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val db = AppDatabase.getDatabase(context)
    private val repository = UploadRepository(db.uploadJobDao())
    private val dataStoreManager = DataStoreManager(context)
    private val msalAuthManager = MsalAuthManager.getInstance(context)
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val uploadTasks: StateFlow<List<UploadJobEntity>> = repository.allJobs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val history: StateFlow<List<UploadJobEntity>> = repository.history
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val appSettings: StateFlow<AppSettings> = dataStoreManager.appSettingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    val authState: StateFlow<AuthState> = msalAuthManager.authStateFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AuthState.Uninitialized
        )

    private val _folderPickerState = MutableStateFlow(FolderPickerUiState())
    val folderPickerState: StateFlow<FolderPickerUiState> = _folderPickerState

    fun triggerWorkManager() {
        val settings = appSettings.value
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .build()

        val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork("OneDriveUploader", ExistingWorkPolicy.REPLACE, uploadWorkRequest)
    }

    fun addSelectedFiles(
        uris: List<Uri>,
        destinationId: String? = null,
        onResult: (AddFilesResult) -> Unit
    ) {
        viewModelScope.launch {
            val settings = dataStoreManager.appSettingsFlow.first()
            val textContext = LocaleHelper.localizedContext(context, settings.languageCode)
            val destination = destinationId
                ?.let { id -> settings.uploadDestinations.firstOrNull { it.id == id } }
                ?: settings.defaultDestination
            var addedCount = 0
            var failedCount = 0

            withContext(Dispatchers.IO) {
                for (uri in uris) {
                    runCatching {
                        val originalName = FileHelper.queryDisplayName(context, uri)
                        val sanitizedName = FileHelper.sanitizeFileName(originalName)
                        val mimeType = FileHelper.getMimeType(context, uri)
                        val targetFolder = FolderResolver.resolveTargetFolder(
                            sanitizedName,
                            mimeType,
                            destination.folderPath,
                            settings.rulesEnabled
                        )
                        val fileSize = FileHelper.queryFileSize(context, uri)
                        val cacheFile = FileHelper.copyUriToCache(context, uri, sanitizedName)
                        val finalSize = if (fileSize > 0) fileSize else cacheFile.length()
                        val now = System.currentTimeMillis()
                        val job = UploadJobEntity(
                            id = UUID.randomUUID().toString(),
                            localCachePath = cacheFile.absolutePath,
                            originalFileName = originalName,
                            sanitizedFileName = sanitizedName,
                            mimeType = mimeType,
                            fileSize = finalSize,
                            targetFolder = targetFolder,
                            destinationId = destination.id,
                            destinationName = destination.displayName,
                            driveAccountId = destination.driveAccountId,
                            driveAccountLabel = destination.driveAccountLabel,
                            status = UploadStatus.QUEUED,
                            progress = 0,
                            uploadedBytes = 0L,
                            totalBytes = finalSize,
                            errorMessage = textContext.getString(R.string.queued_for_upload),
                            uploadedFileName = null,
                            createdAt = now,
                            updatedAt = now,
                            completedAt = null,
                            retryCount = 0
                        )
                        repository.insertJob(job)
                    }.onSuccess {
                        addedCount++
                    }.onFailure { error ->
                        val originalName = runCatching { FileHelper.queryDisplayName(context, uri) }
                            .getOrElse { uri.lastPathSegment ?: "unreadable_shared_file" }
                        val sanitizedName = FileHelper.sanitizeFileName(originalName)
                        val mimeType = runCatching { FileHelper.getMimeType(context, uri) }
                            .getOrDefault("application/octet-stream")
                        val targetFolder = FolderResolver.resolveTargetFolder(
                            sanitizedName,
                            mimeType,
                            destination.folderPath,
                            settings.rulesEnabled
                        )
                        val now = System.currentTimeMillis()
                        val failedJob = UploadJobEntity(
                            id = UUID.randomUUID().toString(),
                            localCachePath = "",
                            originalFileName = originalName,
                            sanitizedFileName = sanitizedName,
                            mimeType = mimeType,
                            fileSize = 0L,
                            targetFolder = targetFolder,
                            destinationId = destination.id,
                            destinationName = destination.displayName,
                            driveAccountId = destination.driveAccountId,
                            driveAccountLabel = destination.driveAccountLabel,
                            status = UploadStatus.FAILED,
                            progress = 0,
                            uploadedBytes = 0L,
                            totalBytes = 0L,
                            errorMessage = error.message
                                ?: FileHelper.unreadableSourceMessage(uri),
                            uploadedFileName = null,
                            createdAt = now,
                            updatedAt = now,
                            completedAt = null,
                            retryCount = 0
                        )
                        repository.insertJob(failedJob)
                        failedCount++
                    }
                }
            }

            if (addedCount > 0) {
                triggerWorkManager()
            }
            onResult(AddFilesResult(addedCount, failedCount))
        }
    }

    fun retryJob(job: UploadJobEntity) {
        viewModelScope.launch {
            val updated = job.copy(
                status = UploadStatus.QUEUED,
                errorMessage = null,
                progress = 0,
                updatedAt = System.currentTimeMillis(),
                completedAt = null
            )
            repository.updateJob(updated)
            triggerWorkManager()
        }
    }

    fun deleteJob(job: UploadJobEntity) {
        viewModelScope.launch {
            repository.deleteJobById(job.id)
            try {
                val file = File(job.localCachePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun cancelJob(job: UploadJobEntity) {
        viewModelScope.launch {
            repository.markJobCanceled(job.id, "Upload canceled by user.", System.currentTimeMillis())
            WorkManager.getInstance(context).cancelUniqueWork("OneDriveUploader")
            if (job.status != UploadStatus.UPLOADING) {
                try {
                    val file = File(job.localCachePath)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    // Ignore cache cleanup failures.
                }
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }


    fun updateDefaultFolder(folder: String) {
        viewModelScope.launch {
            dataStoreManager.updateDefaultFolder(folder)
            clearShareShortcuts()
        }
    }

    fun updateConflictBehavior(behavior: ConflictBehavior) {
        viewModelScope.launch {
            dataStoreManager.updateConflictBehavior(behavior)
        }
    }

    fun updateWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch {
            dataStoreManager.updateWifiOnly(wifiOnly)
            // Trigger work manager in case network constraints shifted and we have pending items
            triggerWorkManager()
        }
    }

    fun updateRulesEnabled(rulesEnabled: Boolean) {
        viewModelScope.launch {
            dataStoreManager.updateRulesEnabled(rulesEnabled)
        }
    }

    fun updateLanguage(languageCode: String) {
        viewModelScope.launch {
            dataStoreManager.updateLanguageCode(languageCode)
        }
    }

    fun addDestination(displayName: String, folderPath: String) {
        viewModelScope.launch {
            val settings = dataStoreManager.appSettingsFlow.first()
            val nextOrder = (settings.uploadDestinations.maxOfOrNull { it.sortOrder } ?: -1) + 1
            val destination = UploadDestination(
                id = UUID.randomUUID().toString(),
                displayName = displayName.trim().ifBlank { UploadDestination.DEFAULT_NAME },
                folderPath = normalizeFolderPath(folderPath),
                driveAccountId = null,
                driveAccountLabel = UploadDestination.CURRENT_ACCOUNT_LABEL,
                isEnabled = true,
                sortOrder = nextOrder
            )
            saveDestinations(settings.uploadDestinations + destination)
        }
    }

    fun updateDestination(destination: UploadDestination) {
        viewModelScope.launch {
            val settings = dataStoreManager.appSettingsFlow.first()
            val updated = settings.uploadDestinations.map {
                if (it.id == destination.id) {
                    destination.copy(
                        displayName = destination.displayName.trim()
                            .ifBlank { UploadDestination.DEFAULT_NAME },
                        folderPath = normalizeFolderPath(destination.folderPath)
                    )
                } else {
                    it
                }
            }
            saveDestinations(updated)
        }
    }

    fun updateDestinationFolder(destinationId: String, folderPath: String) {
        viewModelScope.launch {
            val settings = dataStoreManager.appSettingsFlow.first()
            val updated = settings.uploadDestinations.map {
                if (it.id == destinationId) {
                    it.copy(folderPath = normalizeFolderPath(folderPath))
                } else {
                    it
                }
            }
            saveDestinations(updated)
        }
    }

    fun updateDestinationEnabled(destinationId: String, enabled: Boolean) {
        viewModelScope.launch {
            val settings = dataStoreManager.appSettingsFlow.first()
            val updated = settings.uploadDestinations.map {
                if (it.id == destinationId) it.copy(isEnabled = enabled) else it
            }
            saveDestinations(updated)
        }
    }

    fun deleteDestination(destinationId: String) {
        viewModelScope.launch {
            val settings = dataStoreManager.appSettingsFlow.first()
            val remaining = settings.uploadDestinations.filterNot { it.id == destinationId }
            saveDestinations(remaining.ifEmpty { listOf(UploadDestination.default(settings.defaultFolder)) })
        }
    }

    suspend fun enabledDestinationsOnce(): List<UploadDestination> {
        return dataStoreManager.appSettingsFlow.first().enabledDestinations
    }

    fun clearShareShortcuts() {
        DestinationShortcutPublisher.clear(context)
    }

    fun openFolderPicker() {
        _folderPickerState.value = FolderPickerUiState(isOpen = true, isLoading = true)
        loadFolderChildren(itemId = null, path = "")
    }

    fun closeFolderPicker() {
        _folderPickerState.value = FolderPickerUiState()
    }

    fun loadFolderChildren(itemId: String?, path: String) {
        viewModelScope.launch {
            _folderPickerState.value = _folderPickerState.value.copy(
                isOpen = true,
                isLoading = true,
                currentItemId = itemId,
                currentPath = path,
                errorMessage = null
            )

            val result = withContext(Dispatchers.IO) {
                runCatching { fetchOneDriveFolders(itemId, path) }
            }

            _folderPickerState.value = result.fold(
                onSuccess = { folders ->
                    _folderPickerState.value.copy(isLoading = false, folders = folders)
                },
                onFailure = { error ->
                    _folderPickerState.value.copy(
                        isLoading = false,
                        folders = emptyList(),
                        errorMessage = error.message
                            ?: localizedTextContext().getString(R.string.error_load_onedrive_folders)
                    )
                }
            )
        }
    }

    fun createFolderInPicker(folderName: String) {
        val trimmedName = folderName.trim()
        if (trimmedName.isBlank() || trimmedName.any { it in "\\/:*?\"<>|" }) {
            _folderPickerState.value = _folderPickerState.value.copy(
                errorMessage = localizedTextContext().getString(R.string.error_folder_name_invalid)
            )
            return
        }

        viewModelScope.launch {
            val currentState = _folderPickerState.value
            _folderPickerState.value = currentState.copy(isLoading = true, errorMessage = null)

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    createOneDriveFolder(
                        parentItemId = currentState.currentItemId,
                        parentPath = currentState.currentPath,
                        folderName = trimmedName
                    )
                }
            }

            result.fold(
                onSuccess = { folder ->
                    loadFolderChildren(folder.id, folder.path)
                },
                onFailure = { error ->
                    _folderPickerState.value = _folderPickerState.value.copy(
                        isLoading = false,
                        errorMessage = error.message
                            ?: localizedTextContext().getString(R.string.error_create_onedrive_folder)
                    )
                }
            )
        }
    }

    fun selectFolderFromPicker(path: String) {
        val normalized = normalizeFolderPath(path)
        updateDefaultFolder(normalized)
        closeFolderPicker()
    }

    private suspend fun fetchOneDriveFolders(itemId: String?, parentPath: String): List<OneDriveFolder> {
        val token = msalAuthManager.getAccessTokenSilently()
            ?: throw IllegalStateException(
                localizedTextContext().getString(R.string.error_signin_browse_folders)
            )

        val endpoint = if (itemId == null) {
            "https://graph.microsoft.com/v1.0/me/drive/root/children"
        } else {
            "https://graph.microsoft.com/v1.0/me/drive/items/${urlEncode(itemId)}/children"
        }
        val url = "$endpoint?\$select=id,name,folder&\$orderby=name"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException(parseGraphError(body, response.code))
            }

            val values = JSONObject(body).optJSONArray("value") ?: return emptyList()
            return buildList {
                for (i in 0 until values.length()) {
                    val item = values.getJSONObject(i)
                    if (!item.has("folder")) continue
                    val name = item.optString("name")
                    val id = item.optString("id")
                    if (name.isBlank() || id.isBlank()) continue
                    val childPath = joinFolderPath(parentPath, name)
                    add(OneDriveFolder(id = id, name = name, path = childPath))
                }
            }
        }
    }

    private suspend fun createOneDriveFolder(
        parentItemId: String?,
        parentPath: String,
        folderName: String
    ): OneDriveFolder {
        val token = msalAuthManager.getAccessTokenSilently()
            ?: throw IllegalStateException(
                localizedTextContext().getString(R.string.error_signin_browse_folders)
            )

        val endpoint = if (parentItemId == null) {
            "https://graph.microsoft.com/v1.0/me/drive/root/children"
        } else {
            "https://graph.microsoft.com/v1.0/me/drive/items/${urlEncode(parentItemId)}/children"
        }
        val bodyJson = JSONObject().apply {
            put("name", folderName)
            put("folder", JSONObject())
            put("@microsoft.graph.conflictBehavior", "fail")
        }
        val request = Request.Builder()
            .url(endpoint)
            .post(bodyJson.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .addHeader("Authorization", "Bearer $token")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException(parseGraphError(body, response.code))
            }
            val item = JSONObject(body)
            val id = item.optString("id")
            val name = item.optString("name", folderName)
            if (id.isBlank()) {
                throw IllegalStateException(
                    localizedTextContext().getString(R.string.error_create_onedrive_folder)
                )
            }
            return OneDriveFolder(id = id, name = name, path = joinFolderPath(parentPath, name))
        }
    }

    private fun parseGraphError(body: String, code: Int): String {
        return try {
            val error = JSONObject(body).getJSONObject("error")
            error.optString("message", "Microsoft Graph error HTTP $code")
        } catch (e: Exception) {
            "Microsoft Graph error HTTP $code"
        }
    }

    private fun normalizeFolderPath(path: String): String {
        val trimmed = path.trim().trim('/')
        return if (trimmed.isBlank()) "/" else "/$trimmed"
    }

    private suspend fun saveDestinations(destinations: List<UploadDestination>) {
        val normalized = destinations
            .sortedBy { it.sortOrder }
            .mapIndexed { index, destination -> destination.copy(sortOrder = index) }
        dataStoreManager.updateUploadDestinations(normalized)
        DestinationShortcutPublisher.clear(context)
    }

    private fun localizedTextContext() =
        LocaleHelper.localizedContext(context, appSettings.value.languageCode)

    private fun joinFolderPath(parentPath: String, childName: String): String {
        val cleanParent = parentPath.trim().trim('/')
        return if (cleanParent.isBlank()) "/$childName" else "/$cleanParent/$childName"
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    fun signIn(activity: Activity, onResult: (Result<Unit>) -> Unit) {
        msalAuthManager.signIn(activity) { result ->
            if (result.isSuccess) {
                onResult(Result.success(Unit))
                // Resume uploads if signed in
                triggerWorkManager()
            } else {
                onResult(Result.failure(result.exceptionOrNull() ?: Exception("Sign-in failed")))
            }
        }
    }

    fun signOut(onResult: (Result<Unit>) -> Unit) {
        msalAuthManager.signOut { result ->
            if (result.isSuccess) {
                onResult(Result.success(Unit))
            } else {
                onResult(Result.failure(result.exceptionOrNull() ?: Exception("Sign-out failed")))
            }
        }
    }
}
