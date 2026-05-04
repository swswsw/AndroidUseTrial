package org.goldenpass.androiduse

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecurityManager(context: Context) {
    private val tag = "SecurityManager"
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getGeminiApiKey(): String? {
        val prefKey = sharedPreferences.getString("gemini_api_key", null)
        return if (!prefKey.isNullOrEmpty()) {
            Log.i(tag, "Retrieved Gemini API Key from EncryptedSharedPreferences")
            prefKey
        } else {
            val buildKey = BuildConfig.GEMINI_API_KEY
            if (buildKey.isNotEmpty()) {
                Log.i(tag, "Retrieved Gemini API Key from BuildConfig fallback")
                buildKey
            } else {
                Log.w(tag, "Gemini API Key is missing everywhere!")
                null
            }
        }
    }

    fun setGeminiApiKey(key: String) {
        Log.i(tag, "Saving Gemini API Key to EncryptedSharedPreferences")
        sharedPreferences.edit().putString("gemini_api_key", key).apply()
    }

    fun getOpenAIApiKey(): String? {
        val prefKey = sharedPreferences.getString("openai_api_key", null)
        return if (!prefKey.isNullOrEmpty()) {
            Log.i(tag, "Retrieved OpenAI API Key from EncryptedSharedPreferences")
            prefKey
        } else {
            val buildKey = BuildConfig.OPENAI_API_KEY
            if (buildKey.isNotEmpty()) {
                Log.i(tag, "Retrieved OpenAI API Key from BuildConfig fallback")
                buildKey
            } else {
                Log.w(tag, "OpenAI API Key is missing everywhere!")
                null
            }
        }
    }

    fun setOpenAIApiKey(key: String) {
        Log.i(tag, "Saving OpenAI API Key to EncryptedSharedPreferences")
        sharedPreferences.edit().putString("openai_api_key", key).apply()
    }

    fun getAnthropicApiKey(): String? {
        val prefKey = sharedPreferences.getString("anthropic_api_key", null)
        return if (!prefKey.isNullOrEmpty()) {
            Log.i(tag, "Retrieved Anthropic API Key from EncryptedSharedPreferences")
            prefKey
        } else {
            val buildKey = BuildConfig.ANTHROPIC_API_KEY
            if (buildKey.isNotEmpty()) {
                Log.i(tag, "Retrieved Anthropic API Key from BuildConfig fallback")
                buildKey
            } else {
                Log.w(tag, "Anthropic API Key is missing everywhere!")
                null
            }
        }
    }

    fun setAnthropicApiKey(key: String) {
        Log.i(tag, "Saving Anthropic API Key to EncryptedSharedPreferences")
        sharedPreferences.edit().putString("anthropic_api_key", key).apply()
    }
}
