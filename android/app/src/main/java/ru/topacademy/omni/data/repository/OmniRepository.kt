package ru.topacademy.omni.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import ru.topacademy.omni.BuildConfig
import ru.topacademy.omni.data.calendar.CalendarSyncManager
import ru.topacademy.omni.data.db.BranchEntity
import ru.topacademy.omni.data.db.LessonEntity
import ru.topacademy.omni.data.db.OmniDatabase
import ru.topacademy.omni.data.db.TaskQueueEntity
import ru.topacademy.omni.data.db.TeacherProfileEntity
import ru.topacademy.omni.data.network.OmniApiClient
import ru.topacademy.omni.data.network.OmniSessionManager
import ru.topacademy.omni.data.network.UserSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.topacademy.omni.data.parser.OmniScheduleParser
import ru.topacademy.omni.domain.model.Branch
import ru.topacademy.omni.domain.model.Lesson
import ru.topacademy.omni.domain.model.ReviewStudent
import ru.topacademy.omni.domain.model.TaskQueue
import ru.topacademy.omni.domain.model.TeacherProfile
import ru.topacademy.omni.domain.model.WeekSchedule
import ru.topacademy.omni.widget.ScheduleGlanceWidget
import androidx.glance.appwidget.updateAll
import java.time.format.DateTimeFormatter

class OmniRepository(private val context: Context) {

    private val db = OmniDatabase.getInstance(context)
    private val apiClient = OmniApiClient()
    private val sessionManager = OmniSessionManager(context)
    val calendarSyncManager = CalendarSyncManager(context)
    private val gson = Gson()
    private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private object RepoLog {
        fun d(tag: String, msg: String) { if (BuildConfig.DEBUG) android.util.Log.d(tag, msg) }
        fun i(tag: String, msg: String) { if (BuildConfig.DEBUG) android.util.Log.i(tag, msg) }
        fun w(tag: String, msg: String) { if (BuildConfig.DEBUG) android.util.Log.w(tag, msg) }
        fun e(tag: String, msg: String) { if (BuildConfig.DEBUG) android.util.Log.e(tag, msg) }
    }

    fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()

    suspend fun login(username: String, password: String): Result<Unit> {
        val result = apiClient.login(username, password)
        return if (result.isSuccess) {
            val session = result.getOrThrow()

            // 1. Completely clear all previous user data to prevent cross-account data leakage
            db.clearAllUserData()

            sessionManager.saveSession(session)
            sessionManager.saveCredentials(username, password)

            // Save profile & branches into Room
            val profileEntity = TeacherProfileEntity(
                id = 1,
                teacherId = session.username,
                name = session.teacherName,
                email = session.username
            )
            db.profileDao().insertProfile(profileEntity)

            val branchEntities = session.branches.map { b ->
                val category = OmniScheduleParser.classifyBranch(b.cityName)
                val color = OmniScheduleParser.getBranchColor(b.cityName, category)
                BranchEntity(b.cityId, b.cityName, category, color)
            }
            db.profileDao().insertBranches(branchEntities)

            // Initial sync of current week and tasks
            syncWeekSchedule(0)
            syncTasks()

            // Refresh home screen widgets immediately
            try {
                ru.topacademy.omni.widget.ScheduleGlanceWidget().updateAll(context)
            } catch (_: Exception) {}

            Result.success(Unit)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Ошибка входа"))
        }
    }

    private val reloginMutex = Mutex()

    suspend fun relogin(): Result<UserSession> = reloginMutex.withLock {
        val creds = sessionManager.getCredentials()
            ?: return@withLock Result.failure(Exception("Нет сохраненных учетных данных"))
        RepoLog.d("OmniRepo", "Starting implicit relogin for ${creds.first}...")
        val res = apiClient.login(creds.first, creds.second)
        if (res.isSuccess) {
            val session = res.getOrThrow()
            sessionManager.saveSession(session)
            val profileEntity = TeacherProfileEntity(
                id = 1,
                teacherId = session.username,
                name = session.teacherName,
                email = session.username
            )
            db.profileDao().insertProfile(profileEntity)
            RepoLog.i("OmniRepo", "Implicit relogin succeeded for ${session.teacherName}!")
            Result.success(session)
        } else {
            RepoLog.e("OmniRepo", "Implicit relogin failed: ${res.exceptionOrNull()?.message}")
            Result.failure(res.exceptionOrNull() ?: Exception("Не удалось обновить сессию"))
        }
    }

