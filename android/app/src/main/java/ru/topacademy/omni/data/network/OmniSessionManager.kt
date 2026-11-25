package ru.topacademy.omni.data.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import ru.topacademy.omni.domain.model.Branch

data class BranchSession(
    val cityId: String,
    val cityName: String,
    val cookieStr: String,
    val idLocalHash: String
)

data class UserSession(
    val username: String,
    val cookieStr: String,
    val idLocalHash: String,
    val teacherName: String = "",
    val branches: List<BranchSession> = emptyList()
)

class OmniSessionManager(context: Context) {
    private var isEncryptedStorage: Boolean = false
    private val prefs: SharedPreferences = createEncryptedPrefs(context)
    private val gson = Gson()

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val enc = EncryptedSharedPreferences.create(
                context,
                "omni_native_session_enc",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            isEncryptedStorage = true
            enc
        } catch (_: Exception) {
            // Fallback on devices with broken Keystore hardware; never store plaintext passwords here
            isEncryptedStorage = false
            context.getSharedPreferences("omni_native_session", Context.MODE_PRIVATE)
        }
    }

    fun saveSession(session: UserSession) {
        prefs.edit()
            .putString(KEY_SESSION_JSON, gson.toJson(session))
            .apply()
    }

    fun saveCredentials(username: String, pass: String) {
        if (!isEncryptedStorage) {
            // Refuse to persist plaintext password if Keystore encryption is unavailable
            prefs.edit().remove(KEY_USER).remove(KEY_PASS).apply()
            return
        }
        prefs.edit()
            .putString(KEY_USER, username)
            .putString(KEY_PASS, pass)
            .apply()
    }

    fun getCredentials(): Pair<String, String>? {
        val user = prefs.getString(KEY_USER, null) ?: return null
        val pass = prefs.getString(KEY_PASS, null) ?: return null
        return Pair(user, pass)
    }

    fun clearCredentials() {
        prefs.edit().remove(KEY_USER).remove(KEY_PASS).apply()
    }

    fun getSession(): UserSession? {
        val json = prefs.getString(KEY_SESSION_JSON, null) ?: return null
        return try {
            gson.fromJson(json, UserSession::class.java)
        } catch (_: Exception) {
            null
        }
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean = getSession() != null || getCredentials() != null

    companion object {
        private const val KEY_SESSION_JSON = "key_user_session_json"
        private const val KEY_USER = "key_saved_username"
        private const val KEY_PASS = "key_saved_password"
    }
}
