package com.example.util

import android.content.Context
import androidx.core.content.pm.ShortcutManagerCompat

object DestinationShortcutPublisher {
    private const val SHORTCUT_PREFIX = "upload_destination_"

    fun clear(context: Context) {
        ShortcutManagerCompat.removeAllDynamicShortcuts(context)
    }

    fun destinationIdFromShortcutId(shortcutId: String?): String? {
        return shortcutId
            ?.takeIf { it.startsWith(SHORTCUT_PREFIX) }
            ?.removePrefix(SHORTCUT_PREFIX)
            ?.takeIf { it.isNotBlank() }
    }
}