    private val sessionMutex = Mutex()

    private suspend fun ensureCityActive(targetCityId: String): Result<UserSession> = sessionMutex.withLock {
        var session = sessionManager.getSession() ?: relogin().getOrNull()
            ?: return@withLock Result.failure(Exception("Нет активной сессии"))

        val currentCity = apiClient.getActiveCityId(session.cookieStr)
        if (currentCity == targetCityId) {
            return@withLock Result.success(session)
        }

        RepoLog.i("OmniRepo", "Switching active city from $currentCity to $targetCityId...")
        var res = apiClient.switchCity(session.cookieStr, session.idLocalHash, targetCityId)
        if (res.isFailure && res.exceptionOrNull() is OmniApiClient.SessionExpiredException) {
            val refreshed = relogin().getOrNull()
            if (refreshed != null) {
                session = refreshed
                res = apiClient.switchCity(session.cookieStr, session.idLocalHash, targetCityId)
            }
        }

        if (res.isSuccess) {
            val newCookie = res.getOrThrow()
            val updated = session.copy(cookieStr = newCookie)
            sessionManager.saveSession(updated)
            Result.success(updated)
        } else {
            RepoLog.w("OmniRepo", "Failed to switch city to $targetCityId: ${res.exceptionOrNull()?.message}")
            Result.success(session)
        }
    }

    suspend fun syncCalendarNow(): Result<Int> = calendarSyncManager.syncFromDb(db)

    suspend fun clearCalendar(): Result<Int> = calendarSyncManager.clearAll()

    suspend fun logout() {
        sessionManager.clearSession()
        sessionManager.clearCredentials()
        db.clearAllUserData()
        try {
            calendarSyncManager.onLogout()
        } catch (_: Exception) {}
        try {
            ru.topacademy.omni.widget.ScheduleGlanceWidget().updateAll(context)
        } catch (_: Exception) {}
    }

    fun getTeacherProfile(): Flow<TeacherProfile?> {
        return combine(db.profileDao().getProfile(), db.profileDao().getBranches()) { profile, branches ->
            if (profile != null) {
                TeacherProfile(
                    id = profile.teacherId.ifEmpty { profile.name },
                    name = profile.name,
                    email = profile.email,
                    branches = branches.map { it.toDomain() }
                )
            } else null
        }
    }

    fun getBranches(): Flow<List<Branch>> {
        return db.profileDao().getBranches().map { list -> list.map { it.toDomain() } }
    }

    fun getWeekSchedule(weekOffset: Int): Flow<WeekSchedule> {
        val dates = OmniScheduleParser.getWeekDates(weekOffset)
        val startDate = dates.first().format(isoFormatter)
        val endDate = dates.last().format(isoFormatter)

        return db.lessonDao().getLessonsForWeek(startDate, endDate).map { entities ->
            val lessons = entities.map { it.toDomain() }
            OmniScheduleParser.buildWeekSchedule(lessons, weekOffset)
        }
    }

