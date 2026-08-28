package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Small Android-Keystore-backed store for credentials that must not be plaintext preferences. */
object SecureSecretStore {
    private const val TAG = "SecureSecretStore"
    private const val KEY_ALIAS = "dental_runtime_secrets_v1"
    private const val PREFS_NAME = "dental_secure_secrets"
    private const val PREFIX = "enc_v1_"
    private const val IV_BYTES = 12

    private var fallbackKey: SecretKey? = null

    @Synchronized
    fun put(context: Context, name: String, value: String) {
        if (!name.matches(Regex("[a-z0-9_]{1,64}"))) {
            Log.w(TAG, "Invalid secret name: $name")
            return
        }
        if (value.isEmpty()) {
            remove(context, name)
            return
        }
        try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key)
                updateAAD(name.toByteArray(Charsets.UTF_8))
            }
            val iv = cipher.iv
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val envelope = ByteArray(1 + iv.size + encrypted.size).apply {
                this[0] = iv.size.toByte()
                iv.copyInto(this, 1)
                encrypted.copyInto(this, 1 + iv.size)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREFIX + name, Base64.encodeToString(envelope, Base64.NO_WRAP))
                .apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to persist secret $name securely", e)
        }
    }

    @Synchronized
    fun get(context: Context, name: String): String? {
        val encoded = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREFIX + name, null)
            ?: return null
        return try {
            val envelope = Base64.decode(encoded, Base64.NO_WRAP)
            val ivLength = envelope.firstOrNull()?.toInt()?.and(0xff) ?: return null
            if (envelope.size <= 1 + ivLength) return null
            val iv = envelope.copyOfRange(1, 1 + ivLength)
            val ciphertext = envelope.copyOfRange(1 + ivLength, envelope.size)
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                updateAAD(name.toByteArray(Charsets.UTF_8))
            }
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (error: Exception) {
            Log.w(TAG, "Secure secret '$name' cannot be decrypted, returning null", error)
            remove(context, name)
            null
        }
    }

    fun remove(context: Context, name: String) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(PREFIX + name)
                .apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to remove secret $name", e)
        }
    }

    /** Wipes every secret in the secure store (cloud API token, backup secrets, DB passphrase, etc). Call on logout. */
    @Synchronized
    fun clearAll(context: Context) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to clear secure secrets", e)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            keyGen.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            keyGen.generateKey()
        } catch (e: Throwable) {
            Log.w(TAG, "AndroidKeyStore unavailable, falling back to software key", e)
            fallbackKey ?: synchronized(this) {
                fallbackKey ?: run {
                    val keyBytes = ByteArray(32).apply { SecureRandom().nextBytes(this) }
                    SecretKeySpec(keyBytes, "AES").also { fallbackKey = it }
                }
            }
        }
    }
}
