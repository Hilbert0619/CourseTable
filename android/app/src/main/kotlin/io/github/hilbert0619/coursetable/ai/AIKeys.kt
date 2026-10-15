package io.github.hilbert0619.coursetable.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Each AI connection's API key, by the connection's ID. */
interface AIKeys {
    fun key(id: UUID): String?

    /** Stores [key], or removes the stored one when it's `null` or empty. */
    fun setKey(id: UUID, key: String?)
}

/**
 * The keys encrypted (AES-GCM) with a key that stays inside the Android Keystore, kept in the `ai_keys` preferences
 * file, which backups leave out (`backup_rules.xml`, `data_extraction_rules.xml`). Never logged or exported.
 */
class KeystoreAIKeys(context: Context) : AIKeys {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun key(id: UUID): String? {
        val stored = prefs.getString(id.toString(), null) ?: return null
        return try {
            decrypt(stored)
        } catch (error: Exception) {
            // E.g. a Keystore key lost after a restore; the key has to be typed again.
            Log.w(TAG, "A stored API key can't be read")
            null
        }
    }

    override fun setKey(id: UUID, key: String?) {
        val editor = prefs.edit()
        if (key.isNullOrEmpty()) editor.remove(id.toString()) else editor.putString(id.toString(), encrypt(key))
        editor.apply()
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    /** Base64 of the 12-byte IV followed by the ciphertext. */
    private fun encrypt(text: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes, 0, IV_LENGTH))
        return String(cipher.doFinal(bytes, IV_LENGTH, bytes.size - IV_LENGTH), Charsets.UTF_8)
    }

    companion object {
        const val FILE = "ai_keys"
        private const val TAG = "AIKeys"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "studyhour.ai-keys"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
    }
}

/** Keys in memory only: tests and screenshots. */
class MemoryAIKeys(initial: Map<UUID, String> = emptyMap()) : AIKeys {
    private val keys = initial.toMutableMap()
    override fun key(id: UUID): String? = keys[id]
    override fun setKey(id: UUID, key: String?) {
        if (key.isNullOrEmpty()) keys.remove(id) else keys[id] = key
    }
}
