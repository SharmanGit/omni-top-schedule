package ru.topacademy.omni.data.parser

import org.json.JSONObject
import ru.topacademy.omni.domain.model.Branch
import ru.topacademy.omni.domain.model.DaySchedule
import ru.topacademy.omni.domain.model.Lesson
import ru.topacademy.omni.domain.model.ReviewStudent
import ru.topacademy.omni.domain.model.TaskQueue
import ru.topacademy.omni.domain.model.WeekSchedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

object OmniScheduleParser {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val displayDateFormatter = DateTimeFormatter.ofPattern("dd.MM")

    fun getWeekDates(weekOffset: Int): List<LocalDate> {
        val now = LocalDate.now()
        val monday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(weekOffset.toLong())
        return (0..6).map { monday.plusDays(it.toLong()) }
    }

    fun getWeekRangeLabel(weekOffset: Int): String {
        val dates = getWeekDates(weekOffset)
        val start = dates.first().format(displayDateFormatter)
        val end = dates.last().format(displayDateFormatter)
        return "$start – $end"
    }

    fun classifyBranch(cityName: String, idForm: String? = null): String {
        val lower = cityName.lowercase()
        return when {
            lower.contains("колледж") || idForm == "430" || idForm == "2" -> "COLLEGE"
            lower.contains("школа") || idForm == "607" -> "SCHOOL"
            lower.contains("онлайн") || lower.contains("online") -> "ONLINE"
            else -> "ACADEMY"
        }
    }

    fun getBranchColor(cityName: String, category: String): String {
        return when (category) {
            "COLLEGE" -> "#3b82f6" // blue
            "SCHOOL" -> "#10b981"  // emerald
            "ONLINE" -> "#8b5cf6"  // purple
            else -> "#f59e0b"      // amber / gold
        }
    }