    suspend fun syncWeekSchedule(weekOffset: Int, fetchAttendance: Boolean = true): Result<Unit> {
        val initialSession = sessionManager.getSession() ?: relogin().getOrNull()
            ?: return Result.failure(Exception("Нет активной сессии"))
        var currentSession = initialSession
        val dates = OmniScheduleParser.getWeekDates(weekOffset)
        val startDate = dates.first().format(isoFormatter)
        val endDate = dates.last().format(isoFormatter)

        val aggregatedLessons = mutableListOf<Lesson>()
        val branchesToQuery = if (currentSession.branches.isNotEmpty()) currentSession.branches else {
            listOf(ru.topacademy.omni.data.network.BranchSession(cityId = "95", cityName = "Барнаул", cookieStr = currentSession.cookieStr, idLocalHash = currentSession.idLocalHash))
        }

        RepoLog.d("OmniRepo", "syncWeekSchedule for week=$weekOffset across ${branchesToQuery.size} branches")

        var hasRelogged = false
        // Fetch schedule for all branches associated with teacher
        for (branchSession in branchesToQuery) {
            val branch = Branch(
                cityId = branchSession.cityId,
                cityName = branchSession.cityName,
                category = OmniScheduleParser.classifyBranch(branchSession.cityName),
                colorHex = OmniScheduleParser.getBranchColor(branchSession.cityName, OmniScheduleParser.classifyBranch(branchSession.cityName))
            )

            var activeSession = ensureCityActive(branch.cityId).getOrNull() ?: currentSession

            var schedRes = apiClient.fetchSchedule(
                cookieStr = activeSession.cookieStr,
                idLocalHash = activeSession.idLocalHash,
                weekOffset = weekOffset
            )

            if (schedRes.isFailure && schedRes.exceptionOrNull() is OmniApiClient.SessionExpiredException && !hasRelogged) {
                RepoLog.w("OmniRepo", "Session expired during fetchSchedule. Triggering implicit relogin...")
                val refreshed = relogin().getOrNull()
                if (refreshed != null) {
                    hasRelogged = true
                    currentSession = refreshed
                    activeSession = ensureCityActive(branch.cityId).getOrNull() ?: currentSession
                    schedRes = apiClient.fetchSchedule(
                        cookieStr = activeSession.cookieStr,
                        idLocalHash = activeSession.idLocalHash,
                        weekOffset = weekOffset
                    )
                }
            }

            if (schedRes.isSuccess) {
                val raw = schedRes.getOrThrow()
                val parsed = OmniScheduleParser.parseSchedule(raw, branch, weekOffset)
                RepoLog.d("OmniRepo", "Branch ${branch.cityName} returned ${parsed.size} lessons (raw length=${raw.length})")
                aggregatedLessons.addAll(parsed)
            } else {
                RepoLog.e("OmniRepo", "Branch ${branch.cityName} failed: ${schedRes.exceptionOrNull()?.message}")
            }
        }

        RepoLog.d("OmniRepo", "Total aggregated lessons: ${aggregatedLessons.size}")

        // Cache into Room: preserve attendance marks for current teacher's lessons if already known
        val existingLessons = try {
            db.lessonDao().getLessonsForWeekSync(startDate, endDate)
        } catch (_: Exception) {
            emptyList()
        }
        val existingMarkedKeys = existingLessons
            .filter { it.isAttendanceMarked }
            .map { "${it.date}_${it.lentaNum}_${it.branchId}" }
            .toSet()
        val existingMarkedIds = existingLessons
            .filter { it.isAttendanceMarked }
            .map { it.id }
            .toSet()

        val entities = aggregatedLessons.map { lesson ->
            val key = "${lesson.date}_${lesson.lentaNum}_${lesson.branchId}"
            val marked = existingMarkedIds.contains(lesson.id) ||
                    existingMarkedKeys.contains(key) ||
                    lesson.isAttendanceMarked
            LessonEntity.fromDomain(lesson.copy(isAttendanceMarked = marked))
        }

        // CRUCIAL: Clear the week range in Room so any canceled/displaced lessons are removed
        db.lessonDao().clearWeek(startDate, endDate)
        if (entities.isNotEmpty()) {
            db.lessonDao().insertLessons(entities)
        }
        RepoLog.d("OmniRepo", "Saved ${entities.size} lessons to Room for $startDate..$endDate (preserved ${existingMarkedKeys.size} marked)")

        // Refresh widgets
        try {
            ru.topacademy.omni.widget.ScheduleGlanceWidget().updateAll(context)
        } catch (_: Exception) {}

        // Sync to device calendar if enabled
        if (weekOffset in 0..1 && calendarSyncManager.isEnabled()) {
            try {
                calendarSyncManager.syncFromDb(db)
            } catch (_: Exception) {}
        }

        if (fetchAttendance && weekOffset <= 0) {
            syncAttendanceForPastWeekLessons(weekOffset)
        }

        return Result.success(Unit)
    }

