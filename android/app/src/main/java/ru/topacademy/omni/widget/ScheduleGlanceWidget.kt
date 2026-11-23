package ru.topacademy.omni.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import ru.topacademy.omni.MainActivity
import ru.topacademy.omni.data.db.OmniDatabase
import ru.topacademy.omni.domain.model.Lesson
import ru.topacademy.omni.ui.screens.LessonDialogActivity
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

val LessonIdActionKey = ActionParameters.Key<String>("EXTRA_LESSON_ID")

class ScheduleGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = OmniDatabase.getInstance(context)
        val today = LocalDate.now()
        val todayIso = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val nowTime = LocalTime.now()

        // Fetch upcoming lessons starting today
        val allUpcomingEntities = db.lessonDao().getUpcomingLessonsSync(todayIso)
        val allLessons = allUpcomingEntities.map { it.toDomain() }

        // Find currently running lesson
        val activeLesson = allLessons.firstOrNull { lesson ->
            lesson.date == todayIso && isLessonActive(lesson, nowTime)
        }

        // Filter upcoming lessons that haven't ended yet
        val futureLessons = allLessons.filter { lesson ->
            if (lesson.id == activeLesson?.id) return@filter false
            if (lesson.date > todayIso) {
                true
            } else if (lesson.date == todayIso) {
                val end = parseTime(lesson.endTime)
                end != null && nowTime.isBefore(end)
            } else {
                false
            }
        }.sortedWith(compareBy({ it.date }, { it.startTime }))

        val candidateLessons = mutableListOf<Lesson>()
        if (activeLesson != null) {
            candidateLessons.add(activeLesson)
        }
        candidateLessons.addAll(futureLessons)

        provideContent {
            val size = LocalSize.current

            // 4x1 -> 1 lesson (compact height < 115dp)
            // 4x2 -> 2 lessons (115dp..215dp)  [launcher 4x2 height is ~186dp]
            // 4x3 -> 3 lessons (215dp+)        [launcher 4x3 height is ~279dp]
            val isCompact4x1 = (size.height < 115.dp)
            val maxCount = when {
                isCompact4x1 -> 1
                size.height < 215.dp -> 2
                else -> 3
            }

            val targetLessons = candidateLessons.take(maxCount)

            val bgWidget = Color(0xFF141926) // Deep modern glass-navy background
            val textMuted = Color(0xFF94A3B8)

            // Padding inside widget card
            val vPadding = if (isCompact4x1) 6.dp else 12.dp

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(bgWidget)
                    .cornerRadius(20.dp)
                    .padding(horizontal = 14.dp, vertical = vPadding)
                    .clickable(actionStartActivity<MainActivity>())
            ) {
                if (targetLessons.isEmpty()) {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Предстоящих пар пока нет",
                            style = TextStyle(
                                color = ColorProvider(textMuted),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                } else {
                    // Regular full-height Column with SpaceBetween distribution
                    Column(
                        modifier = GlanceModifier.fillMaxSize()
                    ) {
                        targetLessons.forEachIndexed { index, lesson ->
                            val isRunning = (lesson.id == activeLesson?.id)
                            val isNext = !isRunning && (index == 0 || (activeLesson != null && index == 1))
                            val dayLabel = getDayLabel(lesson.date, today, todayIso)

                            Box(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .defaultWeight()
                                    .clickable(
                                        actionStartActivity<LessonDialogActivity>(
                                            actionParametersOf(LessonIdActionKey to lesson.id)
                                        )
                                    ),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                TimelineLessonItem(
                                    lesson = lesson,
                                    isRunning = isRunning,
                                    isNext = isNext,
                                    dayLabel = dayLabel,
                                    isCompact = isCompact4x1
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getDayLabel(lessonDate: String, today: LocalDate, todayIso: String): String {
        return when (lessonDate) {
            todayIso -> "Сегодня"
            today.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) -> "Завтра"
            else -> {
                try {
                    val d = LocalDate.parse(lessonDate)
                    "${d.dayOfMonth}.${d.monthValue.toString().padStart(2, '0')}"
                } catch (_: Exception) {
                    lessonDate
                }
            }
        }
    }

    private fun isLessonActive(lesson: Lesson, nowTime: LocalTime): Boolean {
        val start = parseTime(lesson.startTime) ?: return false
        val end = parseTime(lesson.endTime) ?: return false
        return !nowTime.isBefore(start) && !nowTime.isAfter(end)
    }

    private fun parseTime(timeStr: String): LocalTime? {
        return try {
            val parts = timeStr.trim().split(":")
            if (parts.size >= 2) LocalTime.of(parts[0].toInt(), parts[1].toInt()) else null
        } catch (_: Exception) {
            null
        }
    }
}

@androidx.compose.runtime.Composable
private fun TimelineLessonItem(
    lesson: Lesson,
    isRunning: Boolean,
    isNext: Boolean,
    dayLabel: String,
    isCompact: Boolean
) {
    val textWhite = Color(0xFFF8FAFC)
    val textMuted = Color(0xFF94A3B8)
    val textSubtle = Color(0xFF64748B)

    // Dot indicators
    val greenActive = Color(0xFF10B981)
    val amberNext = Color(0xFFF59E0B)

    val dotColor = when {
        isRunning -> greenActive
        isNext -> amberNext
        else -> textSubtle
    }

    // Dynamic branch pill
    val branchLabel = lesson.branchName
        .replace("Барнаул", "")
        .trim()
        .ifEmpty { lesson.branchName }

    val rawBranchColor = try {
        Color(android.graphics.Color.parseColor(lesson.branchColor))
    } catch (_: Exception) {
        Color(0xFF3B82F6)
    }

    val tagBg = rawBranchColor.copy(alpha = 0.22f)
    val tagText = rawBranchColor

    val cleanRoom = lesson.room.replace(Regex("\\s*\\([^)]*\\)"), "").trim()

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = if (isCompact) 0.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Left Column: Day Label + Time
        Column(
            modifier = GlanceModifier.width(52.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Day badge: "Сегодня", "Завтра", or "31.10"
            Text(
                text = dayLabel,
                style = TextStyle(
                    color = ColorProvider(
                        when {
                            isRunning -> greenActive
                            isNext -> amberNext
                            else -> textSubtle
                        }
                    ),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            // Time start
            Text(
                text = lesson.startTime,
                style = TextStyle(
                    color = ColorProvider(textWhite),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            // Time end
            Text(
                text = "–${lesson.endTime}",
                style = TextStyle(
                    color = ColorProvider(textSubtle),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
            )
        }

        Spacer(modifier = GlanceModifier.width(6.dp))

        // 2. Timeline Dot
        Box(
            modifier = GlanceModifier
                .width(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = GlanceModifier
                    .size(if (isRunning || isNext) 9.dp else 6.dp)
                    .background(dotColor)
                    .cornerRadius(if (isRunning || isNext) 5.dp else 3.dp)
            ) {}
        }

        Spacer(modifier = GlanceModifier.width(8.dp))

        // 3. Right Lesson Content Card
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .padding(horizontal = 2.dp)
        ) {
            // Top Row: Branch / Location Pill above Subject
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .background(tagBg)
                        .cornerRadius(4.dp)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = branchLabel,
                        style = TextStyle(
                            color = ColorProvider(tagText),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(1.dp))

            // Main Row: Full width Subject Title
            Text(
                text = lesson.subject.ifEmpty { "Занятие" },
                maxLines = 1,
                modifier = GlanceModifier.fillMaxWidth(),
                style = TextStyle(
                    color = ColorProvider(textWhite),
                    fontSize = if (isCompact) 13.sp else 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = GlanceModifier.height(1.dp))

            // Bottom Row: Room / Group
            val roomGroupText = when {
                cleanRoom.isNotEmpty() && lesson.group.isNotEmpty() -> "$cleanRoom • ${lesson.group}"
                cleanRoom.isNotEmpty() -> cleanRoom
                lesson.group.isNotEmpty() -> lesson.group
                else -> lesson.branchName
            }
            Text(
                text = roomGroupText,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(textMuted),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal
                )
            )
        }
    }
}
