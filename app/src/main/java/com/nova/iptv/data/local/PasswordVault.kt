package com.nova.iptv.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Xtream passwords and parental PIN material never live in Room plaintext.
 */
@Singleton
class PasswordVault @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = runCatching {
        val key = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "nova_vault",
            key,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse { err ->
        Timber.e(err, "EncryptedSharedPreferences unavailable — falling back to private prefs")
        context.getSharedPreferences("nova_vault_fallback", Context.MODE_PRIVATE)
    }

    fun putPassword(playlistId: String, password: String) {
        prefs.edit().putString("pw_$playlistId", password).apply()
    }

    fun getPassword(playlistId: String): String = prefs.getString("pw_$playlistId", "") ?: ""

    fun deletePassword(playlistId: String) {
        prefs.edit().remove("pw_$playlistId").apply()
    }

    fun exportPasswords(): Map<String, String> {
        return prefs.all.mapNotNull { (k, v) ->
            if (k.startsWith("pw_") && v is String) k.removePrefix("pw_") to v else null
        }.toMap()
    }
}
