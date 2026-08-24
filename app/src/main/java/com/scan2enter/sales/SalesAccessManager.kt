package com.scan2enter.sales

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class SalesAccessManager(
    context: Context
) {
    private val prefs =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    fun hasPassword(): Boolean {
        return prefs.contains(KEY_HASH) &&
                prefs.contains(KEY_SALT)
    }

    fun setPassword(password: String) {
        require(password.length >= MIN_PASSWORD_LENGTH) {
            "La password deve contenere almeno $MIN_PASSWORD_LENGTH caratteri"
        }

        val salt = ByteArray(SALT_SIZE)
        SecureRandom().nextBytes(salt)

        val hash = hashPassword(
            password = password,
            salt = salt
        )

        prefs.edit()
            .putString(
                KEY_SALT,
                Base64.encodeToString(salt, Base64.NO_WRAP)
            )
            .putString(
                KEY_HASH,
                Base64.encodeToString(hash, Base64.NO_WRAP)
            )
            .apply()
    }

    fun verifyPassword(password: String): Boolean {
        val saltText = prefs.getString(KEY_SALT, null)
            ?: return false

        val hashText = prefs.getString(KEY_HASH, null)
            ?: return false

        val salt = runCatching {
            Base64.decode(saltText, Base64.NO_WRAP)
        }.getOrNull() ?: return false

        val expectedHash = runCatching {
            Base64.decode(hashText, Base64.NO_WRAP)
        }.getOrNull() ?: return false

        val actualHash = hashPassword(
            password = password,
            salt = salt
        )

        return constantTimeEquals(
            expectedHash,
            actualHash
        )
    }

    fun changePassword(
        currentPassword: String,
        newPassword: String
    ): Result<Unit> = runCatching {
        check(verifyPassword(currentPassword)) {
            "Password attuale non corretta"
        }

        setPassword(newPassword)
    }

    private fun hashPassword(
        password: String,
        salt: ByteArray
    ): ByteArray {
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            ITERATIONS,
            KEY_LENGTH_BITS
        )

        return try {
            SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun constantTimeEquals(
        first: ByteArray,
        second: ByteArray
    ): Boolean {
        if (first.size != second.size) {
            return false
        }

        var result = 0

        for (index in first.indices) {
            result =
                result or
                        (first[index].toInt() xor second[index].toInt())
        }

        return result == 0
    }

    companion object {
        private const val PREFS_NAME = "sales_access"
        private const val KEY_SALT = "password_salt"
        private const val KEY_HASH = "password_hash"

        const val MIN_PASSWORD_LENGTH = 4

        private const val SALT_SIZE = 16
        private const val ITERATIONS = 120_000
        private const val KEY_LENGTH_BITS = 256
    }
}
