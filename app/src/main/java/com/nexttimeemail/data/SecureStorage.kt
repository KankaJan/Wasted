package com.nexttimeemail.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Android Keystore-backed storage for everything sensitive at rest.
 *
 * - [prefs] is an [EncryptedSharedPreferences] instance whose contents are
 *   encrypted with a master key held in the hardware-backed AndroidKeyStore
 *   (never exported, never backed up). Used for app settings.
 * - [databasePassphrase] is a random 256-bit key, generated once and persisted
 *   in those same encrypted prefs, used to encrypt the Room/SQLCipher database.
 */
object SecureStorage {

    private const val PREFS_NAME = "nexttimeemail.secure"
    private const val KEY_DB_PASSPHRASE = "db_passphrase"

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    fun prefs(context: Context): SharedPreferences =
        cachedPrefs ?: synchronized(this) {
            cachedPrefs ?: create(context.applicationContext).also { cachedPrefs = it }
        }

    private fun create(appContext: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** The SQLCipher passphrase, created on first use and stored encrypted thereafter. */
    fun databasePassphrase(context: Context): ByteArray {
        val store = prefs(context)
        store.getString(KEY_DB_PASSPHRASE, null)?.let {
            return Base64.decode(it, Base64.NO_WRAP)
        }
        return synchronized(this) {
            store.getString(KEY_DB_PASSPHRASE, null)?.let {
                return Base64.decode(it, Base64.NO_WRAP)
            }
            val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
            store.edit().putString(KEY_DB_PASSPHRASE, Base64.encodeToString(fresh, Base64.NO_WRAP)).apply()
            fresh
        }
    }
}
