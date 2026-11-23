package ru.topacademy.omni.domain.util

import ru.topacademy.omni.domain.model.Lesson
import ru.topacademy.omni.domain.model.LessonStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object LessonStateCalculator {

    fun determineStatus(
        lesson: Lesson,
        todayLessons: List<Lesson>,
        nowDate: LocalDate = LocalDate.now(),
        nowTime: LocalTime = LocalTime.now()
    ): LessonStatus {
        val lessonDate = try {
            LocalDate.parse(lesson.date, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) {
            return LessonStatus.SCHEDULED
        }

        // Past days
        if (lessonDate.isBefore(nowDate)) {
            return if (lesson.isAttendanceMarked) LessonStatus.PAST_MARKED else LessonStatus.PAST_UNMARKED
        }

        // Future days
        if (lessonDate.isAfter(nowDate)) {
            return LessonStatus.SCHEDULED
        }

        // Today
        val start = parseTime(lesson.startTime)
        val end = parseTime(lesson.endTime)

        if (start != null && end != null) {
            if (!nowTime.isBefore(start) && !nowTime.isAfter(end)) {
                return LessonStatus.LIVE_ACTIVE
            }
            if (nowTime.isAfter(end)) {
                return if (lesson.isAttendanceMarked) LessonStatus.PAST_MARKED else LessonStatus.PAST_UNMARKED
            }
        }

        // Check if this is the next upcoming lesson today
        val upcomingToday = todayLessons
            .filter { parseTime(it.startTime)?.isAfter(nowTime) == true }
            .sortedBy { parseTime(it.startTime) }

        if (upcomingToday.firstOrNull()?.id == lesson.id) {
            return LessonStatus.NEXT_UPCOMING
        }

        return LessonStatus.SCHEDULED
    }

    private fun parseTime(timeStr: String): LocalTime? {
        return try {
            val parts = timeStr.trim().split(":")
            if (parts.size >= 2) {
                LocalTime.of(parts[0].toInt(), parts[1].toInt())
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
