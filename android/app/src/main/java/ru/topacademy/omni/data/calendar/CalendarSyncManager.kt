package ru.topacademy.omni.data.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import ru.topacademy.omni.data.db.OmniDatabase
import ru.topacademy.omni.data.parser.OmniScheduleParser
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CalendarSyncManager(
    private val context: Context,
    private val provider: CalendarSyncProvider = DeviceCalendarProvider(context)
) {
    private val prefs = context.getSharedPreferences("omni_calendar_prefs", Context.MODE_PRIVATE)
    private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun getSelectedCalendarId(): Long = prefs.getLong(KEY_CALENDAR_ID, -1L)

    fun getLastSyncedCount(): Int = prefs.getInt(KEY_LAST_COUNT, 0)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun setSelectedCalendarId(calendarId: Long) {
        prefs.edit().putLong(KEY_CALENDAR_ID, calendarId).apply()
    }

    fun hasCalendarPermission(): Boolean {
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        return read && write
    }

    suspend fun getAvailableCalendars(): List<DeviceCalendar> {
        if (!hasCalendarPermission()) return emptyList()
        return provider.getAvailableCalendars()
    }

    suspend fun ensureSelectedCalendar(): Long? {
        val calendars = getAvailableCalendars()
        if (calendars.isEmpty()) return null
        val savedId = getSelectedCalendarId()
        val match = calendars.firstOrNull { it.id == savedId }
        val chosen = match ?: calendars.first()
        if (chosen.id != savedId) {
            setSelectedCalendarId(chosen.id)
        }
        return chosen.id
    }

    /**
     * Synchronizes current week and next week from local Room DB into the chosen calendar.
     */
    suspend fun syncFromDb(db: OmniDatabase): Result<Int> {
        if (!isEnabled()) return Result.success(0)
        if (!hasCalendarPermission()) return Result.failure(Exception("Нет разрешения на доступ к календарю"))

        val calendarId = ensureSelectedCalendar()
            ?: return Result.failure(Exception("На устройстве не найдено доступных календарей"))

        val currentWeekDates = OmniScheduleParser.getWeekDates(0)
        val nextWeekDates = OmniScheduleParser.getWeekDates(1)
        val startIso = currentWeekDates.first().format(isoFormatter)
        val endIso = nextWeekDates.last().format(isoFormatter)

        val entities = db.lessonDao().getLessonsForWeekSync(startIso, endIso)
        val lessons = entities.map { it.toDomain() }

        val windowStart = currentWeekDates.first().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val windowEnd = nextWeekDates.last().plusDays(2).atStartOfDay(ZoneId.systemDefault()).toInstant()

        val result = provider.syncLessons(calendarId, lessons, windowStart, windowEnd)
        if (result.isSuccess) {
            prefs.edit()
                .putInt(KEY_LAST_COUNT, result.getOrDefault(0))
                .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())
                .apply()
        }
        return result
    }

    suspend fun clearAll(): Result<Int> {
        if (!hasCalendarPermission()) return Result.success(0)
        val res = provider.clearSyncedEvents(null)
        if (res.isSuccess) {
            prefs.edit().putInt(KEY_LAST_COUNT, 0).apply()
        }
        return res
    }

    suspend fun onLogout() {
        clearAll()
        setEnabled(false)
    }

    companion object {
        private const val KEY_ENABLED = "key_calendar_sync_enabled"
        private const val KEY_CALENDAR_ID = "key_selected_calendar_id"
        private const val KEY_LAST_COUNT = "key_last_synced_count"
        private const val KEY_LAST_SYNC_AT = "key_last_sync_at"
    }
}
