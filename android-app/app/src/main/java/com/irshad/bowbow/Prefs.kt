package com.irshad.bowbow

import android.content.Context

/** App-wide constants for BOW BOW. No secrets live in the app. */
object BowBowConfig {
    /** mohamed's Supabase user UUID — the backend uses this as user_id. */
    const val USER_ID = "5897b826-feff-4dd2-9841-f8d4b510f6bf"

    /** Hosted backend on Vercel — the live BOW BOW backend.
     *  Local testing can override this in Settings. */
    const val DEFAULT_BACKEND_URL = "https://bow-bow-backend.vercel.app"
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
