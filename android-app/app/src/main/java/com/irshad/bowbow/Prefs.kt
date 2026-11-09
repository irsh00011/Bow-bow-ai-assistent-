package com.irshad.bowbow

import android.content.Context

/** App-wide constants for BOW BOW. No secrets live in the app. */
object BowBowConfig {
    /** mohamed's Supabase user UUID — the backend uses this as user_id. */
    const val USER_ID = "5897b826-feff-4dd2-9841-f8d4b510f6bf"

    /** Emulator loopback to the host machine — local testing only.
     *  On a real phone, set the hosted backend URL in Settings. */
    const val DEFAULT_BACKEND_URL = "http://10.0.2.2:8000"
}

/** Tiny SharedPreferences helper — only the backend URL is stored. */
object Prefs {
    private const val PREFS_NAME = "bowbow"
    private const val KEY_BACKEND_URL = "backend_url"

    fun getBackendUrl(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BACKEND_URL, BowBowConfig.DEFAULT_BACKEND_URL)
            ?: BowBowConfig.DEFAULT_BACKEND_URL

    fun setBackendUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BACKEND_URL, url)
            .apply()
    }
}
