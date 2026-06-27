package io.github.chsbuffer.revancedxposed // यदि प्रोजेक्ट में पैकेज नाम अलग है, तो उसे यहाँ बदलें

import android.content.Context
import android.util.Log

object DexKitCache {
    private const val TAG = "NexAlloyCache"
    private const val PREFS_NAME = "nexalloy_dexkit_cache"
    private const val APP_VERSION_KEY = "target_app_version"

    // कैशे से पहले से सेव की गई क्लास/मेथड का नाम निकालना
    fun getCachedTarget(context: Context, key: String, currentAppVersion: String): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedVersion = prefs.getString(APP_VERSION_KEY, null)
        
        // यदि यूट्यूब का वर्जन बदल गया है (यूट्यूब अपडेट हुआ है), तो पुराने कैशे को डिलीट करें
        if (cachedVersion != currentAppVersion) {
            Log.d(TAG, "YouTube version changed from $cachedVersion to $currentAppVersion. Clearing cache.")
            prefs.edit().clear().putString(APP_VERSION_KEY, currentAppVersion).apply()
            return null
        }
        
        return prefs.getString(key, null)
    }

    // पहली बार मिलने वाले स्कैन रिजल्ट को यूट्यूब के प्राइवेट स्टोरेज में सेव करना
    fun saveTarget(context: Context, key: String, value: String, currentAppVersion: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(APP_VERSION_KEY, currentAppVersion)
            .putString(key, value)
            .apply()
        Log.d(TAG, "Cached successfully: $key -> $value")
    }
}
