package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.R
import com.microsoft.identity.client.*
import com.microsoft.identity.client.exception.MsalException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class MsalAuthManager(private val context: Context) {

    companion object {
        private const val TAG = "MsalAuthManager"
        private val SCOPES = arrayOf("User.Read", "Files.ReadWrite")

        @Volatile
        private var INSTANCE: MsalAuthManager? = null

        fun getInstance(context: Context): MsalAuthManager {
            return INSTANCE ?: synchronized(this) {
                val instance = MsalAuthManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private var mSingleAccountApp: ISingleAccountPublicClientApplication? = null
    private val _authStateFlow = MutableStateFlow<AuthState>(AuthState.Uninitialized)
    val authStateFlow: StateFlow<AuthState> = _authStateFlow.asStateFlow()

    private val initDeferred = CompletableDeferred<ISingleAccountPublicClientApplication>()

    init {
        initMsal()
    }

    private fun initMsal() {
        PublicClientApplication.createSingleAccountPublicClientApplication(
            context,
            R.raw.auth_config_single_account,
            object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                override fun onCreated(application: ISingleAccountPublicClientApplication) {
                    mSingleAccountApp = application
                    initDeferred.complete(application)
                    checkCurrentAccount()
                }

                override fun onError(exception: MsalException) {
                    Log.e(TAG, "Error creating MSAL application", exception)
                    _authStateFlow.value = AuthState.Error(exception)
                    initDeferred.completeExceptionally(exception)
                }
            }
        )
    }

    private fun checkCurrentAccount() {
        val app = mSingleAccountApp ?: return
        app.getCurrentAccountAsync(object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
            override fun onAccountLoaded(activeAccount: IAccount?) {
                if (activeAccount != null) {
                    _authStateFlow.value = AuthState.SignedIn(activeAccount)
                } else {
                    _authStateFlow.value = AuthState.SignedOut
                }
            }

            override fun onAccountChanged(priorAccount: IAccount?, currentAccount: IAccount?) {
                if (currentAccount != null) {
                    _authStateFlow.value = AuthState.SignedIn(currentAccount)
                } else {
                    _authStateFlow.value = AuthState.SignedOut
                }
            }

            override fun onError(exception: MsalException) {
                Log.e(TAG, "Error loading current account", exception)
                _authStateFlow.value = AuthState.Error(exception)
            }
        })
    }

    suspend fun getAccessTokenSilently(): String? = withContext(Dispatchers.IO) {
        try {
            val app = initDeferred.await()
            suspendCancellableCoroutine { continuation ->
                app.getCurrentAccountAsync(object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
                    override fun onAccountLoaded(activeAccount: IAccount?) {
                        if (activeAccount == null) {
                            continuation.resume(null)
                            return
                        }
                        app.acquireTokenSilentAsync(
                            SCOPES,
                            activeAccount.authority,
                            object : SilentAuthenticationCallback {
                                override fun onSuccess(authenticationResult: IAuthenticationResult) {
                                    continuation.resume(authenticationResult.accessToken)
                                }

                                override fun onError(exception: MsalException) {
                                    Log.e(TAG, "acquireTokenSilentAsync error", exception)
                                    continuation.resume(null)
                                }
                            }
                        )
                    }

                    override fun onAccountChanged(priorAccount: IAccount?, currentAccount: IAccount?) {
                        // Current account changed callback
                    }

                    override fun onError(exception: MsalException) {
                        Log.e(TAG, "getCurrentAccountAsync error", exception)
                        continuation.resume(null)
                    }
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during silent token acquisition", e)
            null
        }
    }

    fun signIn(activity: Activity, onResult: (Result<IAccount>) -> Unit) {
        val app = mSingleAccountApp
        if (app == null) {
            onResult(Result.failure(Exception("MSAL not initialized yet")))
            return
        }

        app.signIn(activity, null, SCOPES, object : AuthenticationCallback {
            override fun onSuccess(authenticationResult: IAuthenticationResult) {
                _authStateFlow.value = AuthState.SignedIn(authenticationResult.account)
                onResult(Result.success(authenticationResult.account))
            }

            override fun onError(exception: MsalException) {
                Log.e(TAG, "Sign in error", exception)
                _authStateFlow.value = AuthState.Error(exception)
                onResult(Result.failure(exception))
            }

            override fun onCancel() {
                Log.d(TAG, "Sign in cancelled")
                _authStateFlow.value = AuthState.SignedOut
                onResult(Result.failure(Exception("Sign in cancelled by user")))
            }
        })
    }

    fun signOut(onResult: (Result<Boolean>) -> Unit) {
        val app = mSingleAccountApp
        if (app == null) {
            onResult(Result.failure(Exception("MSAL not initialized yet")))
            return
        }

        app.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
            override fun onSignOut() {
                _authStateFlow.value = AuthState.SignedOut
                onResult(Result.success(true))
            }

            override fun onError(exception: MsalException) {
                Log.e(TAG, "Sign out error", exception)
                onResult(Result.failure(exception))
            }
        })
    }
}

sealed interface AuthState {
    object Uninitialized : AuthState
    object SignedOut : AuthState
    data class SignedIn(val account: IAccount) : AuthState
    data class Error(val exception: MsalException) : AuthState
}
