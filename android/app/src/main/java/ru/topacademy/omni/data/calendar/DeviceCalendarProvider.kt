package ru.topacademy.omni.data.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.topacademy.omni.domain.model.Lesson
import ru.topacademy.omni.domain.util.BranchTimeZoneResolver
import java.time.Instant

class DeviceCalendarProvider(private val context: Context) : CalendarSyncProvider {

    companion object {
        private const val OMNI_TAG_PREFIX = "[omni-id:"
        private const val OMNI_TAG_SUFFIX = "]"
    }

    override suspend fun getAvailableCalendars(): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        val result = mutableListOf<DeviceCalendar>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val selectionArgs = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())

        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
                val typeIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)
                val ownerIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.OWNER_ACCOUNT)
                val primaryIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.IS_PRIMARY)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val displayName = cursor.getString(nameIdx) ?: "Календарь"
                    val accountName = cursor.getString(accIdx) ?: ""
                    val accountType = cursor.getString(typeIdx) ?: ""
                    val ownerAccount = cursor.getString(ownerIdx) ?: ""
                    val isPrimary = cursor.getInt(primaryIdx) == 1 ||
                        (accountName.isNotEmpty() && accountName.equals(ownerAccount, ignoreCase = true))
                    val isGoogle = accountType.equals("com.google", ignoreCase = true)

                    result.add(
                        DeviceCalendar(
                            id = id,
                            displayName = displayName,
                            accountName = accountName,
                            isPrimary = isPrimary,
                            isGoogle = isGoogle
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            return@withContext emptyList()
        } catch (_: Exception) {
            return@withContext emptyList()
        }

        result.sortedWith(
            compareByDescending<DeviceCalendar> { it.isGoogle }
                .thenByDescending { it.isPrimary }
                .thenBy { it.displayName }
        )
    }

    override suspend fun syncLessons(
        calendarId: Long,
        lessons: List<Lesson>,
        windowStart: Instant,
        windowEnd: Instant
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val windowStartMs = windowStart.toEpochMilli()
            val windowEndMs = windowEnd.toEpochMilli()

            // 1. Load existing Omni-tagged events in this calendar within the sync window
            data class ExistingEvent(
                val eventId: Long,
                val omniId: String,
                val title: String,
                val location: String,
                val description: String,
                val dtStart: Long,
                val dtEnd: Long,
                val timeZone: String
            )

            val existingByOmniId = mutableMapOf<String, ExistingEvent>()
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.EVENT_TIMEZONE
            )
            val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND " +
                "${CalendarContract.Events.DELETED} = 0 AND " +
                "${CalendarContract.Events.DTSTART} >= ? AND " +
                "${CalendarContract.Events.DTSTART} <= ? AND " +
                "${CalendarContract.Events.DESCRIPTION} LIKE ?"
            val selectionArgs = arrayOf(
                calendarId.toString(),
                windowStartMs.toString(),
                windowEndMs.toString(),
                "%$OMNI_TAG_PREFIX%"
            )

            resolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
                val titleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val locIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
                val descIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)
                val startIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                val endIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
                val tzIdx = cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_TIMEZONE)

                while (cursor.moveToNext()) {
                    val desc = cursor.getString(descIdx) ?: ""
                    val omniId = extractOmniId(desc) ?: continue
                    val eventId = cursor.getLong(idIdx)
                    // If duplicates exist for the same omniId, delete extras
                    if (existingByOmniId.containsKey(omniId)) {
                        val dupUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
                        resolver.delete(dupUri, null, null)
                        continue
                    }
                    existingByOmniId[omniId] = ExistingEvent(
                        eventId = eventId,
                        omniId = omniId,
                        title = cursor.getString(titleIdx) ?: "",
                        location = cursor.getString(locIdx) ?: "",
                        description = desc,
                        dtStart = cursor.getLong(startIdx),
                        dtEnd = cursor.getLong(endIdx),
                        timeZone = cursor.getString(tzIdx) ?: ""
                    )
                }
            }

            // 2. Insert or update desired lessons
            val seenOmniIds = mutableSetOf<String>()
            var syncedCount = 0

            for (lesson in lessons) {
                val startInstant = BranchTimeZoneResolver.lessonStartInstant(lesson) ?: continue
                val endInstant = BranchTimeZoneResolver.lessonEndInstant(lesson) ?: continue
                val startMs = startInstant.toEpochMilli()
                val endMs = endInstant.toEpochMilli()
                if (startMs < windowStartMs || startMs > windowEndMs) continue

                seenOmniIds.add(lesson.id)
                val title = buildTitle(lesson)
                val location = buildLocation(lesson)
                val description = buildDescription(lesson)
                val timeZone = BranchTimeZoneResolver.resolveZoneId(lesson.branchName).id

                val existing = existingByOmniId[lesson.id]
                if (existing == null) {
                    val values = ContentValues().apply {
                        put(CalendarContract.Events.CALENDAR_ID, calendarId)
                        put(CalendarContract.Events.TITLE, title)
                        put(CalendarContract.Events.EVENT_LOCATION, location)
                        put(CalendarContract.Events.DESCRIPTION, description)
                        put(CalendarContract.Events.DTSTART, startMs)
                        put(CalendarContract.Events.DTEND, endMs)
                        put(CalendarContract.Events.EVENT_TIMEZONE, timeZone)
                    }
                    resolver.insert(CalendarContract.Events.CONTENT_URI, values)
                    syncedCount++
                } else {
                    val changed = existing.title != title ||
                        existing.location != location ||
                        existing.description != description ||
                        existing.dtStart != startMs ||
                        existing.dtEnd != endMs ||
                        existing.timeZone != timeZone
                    if (changed) {
                        val values = ContentValues().apply {
                            put(CalendarContract.Events.TITLE, title)
                            put(CalendarContract.Events.EVENT_LOCATION, location)
                            put(CalendarContract.Events.DESCRIPTION, description)
                            put(CalendarContract.Events.DTSTART, startMs)
                            put(CalendarContract.Events.DTEND, endMs)
                            put(CalendarContract.Events.EVENT_TIMEZONE, timeZone)
                        }
                        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existing.eventId)
                        resolver.update(uri, values, null, null)
                    }
                    syncedCount++
                }
            }

            // 3. Remove events in this window that are no longer in the schedule
            for ((omniId, existing) in existingByOmniId) {
                if (!seenOmniIds.contains(omniId)) {
                    val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existing.eventId)
                    resolver.delete(uri, null, null)
                }
            }

            Result.success(syncedCount)
        } catch (e: SecurityException) {
            Result.failure(Exception("Нет доступа к календарю"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun clearSyncedEvents(calendarId: Long?): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val selection = if (calendarId != null && calendarId > 0) {
                "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DESCRIPTION} LIKE ?"
            } else {
                "${CalendarContract.Events.DESCRIPTION} LIKE ?"
            }
            val args = if (calendarId != null && calendarId > 0) {
                arrayOf(calendarId.toString(), "%$OMNI_TAG_PREFIX%")
            } else {
                arrayOf("%$OMNI_TAG_PREFIX%")
            }
            val deleted = resolver.delete(CalendarContract.Events.CONTENT_URI, selection, args)
            Result.success(deleted)
        } catch (e: SecurityException) {
            Result.failure(Exception("Нет доступа к календарю"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractOmniId(description: String): String? {
        val start = description.indexOf(OMNI_TAG_PREFIX)
        if (start == -1) return null
        val from = start + OMNI_TAG_PREFIX.length
        val end = description.indexOf(OMNI_TAG_SUFFIX, from)
        if (end == -1) return null
        return description.substring(from, end).trim().ifEmpty { null }
    }

    private fun buildTitle(lesson: Lesson): String {
        val subj = lesson.subject.ifBlank { "Занятие" }
        return if (lesson.group.isNotBlank()) "$subj (${lesson.group})" else subj
    }

    private fun buildLocation(lesson: Lesson): String {
        val cleanRoom = lesson.room.replace(Regex("\\s*\\([^)]*\\)"), "").trim()
        return listOf(cleanRoom, lesson.branchName)
            .filter { it.isNotBlank() && it != "—" }
            .joinToString(" • ")
    }

    private fun buildDescription(lesson: Lesson): String {
        val lines = mutableListOf<String>()
        if (lesson.group.isNotBlank()) lines.add("Группа: ${lesson.group}")
        if (lesson.room.isNotBlank() && lesson.room != "—") lines.add("Аудитория: ${lesson.room}")
        if (lesson.branchName.isNotBlank()) lines.add("Филиал: ${lesson.branchName}")
        lines.add("")
        lines.add("$OMNI_TAG_PREFIX${lesson.id}$OMNI_TAG_SUFFIX")
        return lines.joinToString("\n")
    }
}
