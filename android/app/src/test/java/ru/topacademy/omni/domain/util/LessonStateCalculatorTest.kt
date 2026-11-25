package ru.topacademy.omni.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.topacademy.omni.domain.model.Lesson
import ru.topacademy.omni.domain.model.LessonStatus
import java.time.Instant
import java.time.ZoneId

class LessonStateCalculatorTest {

    private fun makeLesson(
        id: String,
        branchName: String,
        startTime: String = "10:00",
        endTime: String = "11:20",
        date: String = "2026-11-25",
        marked: Boolean = false
    ) = Lesson(
        id = id,
        date = date,
        dayOfWeek = 3,
        lentaNum = 1,
        startTime = startTime,
        endTime = endTime,
        subject = "Тест",
        room = "а.1",
        group = "ГР-1",
        branchId = id,
        branchName = branchName,
        branchCategory = "ACADEMY",
        branchColor = "#3B82F6",
        isAttendanceMarked = marked
    )

    @Test
    fun `resolves Barnaul and Yaroslavl timezones`() {
        assertEquals(ZoneId.of("Asia/Barnaul"), BranchTimeZoneResolver.resolveZoneId("Барнаул Колледж"))
        assertEquals(ZoneId.of("Europe/Moscow"), BranchTimeZoneResolver.resolveZoneId("Ярославль"))
    }

    @Test
    fun `same wall-clock lesson is past in Barnaul but upcoming in Yaroslavl`() {
        // 11:36 Barnaul (UTC+7) == 07:36 Yaroslavl (UTC+3) == 04:36 UTC
        val nowInstant = Instant.parse("2026-11-25T04:36:00Z")

        val barnaulLesson = makeLesson(id = "barnaul-1", branchName = "Барнаул")
        val yaroslavlLesson = makeLesson(id = "yaroslavl-1", branchName = "Ярославль")
        val lessons = listOf(barnaulLesson, yaroslavlLesson)

        assertEquals(
            LessonStatus.PAST_UNMARKED,
            LessonStateCalculator.determineStatus(barnaulLesson, lessons, nowInstant)
        )
        assertEquals(
            LessonStatus.NEXT_UPCOMING,
            LessonStateCalculator.determineStatus(yaroslavlLesson, lessons, nowInstant)
        )
    }

    @Test
    fun `active lesson in Yaroslavl at 10_30 Moscow time is LIVE_ACTIVE`() {
        // 10:30 Moscow (UTC+3) == 07:30 UTC (14:30 Barnaul)
        val nowInstant = Instant.parse("2026-11-25T07:30:00Z")
        val yaroslavlLesson = makeLesson(id = "yaroslavl-1", branchName = "Ярославль")

        assertEquals(
            LessonStatus.LIVE_ACTIVE,
            LessonStateCalculator.determineStatus(yaroslavlLesson, listOf(yaroslavlLesson), nowInstant)
        )
    }
}
