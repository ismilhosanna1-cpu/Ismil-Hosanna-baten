package com.example.yesmin.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts sensitive configuration (e.g., user-entered Gemini API keys) using Android Keystore
 * AES-256-GCM hardware-backed keys where available. Never persists plaintext keys to disk.
 */
class SecureVault(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var inMemoryFallbackKey: String = ""

    fun saveGeminiApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            clearGeminiApiKey()
            return
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))

            val payload = ByteBuffer.allocate(4 + iv.size + encrypted.size)
                .putInt(iv.size)
                .put(iv)
                .put(encrypted)
                .array()

            val encoded = Base64.encodeToString(payload, Base64.NO_WRAP)
            prefs.edit().putString(KEY_GEMINI_API_ENC, encoded).apply()
            inMemoryFallbackKey = ""
        } catch (e: Exception) {
            Log.w(TAG, "Keystore unavailable in current environment; keeping key in memory only", e)
            inMemoryFallbackKey = trimmed
        }
    }

    fun getGeminiApiKey(): String {
        val encoded = prefs.getString(KEY_GEMINI_API_ENC, null)
        if (!encoded.isNullOrBlank()) {
            try {
                val payload = Base64.decode(encoded, Base64.NO_WRAP)
                val buffer = ByteBuffer.wrap(payload)
                val ivLength = buffer.int
                if (ivLength in 12..16) {
                    val iv = ByteArray(ivLength)
                    buffer.get(iv)
                    val encrypted = ByteArray(buffer.remaining())
                    buffer.get(encrypted)

                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
                    return String(cipher.doFinal(encrypted), Charsets.UTF_8)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decrypt stored Gemini API key", e)
            }
        }
        return inMemoryFallbackKey
    }

    fun hasApiKey(): Boolean = getGeminiApiKey().isNotBlank()

    fun getMaskedApiKey(): String {
        val key = getGeminiApiKey()
        if (key.isBlank()) return "Not configured"
        if (key.length <= 10) return "••••••••"
        return "${key.take(6)}••••••••${key.takeLast(4)}"
    }

    fun clearGeminiApiKey() {
        inMemoryFallbackKey = ""
        prefs.edit().remove(KEY_GEMINI_API_ENC).apply()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val TAG = "SecureVault"
        private const val PREFS_NAME = "yesmin_secure_vault"
        private const val KEY_GEMINI_API_ENC = "gemini_api_key_enc_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "yesmin_ai_master_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
