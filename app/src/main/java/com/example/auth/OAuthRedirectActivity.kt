package com.example.auth

import android.app.Activity
import android.os.Bundle

class OAuthRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MsalAuthManager.getInstance(applicationContext).handleAuthRedirect(intent?.data)
        finish()
    }
}
