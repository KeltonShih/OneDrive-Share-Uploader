package com.example.ui

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.auth.AuthState
import com.example.auth.MsalAuthManager
import com.example.data.local.AppDatabase
import com.example.data.local.DataStoreManager
import com.example.data.model.AppSettings
import com.example.data.model.ConflictBehavior
import com.example.data.model.UploadJobEntity
import com.example.data.model.UploadStatus
import com.example.data.repository.UploadRepository
import com.example.worker.UploadWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val db = AppDatabase.getDatabase(context)
    private val repository = UploadRepository(db.uploadJobDao())
    private val dataStoreManager = DataStoreManager(context)
    private val msalAuthManager = MsalAuthManager.getInstance(context)

    val activeQueue: StateFlow<List<UploadJobEntity>> = repository.activeQueue
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
            .enqueueUniqueWork("OneDriveUploader", ExistingWorkPolicy.KEEP, uploadWorkRequest)
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

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun updateDefaultFolder(folder: String) {
        viewModelScope.launch {
            dataStoreManager.updateDefaultFolder(folder)
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
