package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class OneDriveAccount(
    val id: String,
    val label: String,
    val authority: String,
    val username: String = label
)

class MsalAuthManager private constructor(private val context: Context) {
    companion object {
        private const val TAG = "OAuthAuthManager"
        private const val CLIENT_ID = "cb45b6ab-7f03-4011-8143-0a7cbed0325e"
        private const val AUTH_ENDPOINT = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
        private const val TOKEN_ENDPOINT = "https://login.microsoftonline.com/common/oauth2/v2.0/token"
        private const val GRAPH_ME_ENDPOINT = "https://graph.microsoft.com/v1.0/me?\$select=id,displayName,userPrincipalName,mail"
        private const val AUTHORITY = "https://login.microsoftonline.com/common"
        private const val SCOPES = "openid profile offline_access User.Read Files.ReadWrite"
        private const val PREFS_NAME = "oauth_pkce_accounts"
        private const val ACCOUNTS_KEY = "encrypted_accounts"
        private const val PENDING_STATE_KEY = "pending_state"
        private const val PENDING_VERIFIER_KEY = "pending_verifier"
        private const val REDIRECT_RELEASE = "msauth://com.keltonshih.onedriveshareuploader/8mqhslKBrD0XppDpZsZCTEOan%2B8%3D"
        private const val REDIRECT_DEBUG = "msauth://com.keltonshih.onedriveshareuploader/yDRtUMXdQVe3Lt8eXOC8TRTFPH0%3D"
        private const val TOKEN_REFRESH_BUFFER_MS = 120_000L

        @Volatile
        private var INSTANCE: MsalAuthManager? = null

        fun getInstance(context: Context): MsalAuthManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MsalAuthManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val tokenCipher = TokenCipher()
    private val secureRandom = SecureRandom()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val okHttpClient = OkHttpClient()
    private val redirectUri: String = if (BuildConfig.DEBUG) REDIRECT_DEBUG else REDIRECT_RELEASE

    private val _authStateFlow = MutableStateFlow<AuthState>(AuthState.Uninitialized)
    val authStateFlow: StateFlow<AuthState> = _authStateFlow.asStateFlow()

    @Volatile
    private var pendingCallback: ((Result<OneDriveAccount>) -> Unit)? = null

    init {
        refreshAccounts()
    }

    fun retryInitialization() {
        refreshAccounts()
    }

    fun refreshAccounts() {
        val accounts = loadStoredAccounts().map { it.toOneDriveAccount() }
        _authStateFlow.value = if (accounts.isEmpty()) {
            AuthState.SignedOut
        } else {
            AuthState.AccountsLoaded(accounts)
        }
    }

    suspend fun loadAccounts(): List<OneDriveAccount> = withContext(Dispatchers.IO) {
        loadStoredAccounts().map { it.toOneDriveAccount() }.also { accounts ->
            withContext(Dispatchers.Main) {
                _authStateFlow.value = if (accounts.isEmpty()) AuthState.SignedOut else AuthState.AccountsLoaded(accounts)
            }
        }
    }

    fun signIn(activity: Activity, onResult: (Result<OneDriveAccount>) -> Unit) {
        val state = randomUrlSafe(32)
        val verifier = randomUrlSafe(64)
        val challenge = pkceChallenge(verifier)
        prefs.edit()
            .putString(PENDING_STATE_KEY, state)
            .putString(PENDING_VERIFIER_KEY, verifier)
            .apply()
        pendingCallback = onResult

        val authUri = Uri.parse(AUTH_ENDPOINT).buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("response_mode", "query")
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("state", state)
            .appendQueryParameter("prompt", "select_account")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        runCatching {
            activity.startActivity(Intent(Intent.ACTION_VIEW, authUri).addCategory(Intent.CATEGORY_BROWSABLE))
        }.onFailure { error ->
            failAuth(Exception(error.message ?: "Could not open Microsoft sign-in.", error))
        }
    }

    fun handleAuthRedirect(uri: Uri?) {
        if (uri == null) {
            failAuth(Exception("Microsoft sign-in returned without a redirect URI."))
            return
        }

        val returnedState = uri.getQueryParameter("state")
        val expectedState = prefs.getString(PENDING_STATE_KEY, null)
        val verifier = prefs.getString(PENDING_VERIFIER_KEY, null)
        val error = uri.getQueryParameter("error")
        val errorDescription = uri.getQueryParameter("error_description")
        val code = uri.getQueryParameter("code")

        if (!error.isNullOrBlank()) {
            clearPendingAuth()
            failAuth(Exception(errorDescription ?: error))
            return
        }

        if (returnedState.isNullOrBlank() || returnedState != expectedState || verifier.isNullOrBlank()) {
            clearPendingAuth()
            failAuth(Exception("Microsoft sign-in response could not be verified."))
            return
        }

        if (code.isNullOrBlank()) {
            clearPendingAuth()
            failAuth(Exception("Microsoft sign-in did not return an authorization code."))
            return
        }

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val token = exchangeAuthorizationCode(code, verifier)
                    val profile = fetchProfile(token.accessToken)
                    val account = StoredOAuthAccount(
                        id = profile.id,
                        label = profile.label,
                        username = profile.label,
                        authority = AUTHORITY,
                        accessToken = token.accessToken,
                        refreshToken = token.refreshToken,
                        expiresAt = System.currentTimeMillis() + token.expiresInSeconds * 1000L
                    )
                    upsertStoredAccount(account)
                    account.toOneDriveAccount()
                }
            }
            clearPendingAuth()
            result.fold(
                onSuccess = { account ->
                    refreshAccounts()
                    pendingCallback?.invoke(Result.success(account))
                    pendingCallback = null
                },
                onFailure = { error ->
                    failAuth(Exception(error.message ?: "Microsoft sign-in failed.", error))
                }
            )
        }
    }

    suspend fun getAccessTokenSilently(accountId: String?): String? = withContext(Dispatchers.IO) {
        if (accountId.isNullOrBlank()) return@withContext null
        val account = loadStoredAccounts().firstOrNull { it.id == accountId } ?: return@withContext null
        val now = System.currentTimeMillis()
        if (account.accessToken.isNotBlank() && account.expiresAt - TOKEN_REFRESH_BUFFER_MS > now) {
            return@withContext account.accessToken
        }

        runCatching {
            val token = refreshToken(account.refreshToken)
            val refreshedAccount = account.copy(
                accessToken = token.accessToken,
                refreshToken = token.refreshToken.ifBlank { account.refreshToken },
                expiresAt = System.currentTimeMillis() + token.expiresInSeconds * 1000L
            )
            upsertStoredAccount(refreshedAccount)
            refreshedAccount.accessToken
        }.onFailure { error ->
            Log.e(TAG, "Token refresh failed for ${account.label}", error)
        }.getOrNull()
    }

    fun removeAccount(accountId: String, onResult: (Result<OneDriveAccount>) -> Unit) {
        val accounts = loadStoredAccounts()
        val removed = accounts.firstOrNull { it.id == accountId }
        if (removed == null) {
            onResult(Result.failure(Exception("Account not found.")))
            refreshAccounts()
            return
        }
        saveStoredAccounts(accounts.filterNot { it.id == accountId })
        refreshAccounts()
        onResult(Result.success(removed.toOneDriveAccount()))
    }

    fun renameAccount(accountId: String, alias: String, onResult: (Result<OneDriveAccount>) -> Unit) {
        val accounts = loadStoredAccounts()
        val account = accounts.firstOrNull { it.id == accountId }
        if (account == null) {
            onResult(Result.failure(Exception("Account not found.")))
            refreshAccounts()
            return
        }

        val normalizedAlias = alias.trim()
        val updatedAccount = account.copy(
            label = normalizedAlias.ifBlank { account.username }
        )
        saveStoredAccounts(accounts.map { if (it.id == accountId) updatedAccount else it })
        refreshAccounts()
        onResult(Result.success(updatedAccount.toOneDriveAccount()))
    }

    private fun failAuth(exception: Exception) {
        Log.e(TAG, "Authentication error", exception)
        _authStateFlow.value = AuthState.Error(exception)
        pendingCallback?.invoke(Result.failure(exception))
        pendingCallback = null
    }

    private fun clearPendingAuth() {
        prefs.edit()
            .remove(PENDING_STATE_KEY)
            .remove(PENDING_VERIFIER_KEY)
            .apply()
    }

    private fun exchangeAuthorizationCode(code: String, verifier: String): OAuthTokenResponse {
        val body = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("scope", SCOPES)
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .add("grant_type", "authorization_code")
            .add("code_verifier", verifier)
            .build()
        return executeTokenRequest(body)
    }

    private fun refreshToken(refreshToken: String): OAuthTokenResponse {
        val body = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("scope", SCOPES)
            .add("refresh_token", refreshToken)
            .add("redirect_uri", redirectUri)
            .add("grant_type", "refresh_token")
            .build()
        return executeTokenRequest(body)
    }

    private fun executeTokenRequest(body: FormBody): OAuthTokenResponse {
        val request = Request.Builder()
            .url(TOKEN_ENDPOINT)
            .post(body)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw Exception(parseOAuthError(responseBody, response.code))
            }
            val json = JSONObject(responseBody)
            val accessToken = json.optString("access_token")
            val refreshToken = json.optString("refresh_token")
            val expiresIn = json.optLong("expires_in", 3600L)
            if (accessToken.isBlank()) {
                throw Exception("Microsoft did not return an access token.")
            }
            return OAuthTokenResponse(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresInSeconds = expiresIn
            )
        }
    }

    private fun fetchProfile(accessToken: String): GraphProfile {
        val request = Request.Builder()
            .url(GRAPH_ME_ENDPOINT)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw Exception(parseOAuthError(body, response.code))
            }
            val json = JSONObject(body)
            val id = json.optString("id").takeIf { it.isNotBlank() }
                ?: throw Exception("Microsoft account profile did not include an id.")
            val email = json.optString("userPrincipalName").takeIf { it.isNotBlank() }
                ?: json.optString("mail").takeIf { it.isNotBlank() }
            val label = email
                ?: json.optString("displayName").takeIf { it.isNotBlank() }
                ?: id
            return GraphProfile(id = id, label = label)
        }
    }

    private fun parseOAuthError(body: String, statusCode: Int): String {
        return runCatching {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optString("message")
                ?: json.optString("error_description")
                ?: json.optString("error")
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Microsoft authentication error HTTP $statusCode"
    }

    private fun loadStoredAccounts(): List<StoredOAuthAccount> {
        val encrypted = prefs.getString(ACCOUNTS_KEY, null) ?: return emptyList()
        val json = tokenCipher.decrypt(encrypted) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val id = item.optString("id")
                    val label = item.optString("label")
                    val accessToken = item.optString("accessToken")
                    val refreshToken = item.optString("refreshToken")
                    if (id.isBlank() || label.isBlank() || refreshToken.isBlank()) continue
                    add(
                        StoredOAuthAccount(
                            id = id,
                            label = label,
                            username = item.optString("username").takeIf { it.isNotBlank() } ?: label,
                            authority = item.optString("authority", AUTHORITY),
                            accessToken = accessToken,
                            refreshToken = refreshToken,
                            expiresAt = item.optLong("expiresAt", 0L)
                        )
                    )
                }
            }
        }.getOrElse {
            Log.e(TAG, "Could not parse stored OAuth accounts", it)
            emptyList()
        }
    }

    private fun saveStoredAccounts(accounts: List<StoredOAuthAccount>) {
        val array = JSONArray()
        accounts.forEach { account ->
            array.put(JSONObject().apply {
                put("id", account.id)
                put("label", account.label)
                put("username", account.username)
                put("authority", account.authority)
                put("accessToken", account.accessToken)
                put("refreshToken", account.refreshToken)
                put("expiresAt", account.expiresAt)
            })
        }
        val encrypted = tokenCipher.encrypt(array.toString())
        prefs.edit().putString(ACCOUNTS_KEY, encrypted).apply()
    }

    private fun upsertStoredAccount(account: StoredOAuthAccount) {
        val existing = loadStoredAccounts()
        val updated = if (existing.any { it.id == account.id }) {
            existing.map { if (it.id == account.id) account else it }
        } else {
            existing + account
        }
        saveStoredAccounts(updated)
    }

    private fun randomUrlSafe(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun pkceChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun StoredOAuthAccount.toOneDriveAccount(): OneDriveAccount =
        OneDriveAccount(id = id, label = label, authority = authority, username = username)

    private data class StoredOAuthAccount(
        val id: String,
        val label: String,
        val username: String,
        val authority: String,
        val accessToken: String,
        val refreshToken: String,
        val expiresAt: Long
    )

    private data class OAuthTokenResponse(
        val accessToken: String,
        val refreshToken: String,
        val expiresInSeconds: Long
    )

    private data class GraphProfile(
        val id: String,
        val label: String
    )

    private class TokenCipher {
        private companion object {
            const val KEY_ALIAS = "onedrive_share_uploader_oauth_tokens_v1"
            const val ANDROID_KEYSTORE = "AndroidKeyStore"
            const val TRANSFORMATION = "AES/GCM/NoPadding"
            const val GCM_TAG_LENGTH = 128
        }

        private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        fun encrypt(plainText: String): String {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            return "${encode(cipher.iv)}:${encode(cipherText)}"
        }

        fun decrypt(encryptedText: String): String? {
            val parts = encryptedText.split(":")
            if (parts.size != 2) return null
            return runCatching {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateKey(),
                    GCMParameterSpec(GCM_TAG_LENGTH, decode(parts[0]))
                )
                String(cipher.doFinal(decode(parts[1])), Charsets.UTF_8)
            }.getOrNull()
        }

        private fun getOrCreateKey(): SecretKey {
            (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let {
                return it
            }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            generator.init(spec)
            return generator.generateKey()
        }

        private fun encode(bytes: ByteArray): String =
            Base64.encodeToString(bytes, Base64.NO_WRAP)

        private fun decode(value: String): ByteArray =
            Base64.decode(value, Base64.NO_WRAP)
    }
}

sealed interface AuthState {
    object Uninitialized : AuthState
    object SignedOut : AuthState
    data class AccountsLoaded(val accounts: List<OneDriveAccount>) : AuthState
    data class Error(val exception: Exception) : AuthState
}
