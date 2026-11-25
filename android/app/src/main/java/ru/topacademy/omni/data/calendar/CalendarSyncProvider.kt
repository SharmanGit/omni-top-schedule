package ru.topacademy.omni.data.calendar

import ru.topacademy.omni.domain.model.Lesson
import java.time.Instant

data class DeviceCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val isPrimary: Boolean,
    val isGoogle: Boolean
)

/**
 * Abstraction for calendar synchronization backends.
 * Currently backed by [DeviceCalendarProvider] (Android CalendarContract),
 * and structured to allow adding a direct Google Calendar REST API provider later.
 */
interface CalendarSyncProvider {
    suspend fun getAvailableCalendars(): List<DeviceCalendar>

    suspend fun syncLessons(
        calendarId: Long,
        lessons: List<Lesson>,
        windowStart: Instant,
        windowEnd: Instant
    ): Result<Int>

    suspend fun clearSyncedEvents(calendarId: Long? = null): Result<Int>
}
