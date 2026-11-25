package ru.topacademy.omni.domain.util

import ru.topacademy.omni.domain.model.Lesson
import ru.topacademy.omni.domain.model.LessonStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object LessonStateCalculator {

    fun determineStatus(
        lesson: Lesson,
        todayLessons: List<Lesson>,
        nowInstant: Instant = Instant.now()
    ): LessonStatus {
        val branchZone = BranchTimeZoneResolver.resolveZoneId(lesson.branchName)
        val branchToday = nowInstant.atZone(branchZone).toLocalDate()

        val lessonDate = try {
            LocalDate.parse(lesson.date, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) {
            return LessonStatus.SCHEDULED
        }

        // Past days in branch's local calendar
        if (lessonDate.isBefore(branchToday)) {
            return if (lesson.isAttendanceMarked) LessonStatus.PAST_MARKED else LessonStatus.PAST_UNMARKED
        }

        // Future days in branch's local calendar
        if (lessonDate.isAfter(branchToday)) {
            return LessonStatus.SCHEDULED
        }

        // Today in branch's local timezone: compare exact Instants
        val startInstant = BranchTimeZoneResolver.lessonStartInstant(lesson)
        val endInstant = BranchTimeZoneResolver.lessonEndInstant(lesson)

        if (startInstant != null && endInstant != null) {
            if (!nowInstant.isBefore(startInstant) && !nowInstant.isAfter(endInstant)) {
                return LessonStatus.LIVE_ACTIVE
            }
            if (nowInstant.isAfter(endInstant)) {
                return if (lesson.isAttendanceMarked) LessonStatus.PAST_MARKED else LessonStatus.PAST_UNMARKED
            }
        }

        // Find the chronologically earliest upcoming lesson across all branches by Instant
        val nextUpcoming = todayLessons
            .mapNotNull { candidate ->
                val cStart = BranchTimeZoneResolver.lessonStartInstant(candidate) ?: return@mapNotNull null
                if (cStart.isAfter(nowInstant)) candidate to cStart else null
            }
            .minByOrNull { it.second }
            ?.first

        if (nextUpcoming?.id == lesson.id) {
            return LessonStatus.NEXT_UPCOMING
        }

        return LessonStatus.SCHEDULED
    }

    fun determineStatus(
        lesson: Lesson,
        todayLessons: List<Lesson>,
        nowDate: LocalDate,
        nowTime: LocalTime
    ): LessonStatus {
        val instant = LocalDateTime.of(nowDate, nowTime).atZone(ZoneId.systemDefault()).toInstant()
        return determineStatus(lesson, todayLessons, instant)
    }
}
