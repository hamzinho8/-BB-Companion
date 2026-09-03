package com.hamza.blackberrybridge.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log

object ClipboardManagerBridge {
    private const val TAG = "ClipboardManagerBridge"

    fun copyToAndroidClipboard(context: Context, text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Copied from BlackBerry", text)
            clipboard.setPrimaryClip(clip)
            Log.d(TAG, "Copied to clipboard: $text")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy to clipboard", e)
        }
    }
}
