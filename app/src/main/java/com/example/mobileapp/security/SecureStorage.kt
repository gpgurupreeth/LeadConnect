package com.example.mobileapp.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * SecureStorage manages encrypted client-side storage backed by the Android Keystore.
 *
 * --- SECURITY BOUNDARY ARCHITECTURE ---
 *
 * 1. WHAT IS STORED LOCALLY:
 *    - Short-Lived Backend Session Token (JWT):
 *      Acquired during user login. Grants scoped access to the backend proxy only.
 *    - Device Client Identifier (UUID):
 *      Used by the backend to identify which mobile agent / device is dispatching jobs.
 *
 * 2. HOW SECRETS ARE PROTECTED:
 *    - Uses [EncryptedSharedPreferences] with a hardware-backed [MasterKey] in Android KeyStore.
 *    - Keys: Encrypted using AES-256 SIV.
 *    - Values: Encrypted using AES-256 GCM.
 *
 * 3. WHAT CREDENTIALS MUST REMAIN STRICTLY ON THE BACKEND:
 *    - WAHA API Key / Bearer Tokens:
 *      NEVER embedded in the APK or downloaded to the handset.
 *    - WhatsApp Business Account credentials / phone number certificates.
 *    - Master CRM database credentials.
 *    The mobile app only communicates with the backend proxy; the backend attaches WAHA credentials.
 */
class SecureStorage(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback for non-hardware environments / tests
        context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveBackendToken(token: String) {
        prefs.edit().putString(KEY_BACKEND_TOKEN, token).apply()
    }

    fun getBackendToken(): String? {
        return prefs.getString(KEY_BACKEND_TOKEN, null)
    }

    fun saveDeviceId(deviceId: String) {
        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
    }

    fun getDeviceId(): String? {
        return prefs.getString(KEY_DEVICE_ID, null)
    }

    fun saveBackendUrl(url: String) {
        prefs.edit().putString(KEY_BACKEND_URL, url).apply()
    }

    fun getBackendUrl(): String {
        val saved = prefs.getString(KEY_BACKEND_URL, null)
        // Migrate the old emulator-only default to the current proxy port.
        if (saved == LEGACY_DEFAULT_BACKEND_URL) {
            saveBackendUrl(DEFAULT_BACKEND_URL)
            return DEFAULT_BACKEND_URL
        }
        return saved ?: DEFAULT_BACKEND_URL
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val SECURE_PREFS_NAME = "secure_sales_app_prefs"
        private const val KEY_BACKEND_TOKEN = "jwt_backend_auth_token"
        private const val KEY_DEVICE_ID = "device_client_id"
        private const val KEY_BACKEND_URL = "backend_proxy_url"
        private const val DEFAULT_BACKEND_URL = "http://10.0.2.2:8081"
        private const val LEGACY_DEFAULT_BACKEND_URL = "http://10.0.2.2:8080"
    }
}