    fun parseSchedule(
        rawJsonStr: String,
        branch: Branch,
        weekOffset: Int
    ): List<Lesson> {
        val lessons = mutableListOf<Lesson>()
        try {
            val root = JSONObject(rawJsonStr)
            val body = root.optJSONObject("body") ?: return emptyList()
            val weekDates = getWeekDates(weekOffset)

            val lentaKeys = body.keys()
            while (lentaKeys.hasNext()) {
                val lentaKey = lentaKeys.next()
                val dayObj = body.optJSONObject(lentaKey) ?: continue
                val dayKeys = dayObj.keys()

                while (dayKeys.hasNext()) {
                    val dayKey = dayKeys.next()
                    val lessonObj = dayObj.optJSONObject(dayKey) ?: continue

                    val weekdayNum = dayKey.toIntOrNull() ?: 1
                    if (weekdayNum !in 1..7) continue
                    val dateIso = weekDates[weekdayNum - 1].format(dateFormatter)

                    val lentaNum = lessonObj.optString("lenta", lentaKey).toIntOrNull() ?: 1
                    val rawSubject = cleanJsonString(lessonObj, "name_spec")
                    val eventName = cleanJsonString(lessonObj, "eventName")
                    val eventSubtypeName = cleanJsonString(lessonObj, "eventSubtypeName")
                    val eventTitle = when {
                        eventName.isNotEmpty() && eventSubtypeName.isNotEmpty() -> "$eventName / $eventSubtypeName"
                        eventName.isNotEmpty() -> eventName
                        eventSubtypeName.isNotEmpty() -> eventSubtypeName
                        else -> ""
                    }
                    val subject = when {
                        rawSubject.isNotEmpty() -> rawSubject
                        eventTitle.isNotEmpty() -> eventTitle
                        else -> "Занятие"
                    }

                    val group = cleanJsonString(lessonObj, "groups")
                    val room = cleanJsonString(lessonObj, "num_rooms")
                    val startTime = cleanJsonString(lessonObj, "l_start")
                    val endTime = cleanJsonString(lessonObj, "l_end")
                    val idForm = cleanJsonString(lessonObj, "id_form")

                    if (rawSubject.isNotEmpty() || eventTitle.isNotEmpty() || group.isNotEmpty() || (startTime.isNotEmpty() && room.isNotEmpty())) {
                        val category = classifyBranch(branch.cityName, idForm)
                        val color = branch.colorHex.ifEmpty { getBranchColor(branch.cityName, category) }

                        val roomDisplay = if (room.isNotEmpty() && !room.startsWith("ауд", ignoreCase = true)) {
                            "ауд. $room"
                        } else room

                        lessons.add(
                            Lesson(
                                id = "${dateIso}_${lentaNum}_${branch.cityId}_${group}_${subject.hashCode()}",
                                date = dateIso,
                                dayOfWeek = weekdayNum,
                                lentaNum = lentaNum,
                                startTime = startTime,
                                endTime = endTime,
                                subject = subject,
                                room = roomDisplay,
                                group = group,
                                branchId = branch.cityId,
                                branchName = branch.cityName,
                                branchCategory = category,
                                branchColor = color
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // parsing error or empty json
        }
        return lessons
    }

    fun buildWeekSchedule(
        allLessons: List<Lesson>,
        weekOffset: Int
    ): WeekSchedule {
        val weekDates = getWeekDates(weekOffset)
        val days = (1..7).map { weekdayNum ->
            val dateIso = weekDates[weekdayNum - 1].format(dateFormatter)
            val dayLessons = allLessons
                .filter { it.date == dateIso }
                .sortedWith(compareBy({ it.lentaNum }, { it.startTime }))
            DaySchedule(
                date = dateIso,
                dayOfWeek = weekdayNum,
                lessons = dayLessons
            )
        }
        return WeekSchedule(
            weekOffset = weekOffset,
            weekRangeLabel = getWeekRangeLabel(weekOffset),
            days = days
        )
    }

    fun getInitialWeekSchedule(weekOffset: Int): WeekSchedule {
        val weekDates = getWeekDates(weekOffset)
        val days = (1..7).map { weekdayNum ->
            DaySchedule(
                date = weekDates[weekdayNum - 1].format(dateFormatter),
                dayOfWeek = weekdayNum,
                lessons = emptyList()
            )
        }
        return WeekSchedule(
            weekOffset = weekOffset,
            weekRangeLabel = getWeekRangeLabel(weekOffset),
            days = days
        )
    }

    fun parseTasks(rawJsonStr: String): TaskQueue {
        return try {
            val root = JSONObject(rawJsonStr)
            val dzStud = root.optJSONObject("dzStud")
            val reviewsStud = root.optJSONObject("reviewsStud")

            val newHw = dzStud?.optInt("new_home_work", 0) ?: 0
            val pb = dzStud?.optJSONObject("progress_bar")
            val factHours = pb?.optInt("factHours", 0) ?: 0
            val factDays = pb?.optInt("factDays", 0) ?: 0

            // In Omni TOP, factDays often mirrors factHours (e.g. 3 hours -> factDays = 3).
            // Real waiting days is factHours / 24 when >= 24.
            val actualHwDays = if (factHours >= 24) factHours / 24 else 0
            val actualHwHours = if (factHours in 1..23) factHours else if (factDays in 1..23 && factHours == 0) factDays else 0

            val countReviews = reviewsStud?.optInt("count_students", 0) ?: 0
            val reviewDays = reviewsStud?.optJSONObject("progress_bar")?.optInt("factDays", 0) ?: 0

            val studentsList = mutableListOf<ReviewStudent>()
            val studentsArr = reviewsStud?.optJSONArray("students_list")
            if (studentsArr != null) {
                for (i in 0 until studentsArr.length()) {
                    val s = studentsArr.optJSONObject(i) ?: continue
                    val name = cleanJsonString(s, "fio_stud").ifEmpty { cleanJsonString(s, "fio_student") }.ifEmpty { cleanJsonString(s, "name") }.ifEmpty { "Студент" }
                    val group = cleanJsonString(s, "name_tgroups").ifEmpty { cleanJsonString(s, "group_name") }.ifEmpty { cleanJsonString(s, "group") }
                    val subject = cleanJsonString(s, "name_spec").ifEmpty { cleanJsonString(s, "spec_name") }.ifEmpty { cleanJsonString(s, "subject") }
                    val deadline = cleanJsonString(s, "due_date").ifEmpty { cleanJsonString(s, "date_limit") }.ifEmpty { cleanJsonString(s, "date_vizit") }
                    studentsList.add(ReviewStudent(name = name, group = group, subject = subject, deadline = deadline))
                }
            }

            TaskQueue(
                newHomeworkCount = newHw,
                pendingReviewsCount = countReviews,
                oldestHwWaitDays = actualHwDays,
                oldestReviewWaitDays = reviewDays,
                oldestHwWaitHours = actualHwHours,
                reviewStudents = studentsList
            )
        } catch (_: Exception) {
            TaskQueue()
        }
    }

    fun parseAttendanceStatus(rawJsonStr: String): Boolean? {
        return try {
            val root = JSONObject(rawJsonStr)

            val studentsArr = root.optJSONArray("students")
            val scheduleArr = root.optJSONArray("schedule")
            val groupsObj = root.optJSONObject("groups")
            val vizitInfo = root.optJSONObject("vizit_info")
                ?: root.optJSONObject("data")?.optJSONObject("vizit_info")
                ?: studentsArr?.optJSONObject(0)

            // If empty response (e.g. schedule: [], no students, no groups, no vizit_info), return null (inconclusive)
            if ((studentsArr == null || studentsArr.length() == 0) &&
                (scheduleArr == null || scheduleArr.length() == 0) &&
                groupsObj == null &&
                vizitInfo == null
            ) {
                return null
            }

            val primaryTeach = vizitInfo?.optString("primary_teach", "")
                ?: root.optString("primary_teach", "")
            val isPrimaryTeacher = primaryTeach == "0" ||
                    (vizitInfo != null && vizitInfo.optInt("primary_teach", -1) == 0) ||
                    root.optInt("primary_teach", -1) == 0

            val theme = (vizitInfo?.optString("theme", "") ?: "")
                .ifBlank { vizitInfo?.optString("setting_theme", "") ?: "" }
                .ifBlank { root.optString("theme", "") }
                .ifBlank { root.optString("setting_theme", "") }
                .trim()

            val hasVisitId = (vizitInfo != null && vizitInfo.optInt("id_vizit", 0) > 0) ||
                    (studentsArr != null && studentsArr.length() > 0 && studentsArr.optJSONObject(0)?.optInt("id_vizit", 0) ?: 0 > 0)

            if (isPrimaryTeacher && theme.isNotBlank()) {
                true
            } else if (hasVisitId && theme.isNotBlank()) {
                true
            } else {
                false
            }
        } catch (_: Exception) {
            null
        }
    }

    fun isPresentsMarked(rawJsonStr: String): Boolean {
        return parseAttendanceStatus(rawJsonStr) ?: false
    }

    private fun cleanJsonString(obj: org.json.JSONObject, key: String): String {
        if (obj.isNull(key)) return ""
        val v = obj.optString(key, "").trim()
        return if (v.equals("null", ignoreCase = true)) "" else v
    }
}
