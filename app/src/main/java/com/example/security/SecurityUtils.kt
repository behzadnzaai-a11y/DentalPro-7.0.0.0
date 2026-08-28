package com.example.security

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

object SecurityUtils {

    private const val LEGACY_PBKDF2_ITERATIONS = 10_000
    private const val CURRENT_PBKDF2_ITERATIONS = 210_000
    private const val PIN_HASH_VERSION = "v2"
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val SALT_LENGTH_BYTES = 16

    private val secureRandom = SecureRandom()

    // ----------------------------------------------------
    // PIN HASHING & VERIFICATION (Salted PBKDF2 / SHA-256)
    // ----------------------------------------------------

    private fun encodeBase64(bytes: ByteArray): String {
        return try {
            java.util.Base64.getEncoder().encodeToString(bytes)
        } catch (e: Throwable) {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }

    private fun decodeBase64(str: String): ByteArray {
        return try {
            java.util.Base64.getDecoder().decode(str)
        } catch (e: Throwable) {
            Base64.decode(str, Base64.NO_WRAP)
        }
    }

    fun generateSalt(): String {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)
        return encodeBase64(salt)
    }

    fun hashPin(pin: String, saltBase64: String): String {
        val encoded = derivePinHash(pin, saltBase64, CURRENT_PBKDF2_ITERATIONS)
        return "$PIN_HASH_VERSION\$$CURRENT_PBKDF2_ITERATIONS\$$encoded"
    }

    fun verifyPin(enteredPin: String, storedHash: String, storedSalt: String): Boolean {
        if (storedHash.isBlank() || storedSalt.isBlank()) return false
        return try {
            val parts = storedHash.split('$')
            val iterations: Int
            val expected: String
            if (parts.size == 3 && parts[0] == PIN_HASH_VERSION) {
                iterations = parts[1].toInt()
                if (iterations !in 100_000..1_000_000) return false
                expected = parts[2]
            } else {
                // Versionless hashes shipped by earlier builds used 10,000 rounds.
                iterations = LEGACY_PBKDF2_ITERATIONS
                expected = storedHash
            }
            val computed = derivePinHash(enteredPin, storedSalt, iterations)
            MessageDigest.isEqual(
                computed.toByteArray(StandardCharsets.US_ASCII),
                expected.toByteArray(StandardCharsets.US_ASCII)
            )
        } catch (_: Exception) {
            false
        }
    }

    fun needsPinHashUpgrade(storedHash: String): Boolean =
        !storedHash.startsWith("$PIN_HASH_VERSION\$$CURRENT_PBKDF2_ITERATIONS\$")

    private fun derivePinHash(pin: String, saltBase64: String, iterations: Int): String {
        val salt = decodeBase64(saltBase64)
        val password = pin.toCharArray()
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            encodeBase64(factory.generateSecret(spec).encoded)
        } finally {
            password.fill('\u0000')
            spec.clearPassword()
            salt.fill(0)
        }
    }

    // ----------------------------------------------------
    // SECURE KEY GENERATION
    // ----------------------------------------------------

    fun generateRandomEncryptionKey(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return encodeBase64(bytes)
    }

    fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    // ----------------------------------------------------
    // AES-256-GCM ENCRYPTION & DECRYPTION FOR BACKUPS
    // ----------------------------------------------------

    private fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val password = passphrase.toCharArray()
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            password.fill('\u0000')
            spec.clearPassword()
        }
    }

    fun encryptPayload(plainText: String, secretKeyPass: String): String {
        require(secretKeyPass.length >= 12) { "Encryption secret must contain at least 12 characters" }
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val aesKey = deriveKey(secretKeyPass, salt, CURRENT_PBKDF2_ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec)

        val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

        val envelope = JSONObject()
        envelope.put("version", 2)
        envelope.put("encrypted", true)
        envelope.put("iterations", CURRENT_PBKDF2_ITERATIONS)
        envelope.put("salt", encodeBase64(salt))
        envelope.put("iv", encodeBase64(iv))
        envelope.put("ciphertext", encodeBase64(cipherBytes))

        return envelope.toString()
    }

    fun decryptPayload(encryptedEnvelopeOrPlain: String, secretKeyPass: String): String {
        if (!encryptedEnvelopeOrPlain.trim().startsWith("{")) {
            return encryptedEnvelopeOrPlain
        }
        try {
            val json = JSONObject(encryptedEnvelopeOrPlain)
            if (!json.optBoolean("encrypted", false)) {
                return encryptedEnvelopeOrPlain
            }
            val salt = decodeBase64(json.getString("salt"))
            val iv = decodeBase64(json.getString("iv"))
            val ciphertext = decodeBase64(json.getString("ciphertext"))
            val version = json.optInt("version", 1)
            val iterations = when (version) {
                1 -> 5_000
                2 -> json.optInt("iterations", CURRENT_PBKDF2_ITERATIONS).also {
                    require(it in 100_000..1_000_000) { "Invalid encryption parameters" }
                }
                else -> throw IllegalArgumentException("Unsupported encrypted payload version")
            }

            val aesKey = deriveKey(secretKeyPass, salt, iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec)

            val plainBytes = cipher.doFinal(ciphertext)
            return String(plainBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            throw IllegalArgumentException("Decryption failed: Incorrect key or corrupted payload (${e.message})")
        }
    }
}
