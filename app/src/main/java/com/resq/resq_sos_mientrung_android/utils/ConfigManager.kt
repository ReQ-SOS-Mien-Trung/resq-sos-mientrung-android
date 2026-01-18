package com.resq.resq_sos_mientrung_android.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Configuration Manager for storing app settings
 */
object ConfigManager {
    private const val PREFS_NAME = "resq_sos_prefs"
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    
    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Save Gemini API key
     */
    fun saveGeminiApiKey(context: Context, apiKey: String) {
        getSharedPreferences(context).edit()
            .putString(KEY_GEMINI_API_KEY, apiKey)
            .apply()
    }
    
    /**
     * Get Gemini API key
     */
    fun getGeminiApiKey(context: Context): String? {
        return getSharedPreferences(context).getString(KEY_GEMINI_API_KEY, null)
    }
    
    /**
     * Check if Gemini API key is set
     */
    fun hasGeminiApiKey(context: Context): Boolean {
        return getGeminiApiKey(context)?.isNotEmpty() == true
    }
    
    /**
     * Clear Gemini API key
     */
    fun clearGeminiApiKey(context: Context) {
        getSharedPreferences(context).edit()
            .remove(KEY_GEMINI_API_KEY)
            .apply()
    }
}
