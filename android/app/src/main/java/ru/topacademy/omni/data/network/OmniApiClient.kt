package ru.topacademy.omni.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class OmniApiClient {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Dedicated client with followRedirects = false to capture 302 Set-Cookie headers on change-city
    private val noRedirectClient: OkHttpClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun login(username: String, password: String): Result<UserSession> = withContext(Dispatchers.IO) {
        try {
            // 1. Initial GET to fetch CSRF token and initial cookies
            val initRequest = Request.Builder()
                .url("$BASE_URL/login/index")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            val initResponse = client.newCall(initRequest).execute()
            val initHtml = initResponse.body?.string() ?: ""
            val initCookies = extractCookieHeaders(initResponse)

            val csrfDoc = Jsoup.parse(initHtml)
            val csrfToken = csrfDoc.select("meta[name=csrf-token]").attr("content")
                .ifEmpty {
                    val match = Regex("name=\"csrf-token\" content=\"([^\"]+)\"").find(initHtml)
                    match?.groupValues?.get(1) ?: ""
                }

            val initCookieHeader = initCookies.joinToString("; ") { it.substringBefore(";") }

            // 2. POST to /auth/login
            val loginPayload = JSONObject().apply {
                put("LoginForm", JSONObject().apply {
                    put("username", username)
                    put("password", password)
                    put("id_city", JSONObject.NULL)
                })
            }

            val loginRequest = Request.Builder()
                .url("$BASE_URL/auth/login")
                .header("Content-Type", "application/json;charset=UTF-8")
                .header("X-CSRF-Token", csrfToken)
                .header("Cookie", initCookieHeader)
                .header("User-Agent", USER_AGENT)
                .header("X-Requested-With", "XMLHttpRequest")
                .post(loginPayload.toString().toRequestBody(jsonMediaType))
                .build()

            val loginResponse = client.newCall(loginRequest).execute()
            val loginBody = loginResponse.body?.string() ?: ""
            val loginCookies = extractCookieHeaders(loginResponse)

            val allCookies = (initCookies + loginCookies)
            val mergedCookieMap = mutableMapOf<String, String>()
            for (c in allCookies) {
                val pair = c.substringBefore(";").trim()
                val idx = pair.indexOf('=')
                if (idx != -1) {
                    mergedCookieMap[pair.substring(0, idx).trim()] = pair.substring(idx + 1).trim()
                }
            }
            val baseCookieStr = mergedCookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }

            val loginJson = JSONObject(loginBody)
            val isSuccess = loginJson.optBoolean("success", false)

            if (!isSuccess) {
                val errorMsg = when {
                    loginJson.has("error") -> {
                        val errObj = loginJson.optJSONObject("error")
                        errObj?.optJSONArray("password")?.optString(0)
                            ?: errObj?.optJSONArray("username")?.optString(0)
                            ?: loginJson.optString("error", "Ошибка авторизации")
                    }
                    else -> "Неверный логин или пароль"
                }
                return@withContext Result.failure(Exception(errorMsg))
            }

            val idLocalHash = loginJson.optString("IdLocalHash", "")

            // 3. Discover branches & parse teacher profile dynamically
            val (parsedTeacherName, branches) = discoverBranchesAndProfile(baseCookieStr, idLocalHash)

            val teacherName = parsedTeacherName.ifEmpty {
                val cleanUser = username.substringBefore("@").replace(".", " ").replace("_", " ")
                cleanUser.split(" ")
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { part -> part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
                    .ifEmpty { "Преподаватель" }
            }

            // 4. Initial branch sessions with authenticated base cookie
            val branchSessions = branches.map {
                BranchSession(
                    cityId = it.first,
                    cityName = it.second,
                    cookieStr = baseCookieStr,
                    idLocalHash = idLocalHash
                )
            }

            val userSession = UserSession(
                username = username,
                cookieStr = baseCookieStr,
                idLocalHash = idLocalHash,
                teacherName = teacherName,
                branches = branchSessions
            )

            Result.success(userSession)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun discoverBranchesAndProfile(cookieStr: String, idLocalHash: String): Pair<String, List<Pair<String, String>>> {
        return try {
            val req = Request.Builder()
                .url("$BASE_URL/")
                .header("Cookie", cookieStr)
                .header("id-local-hash", idLocalHash)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: ""
            val doc = Jsoup.parse(html)

            // Extract teacher name dynamically from profile/header elements or scripts
            var name = ""
            // 1. Check exact user_name class from Omni TOP DOM (e.g. <div class="user_name">ФИО</div>)
            val userNameEl = doc.select(".user_name, div.user_name, .user-name, .teacher-name, .profile-name, .dropdown-user span, a.dropdown-toggle span")
                .firstOrNull { it.text().isNotBlank() }
            if (userNameEl != null) {
                name = userNameEl.text().trim()
            }

            // 2. Check regex in case DOM parser missed it
            if (name.isEmpty()) {
                val match = Regex("""<div[^>]*class=["'][^"']*user_name[^"']*["'][^>]*>\s*([^<]+)\s*</div>""").find(html)
                if (match != null) {
                    name = match.groupValues[1].trim()
                }
            }

            // 3. Check script variables
            if (name.isEmpty()) {
                for (s in doc.select("script")) {
                    val data = s.data()
                    val match = Regex("""["'](?:fio|teacher_name|fullName|user_name)["']\s*:\s*["']([^"']+)["']""").find(data)
                    if (match != null) {
                        name = match.groupValues[1].trim()
                        break
                    }
                }
            }

            // Extract branches from #cityes or changeCity calls
            val branches = mutableListOf<Pair<String, String>>()
            val seen = mutableSetOf<String>()
            val regex = Regex("changeCity\\((\\d+)\\)[^>]*>\\s*([^<]+)\\s*<")
            for (m in regex.findAll(html)) {
                val cityId = m.groupValues[1]
                val cityName = m.groupValues[2].trim()
                if (seen.add(cityId)) {
                    branches.add(cityId to cityName)
                }
            }

            name to branches
        } catch (_: Exception) {
            "" to emptyList()
        }
    }

    suspend fun switchCity(baseCookieStr: String, idLocalHash: String, cityId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$BASE_URL/auth/change-city?city=$cityId")
                .header("Cookie", baseCookieStr)
                .header("id-local-hash", idLocalHash)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .header("Origin", BASE_URL)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "*/*")
                .get()
                .build()

            val resp = noRedirectClient.newCall(req).execute()
            val code = resp.code
            val location = resp.header("Location") ?: ""
            if (code in listOf(302, 301) && location.contains("login/index")) {
                throw SessionExpiredException("HTTP $code: Перенаправление на страницу входа")
            }

            val setCookies = extractCookieHeaders(resp)
            val cookieMap = mutableMapOf<String, String>()

            for (c in baseCookieStr.split(";")) {
                val idx = c.indexOf('=')
                if (idx != -1) cookieMap[c.substring(0, idx).trim()] = c.substring(idx + 1).trim()
            }
            for (sc in setCookies) {
                val pair = sc.substringBefore(";")
                val idx = pair.indexOf('=')
                if (idx != -1) cookieMap[pair.substring(0, idx).trim()] = pair.substring(idx + 1).trim()
            }
            val newCookieStr = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
            Result.success(newCookieStr)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getActiveCityId(cookieStr: String): String? {
        val match = Regex("""city_id[=:][^;]*%3Bi%3A(\d+)%3B""").find(cookieStr)
            ?: Regex("""city_id[=:][^;]*i:(\d+);""").find(cookieStr)
        return match?.groupValues?.get(1)
    }

    suspend fun fetchSchedule(cookieStr: String, idLocalHash: String, weekOffset: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("week", weekOffset)
            }.toString()

            val req = Request.Builder()
                .url("$BASE_URL/schedule/get-schedule")
                .header("Content-Type", "application/json;charset=UTF-8")
                .header("Cookie", cookieStr)
                .header("id-local-hash", idLocalHash)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .header("Origin", BASE_URL)
                .header("X-Requested-With", "XMLHttpRequest")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: "{}"
            checkSessionValid(resp.code, body)
            if (!resp.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${resp.code}"))
            }
            if (!body.trimStart().startsWith("{")) {
                return@withContext Result.failure(Exception("Некорректный ответ сервера (не JSON)"))
            }
            Result.success(body)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchTasks(cookieStr: String, idLocalHash: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val payload = "{}"
            val req = Request.Builder()
                .url("$BASE_URL/tasks/not-done-tasks")
                .header("Content-Type", "application/json;charset=UTF-8")
                .header("Cookie", cookieStr)
                .header("id-local-hash", idLocalHash)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .header("Origin", BASE_URL)
                .header("X-Requested-With", "XMLHttpRequest")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: "{}"
            checkSessionValid(resp.code, body)
            if (!resp.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${resp.code}"))
            }
            if (!body.trimStart().startsWith("{")) {
                return@withContext Result.failure(Exception("Некорректный ответ сервера (не JSON)"))
            }
            Result.success(body)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchPresents(
        cookieStr: String,
        idLocalHash: String,
        lenta: Int,
        group: String,
        date: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("lenta", lenta.toString())
                put("date", date)
            }.toString()

            val req = Request.Builder()
                .url("$BASE_URL/presents/get-presents")
                .header("Content-Type", "application/json;charset=UTF-8")
                .header("Cookie", cookieStr)
                .header("id-local-hash", idLocalHash)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .header("Origin", BASE_URL)
                .header("X-Requested-With", "XMLHttpRequest")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: "{}"
            checkSessionValid(resp.code, body)
            if (!resp.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${resp.code}"))
            }
            if (!body.trimStart().startsWith("{")) {
                return@withContext Result.failure(Exception("Некорректный ответ сервера (не JSON)"))
            }
            Result.success(body)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun checkSessionValid(code: Int, body: String) {
        if (code in listOf(302, 401, 403)) {
            throw SessionExpiredException("HTTP $code: Сессия истекла")
        }
        val trimmed = body.trim()
        if (trimmed.startsWith("<!DOCTYPE", ignoreCase = true) || trimmed.startsWith("<html", ignoreCase = true) || trimmed.contains("login/index")) {
            throw SessionExpiredException("Получена страница входа: сессия истекла")
        }
    }

    private fun extractCookieHeaders(response: Response): List<String> {
        return response.headers("Set-Cookie").ifEmpty { response.headers("set-cookie") }
    }

    class SessionExpiredException(message: String = "Сессия истекла") : Exception(message)

    companion object {
        private const val BASE_URL = "https://omni.top-academy.ru"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
