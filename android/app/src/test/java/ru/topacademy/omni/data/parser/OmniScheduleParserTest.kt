package ru.topacademy.omni.data.parser

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.topacademy.omni.domain.model.Branch

class OmniScheduleParserTest {

    @Test
    fun `classifyBranch detects college school online and academy`() {
        assertEquals("COLLEGE", OmniScheduleParser.classifyBranch("Барнаул Колледж"))
        assertEquals("SCHOOL", OmniScheduleParser.classifyBranch("Барнаул Школа"))
        assertEquals("ONLINE", OmniScheduleParser.classifyBranch("Онлайн"))
        assertEquals("ACADEMY", OmniScheduleParser.classifyBranch("Ярославль"))
    }

    @Test
    fun `getWeekDates returns 7 consecutive days starting Monday`() {
        val dates = OmniScheduleParser.getWeekDates(0)
        assertEquals(7, dates.size)
        assertEquals(java.time.DayOfWeek.MONDAY, dates.first().dayOfWeek)
        assertEquals(java.time.DayOfWeek.SUNDAY, dates.last().dayOfWeek)
    }

    @Test
    fun `parseSchedule parses lessons with branch metadata`() {
        val json = """
            {
              "body": {
                "1": {
                  "3": {
                    "lenta": "1",
                    "l_start": "10:00",
                    "l_end": "11:20",
                    "name_spec": "Kotlin",
                    "num_rooms": "4",
                    "groups": "1311"
                  }
                }
              }
            }
        """.trimIndent()
        val branch = Branch("95", "Ярославль", "ACADEMY", "#f59e0b")

        val lessons = OmniScheduleParser.parseSchedule(json, branch, 0)
        assertEquals(1, lessons.size)
        assertEquals("Kotlin", lessons[0].subject)
        assertEquals("10:00", lessons[0].startTime)
        assertEquals("11:20", lessons[0].endTime)
        assertEquals("Ярославль", lessons[0].branchName)
        assertEquals(3, lessons[0].dayOfWeek)
    }
}