    fun getTaskQueue(): Flow<TaskQueue> {
        return db.taskDao().getTaskQueue().map { entity ->
            if (entity != null) {
                val type = object : TypeToken<List<ReviewStudent>>() {}.type
                val students: List<ReviewStudent> = try {
                    gson.fromJson(entity.rawStudentsJson, type) ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
                TaskQueue(
                    newHomeworkCount = entity.newHomeworkCount,
                    pendingReviewsCount = entity.pendingReviewsCount,
                    oldestHwWaitDays = entity.oldestHwWaitDays,
                    oldestReviewWaitDays = entity.oldestReviewWaitDays,
                    oldestHwWaitHours = entity.oldestHwWaitHours,
                    reviewStudents = students
                )
            } else {
                TaskQueue()
            }
        }
    }

    suspend fun syncTasks(): Result<Unit> {
        val initialSession = sessionManager.getSession() ?: relogin().getOrNull()
            ?: return Result.failure(Exception("Нет активной сессии"))
        var currentSession = initialSession
        val branchesToQuery = if (currentSession.branches.isNotEmpty()) currentSession.branches else {
            listOf(ru.topacademy.omni.data.network.BranchSession(cityId = "95", cityName = "Барнаул", cookieStr = currentSession.cookieStr, idLocalHash = currentSession.idLocalHash))
        }

        var totalHw = 0
        var totalReviews = 0
        var maxHwDays = 0
        var maxHwHours = 0
        var maxReviewDays = 0
        val allStudents = mutableListOf<ReviewStudent>()
        val seenStudentNames = mutableSetOf<String>()

        var hasRelogged = false
        for (b in branchesToQuery) {
            var activeSession = ensureCityActive(b.cityId).getOrNull() ?: currentSession
            var tasksRes = apiClient.fetchTasks(activeSession.cookieStr, activeSession.idLocalHash)

            if (tasksRes.isFailure && tasksRes.exceptionOrNull() is OmniApiClient.SessionExpiredException && !hasRelogged) {
                RepoLog.w("OmniRepo", "Session expired during fetchTasks. Triggering implicit relogin...")
                val refreshed = relogin().getOrNull()
                if (refreshed != null) {
                    hasRelogged = true
                    currentSession = refreshed
                    activeSession = ensureCityActive(b.cityId).getOrNull() ?: currentSession
                    tasksRes = apiClient.fetchTasks(activeSession.cookieStr, activeSession.idLocalHash)
                }
            }

            if (tasksRes.isSuccess) {
                val parsed = OmniScheduleParser.parseTasks(tasksRes.getOrThrow())
                totalHw += parsed.newHomeworkCount
                totalReviews += parsed.pendingReviewsCount
                if (parsed.oldestHwWaitDays > maxHwDays) maxHwDays = parsed.oldestHwWaitDays
                if (parsed.oldestHwWaitHours > maxHwHours) maxHwHours = parsed.oldestHwWaitHours
                if (parsed.oldestReviewWaitDays > maxReviewDays) maxReviewDays = parsed.oldestReviewWaitDays
                for (s in parsed.reviewStudents) {
                    if (seenStudentNames.add("${s.name}_${s.group}_${s.subject}")) {
                        allStudents.add(s)
                    }
                }
            }
        }

        val entity = TaskQueueEntity(
            id = 1,
            newHomeworkCount = totalHw,
            pendingReviewsCount = totalReviews,
            oldestHwWaitDays = maxHwDays,
            oldestReviewWaitDays = maxReviewDays,
            oldestHwWaitHours = maxHwHours,
            rawStudentsJson = gson.toJson(allStudents)
        )
        db.taskDao().insertTaskQueue(entity)
        RepoLog.d("OmniRepo", "Synced tasks across branches: $totalHw HW, $totalReviews reviews, ${allStudents.size} students")
        return Result.success(Unit)
    }

    suspend fun toggleAttendance(lessonId: String, isMarked: Boolean) {
        db.lessonDao().updateAttendanceStatus(lessonId, isMarked)
    }

    suspend fun checkLessonAttendance(lesson: Lesson): Boolean {
        // If already marked in DB, no need to re-query network
        if (lesson.isAttendanceMarked) {
            return true
        }

        val targetCity = lesson.branchId.ifEmpty { "430" }
        val sessionResult = ensureCityActive(targetCity)
        var activeSession = sessionResult.getOrNull() ?: sessionManager.getSession() ?: relogin().getOrNull()
            ?: return lesson.isAttendanceMarked

        RepoLog.i("OmniRepo", "Checking attendance for ${lesson.date} lenta=${lesson.lentaNum} group=${lesson.group} branch=${lesson.branchName} (city=$targetCity)")

        var res = apiClient.fetchPresents(
            cookieStr = activeSession.cookieStr,
            idLocalHash = activeSession.idLocalHash,
            lenta = lesson.lentaNum,
            group = lesson.group,
            date = lesson.date
        )

        if (res.isFailure && res.exceptionOrNull() is OmniApiClient.SessionExpiredException) {
            val refreshed = relogin().getOrNull()
            if (refreshed != null) {
                activeSession = ensureCityActive(targetCity).getOrNull() ?: refreshed
                res = apiClient.fetchPresents(
                    cookieStr = activeSession.cookieStr,
                    idLocalHash = activeSession.idLocalHash,
                    lenta = lesson.lentaNum,
                    group = lesson.group,
                    date = lesson.date
                )
            }
        }

        if (res.isSuccess) {
            val raw = res.getOrThrow()
            val parsedResult = OmniScheduleParser.parseAttendanceStatus(raw)
            if (parsedResult != null) {
                RepoLog.i("OmniRepo", "Lesson ${lesson.id} marked: $parsedResult (raw length=${raw.length})")
                db.lessonDao().updateAttendanceStatus(lesson.id, parsedResult)
                return parsedResult
            } else {
                RepoLog.d("OmniRepo", "Lesson ${lesson.id} inconclusive attendance response, retaining ${lesson.isAttendanceMarked}")
                return lesson.isAttendanceMarked
            }
        } else {
            RepoLog.w("OmniRepo", "fetchPresents failed for ${lesson.id}: ${res.exceptionOrNull()?.message}")
        }
        return lesson.isAttendanceMarked
    }

    suspend fun syncAttendanceForDay(dayOfWeek: Int, weekOffset: Int) {
        val weekDates = OmniScheduleParser.getWeekDates(weekOffset)
        if (dayOfWeek !in 1..7) return
        val targetDate = weekDates[dayOfWeek - 1].format(isoFormatter)
        val tomorrowIso = java.time.LocalDate.now().plusDays(1).format(isoFormatter)

        // Only skip strictly future days (tomorrow+); today may span multiple branch timezones
        if (targetDate > tomorrowIso) return

        val lessons = db.lessonDao().getLessonsForDateSync(targetDate)
        val nowInstant = java.time.Instant.now()

        RepoLog.i("OmniRepo", "syncAttendanceForDay: checking ${lessons.size} lessons on $targetDate (day=$dayOfWeek)")

        for (lEntity in lessons) {
            val lesson = lEntity.toDomain()
            val endInstant = ru.topacademy.omni.domain.util.BranchTimeZoneResolver.lessonEndInstant(lesson)
            val isPast = if (endInstant != null) {
                nowInstant.isAfter(endInstant)
            } else {
                targetDate < java.time.LocalDate.now().format(isoFormatter)
            }

            if (isPast) {
                checkLessonAttendance(lesson)
            }
        }
    }

    suspend fun syncAttendanceForPastWeekLessons(weekOffset: Int) {
        val weekDates = OmniScheduleParser.getWeekDates(weekOffset)
        val todayIso = java.time.LocalDate.now().format(isoFormatter)

        for (d in weekDates) {
            val dateStr = d.format(isoFormatter)
            if (dateStr <= todayIso) {
                syncAttendanceForDay(d.dayOfWeek.value, weekOffset)
            }
        }
    }

    suspend fun syncAttendanceForToday() {
        val todayDow = java.time.LocalDate.now().dayOfWeek.value
        syncAttendanceForDay(todayDow, 0)
    }
}
