package ru.topacademy.omni.domain.model

data class Branch(
    val cityId: String,
    val cityName: String,
    val category: String, // "COLLEGE", "ACADEMY", "SCHOOL", "ONLINE"
    val colorHex: String
)

data class Lesson(
    val id: String,
    val date: String, // "YYYY-MM-DD"
    val dayOfWeek: Int, // 1..7 (1 = Mon)
    val lentaNum: Int,
    val startTime: String, // "09:40"
    val endTime: String, // "11:10"
    val subject: String,
    val room: String,
    val group: String,
    val branchId: String,
    val branchName: String,
    val branchCategory: String,
    val branchColor: String,
    val isAttendanceMarked: Boolean = false
)

enum class LessonStatus {
    LIVE_ACTIVE,   // Идёт пара (зеленый)
    NEXT_UPCOMING, // Следующая пара (янтарный / синий)
    SCHEDULED,     // Предстоящая пара
    PAST_MARKED,   // Завершена и отмечена (зеленая галочка / приглушенная)
    PAST_UNMARKED  // Завершена, но НЕ отмечена (янтарное предупреждение)
}

data class TeacherProfile(
    val id: String,
    val name: String,
    val email: String,
    val branches: List<Branch> = emptyList()
)

data class ReviewStudent(
    val name: String,
    val group: String,
    val subject: String,
    val deadline: String
)

data class TaskQueue(
    val newHomeworkCount: Int = 0,
    val pendingReviewsCount: Int = 0,
    val oldestHwWaitDays: Int = 0,
    val oldestReviewWaitDays: Int = 0,
    val oldestHwWaitHours: Int = 0,
    val reviewStudents: List<ReviewStudent> = emptyList()
)

data class DaySchedule(
    val date: String,
    val dayOfWeek: Int,
    val lessons: List<Lesson> = emptyList()
)

data class WeekSchedule(
    val weekOffset: Int,
    val weekRangeLabel: String,
    val days: List<DaySchedule> = emptyList()
)
