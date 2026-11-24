package ru.topacademy.omni.data.network

import android.content.Context
import android.content.SharedPreferences
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
    private val prefs: SharedPreferences =
        context.getSharedPreferences("omni_native_session", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveSession(session: UserSession) {
        prefs.edit()
            .putString(KEY_SESSION_JSON, gson.toJson(session))
            .apply()
    }

    fun saveCredentials(username: String, pass: String) {
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
