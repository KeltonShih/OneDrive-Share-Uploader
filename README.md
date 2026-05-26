# OneDrive Share Uploader

An Android application that integrates directly into the **Android System Share Menu**. It allows users to "Share" any file from standard photo galleries, document apps, or file browsers, copying them automatically to a pre-configured, personalized target OneDrive folder. 

This app is designed as a focused **"One-Click OneDrive Drop-Box"** rather than a full sync client or active file viewer.

---

## Technical Stack & Architecture

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material Design 3)
- **Database Persistence**: Room Database (Upload Job queue, history logs, and states)
- **Settings Store**: Jetpack Preferences DataStore (Default folder paths, conflict resolution tactics, and Wi-Fi flags)
- **Background Engine**: WorkManager (with `CoroutineWorker` support executing foreground notification services for long-running chunked file streams)
- **HTTP Transport**: OkHttp 4 for reliable Graph API networking
- **Identity Authentication**: Microsoft Authentication Library (MSAL) for Android

---

## Crucial Microsoft App Registration & Setup

To authenticate against Microsoft Graph APIs, you must register the application in the **Azure Microsoft Entra ID portal**:

### 1. Register Application in Azure Portal
1. Navigate to the [Microsoft Entra Admin Center](https://entra.microsoft.com/) (formerly Azure Active Directory portal).
2. Go to **Identity** (in side-bar) > **Applications** > **App registrations** and click **New registration**.
3. Choose a name (e.g., `OneDrive Share Uploader`).
4. Select the Supported Account Types (e.g., *Accounts in any organizational directory and personal Microsoft accounts*).
5. Click **Register**.

### 2. Configure Authenticated Platforms
1. Inside your newly registered App panel, choose **Authentication** under the Manage section.
2. Click **Add a platform** and choose **Android**.
3. Fill in the package credentials:
   - **Package Name**: `com.keltonshih.onedriveshareuploader`
   - **Signature Hash**: Run the keytool command below on your developer environment keystore to generate the Base64 SHA-1 signature hash.
4. Microsoft will generate a **Redirect URI** formatted like:
   `msauth://com.keltonshih.onedriveshareuploader/<YOUR_SIGNATURE_HASH_URL_ENCODED>`
5. Copy this redirect URI.

### 3. Generate your Signature Hash Key
To map your local debug or production signature key to Microsoft's Authenticator:
```bash
# Debug Keystore Hash retrieval
keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore | openssl sha1 -binary | openssl base64
```

### 4. Code Integration Map

Paste your generated App Client ID and Signature Hash into these files in the source tree:

1. **/app/src/main/res/raw/auth_config_single_account.json**:
   ```json
   {
     "client_id" : "YOUR_AZURE_CLIENT_ID_HERE",
     "redirect_uri" : "msauth://com.keltonshih.onedriveshareuploader/YOUR_SIGNATURE_HASH_URL_ENCODED",
     "authorities" : [
       {
         "type": "AAD",
         "audience": {
           "type": "AllAccounts"
         },
         "default": true
       }
     ]
   }
   ```
2. **/app/src/main/AndroidManifest.xml**:
   Ensure the MSAL Redirection handler block contains your correct URL-decoded signature hash:
   ```xml
   <activity
       android:name="com.microsoft.identity.client.BrowserTabActivity"
       android:exported="true">
       <intent-filter>
         <action android:name="android.intent.action.VIEW" />
         <category android:name="android.intent.category.DEFAULT" />
         <category android:name="android.intent.category.BROWSABLE" />
         <data
             android:scheme="msauth"
             android:host="com.keltonshih.onedriveshareuploader"
             android:path="/YOUR_SIGNATURE_HASH" /> <!-- e.g., /yNUr6X6oMhK+7jPruxM+2wV6p8A= -->
       </intent-filter>
   </activity>
   ```

---

## App Features & Capabilities

- **Automatic Shared copying**: Standard `content://` shared URIs can sometimes lose permission tokens when passed directly into delayed background services. This application handles this gracefully by replicating and querying the file attributes into a private cache folder (`/cache/`) immediately in the background upon receipt.
- **Support for Multi-Files**: Accepts multiple items simultaneously, queues them inside local Room indexes, and uploads sequentially.
- **Intelligent Chunked Uploads**: Automatically switches between standard `PUT` requests (for small files $\le$ 250MB) and multi-part upload sessions utilizing chunk buffers (for files $>$ 250MB).
- **Persistent Network Constraints**: Enforces "Wi-Fi Only" flags dynamically by setting `NetworkType.UNMETERED` on WorkManager constraints.
- **Flexible Path Customizations**: Configurable default target folder with implicit full subfolder autovalidation, support for special characters or Chinese directories (*e.g., `/手機快速上傳`*), and smart routing rule fallbacks.
- **Interactive Conflict resolution Options**: Choice between:
  - **RENAME** (Default): Appends timestamp counters to prevent overwriting.
  - **REPLACE**: Overwrites the existing file directly.
  - **FAIL**: Flags the queue task as an explicit error, allowing the user to examine and manually retry.
- **Log Management**: Completed history tasks are logged to Room with clear error strings if failures occur, and can be cleared with a single button click inside Settings.
