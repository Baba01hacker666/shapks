package com.shapks.biovault

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONArray
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class VaultStorage(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("biovault_encrypted_store", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SECRETS_DATA = "vault_payload"
        private const val KEY_ENCRYPTION_KEY = "vault_sec_key"
        private const val KEY_AUTOLOCK_ENABLED = "setting_autolock"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }

    private fun getOrCreateKey(): SecretKey {
        val existing = prefs.getString(KEY_ENCRYPTION_KEY, null)
        if (existing != null) {
            val decoded = Base64.decode(existing, Base64.DEFAULT)
            return SecretKeySpec(decoded, "AES")
        }
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val key = keyGen.generateKey()
        prefs.edit()
            .putString(KEY_ENCRYPTION_KEY, Base64.encodeToString(key.encoded, Base64.DEFAULT))
            .apply()
        return key
    }

    private fun encrypt(plaintext: String): String {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        return Base64.encodeToString(combined, Base64.DEFAULT)
    }

    private fun decrypt(encryptedBase64: String): String {
        return try {
            val key = getOrCreateKey()
            val combined = Base64.decode(encryptedBase64, Base64.DEFAULT)
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    fun loadSecrets(): MutableList<SecretItem> {
        val raw = prefs.getString(KEY_SECRETS_DATA, null) ?: return mutableListOf()
        val decryptedJson = decrypt(raw)
        if (decryptedJson.isBlank()) return mutableListOf()

        val list = mutableListOf<SecretItem>()
        try {
            val jsonArray = JSONArray(decryptedJson)
            for (i in 0 until jsonArray.length()) {
                list.add(SecretItem.fromJson(jsonArray.getJSONObject(i)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveSecrets(secrets: List<SecretItem>) {
        val array = JSONArray()
        for (item in secrets) {
            array.put(item.toJson())
        }
        val encrypted = encrypt(array.toString())
        prefs.edit().putString(KEY_SECRETS_DATA, encrypted).apply()
    }

    fun isAutoLockEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTOLOCK_ENABLED, true)
    }

    fun setAutoLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOLOCK_ENABLED, enabled).apply()
    }
}
