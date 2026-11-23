package ru.topacademy.omni.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.topacademy.omni.domain.model.Lesson
import ru.topacademy.omni.domain.model.LessonStatus
import ru.topacademy.omni.ui.theme.ActiveGreen
import ru.topacademy.omni.ui.theme.ActiveGreenBg
import ru.topacademy.omni.ui.theme.AmberDark
import ru.topacademy.omni.ui.theme.AmberLight
import ru.topacademy.omni.ui.theme.AmberPrimary
import ru.topacademy.omni.ui.theme.BlueAccent
import ru.topacademy.omni.ui.theme.BorderDark
import ru.topacademy.omni.ui.theme.CardDark
import ru.topacademy.omni.ui.theme.SurfaceDark
import ru.topacademy.omni.ui.theme.TextMuted
import ru.topacademy.omni.ui.theme.TextPrimary
import ru.topacademy.omni.ui.theme.TextSecondary

@Composable
fun LessonCard(
    lesson: Lesson,
    status: LessonStatus,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val branchColor = try {
        Color(android.graphics.Color.parseColor(lesson.branchColor))
    } catch (_: Exception) {
        if (isDark) AmberPrimary else BlueAccent
    }

    val colorScheme = androidx.compose.material3.MaterialTheme.colorScheme

        val cardBorder = when (status) {
        LessonStatus.LIVE_ACTIVE -> ActiveGreen
        LessonStatus.NEXT_UPCOMING -> if (isDark) AmberPrimary else BlueAccent
        else -> colorScheme.outline
    }

    val cardBg = when (status) {
        LessonStatus.LIVE_ACTIVE -> if (isDark) Color(0xFF0F261F) else Color(0xFFF0FDF4)
        LessonStatus.NEXT_UPCOMING -> if (isDark) Color(0xFF221A0F) else Color(0xFFEFF6FF)
        else -> colorScheme.surface
    }

    val borderThickness = when (status) {
        LessonStatus.LIVE_ACTIVE, LessonStatus.NEXT_UPCOMING -> 1.5.dp
        else -> 1.dp
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(borderThickness, cardBorder, RoundedCornerShape(14.dp))
            .background(cardBg)
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        // Top status & time row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status Tag
            when (status) {
                LessonStatus.LIVE_ACTIVE -> {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ActiveGreenBg)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(ActiveGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ИДЁТ ПАРА",
                            color = ActiveGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                LessonStatus.NEXT_UPCOMING -> {
                    val tagBg = if (isDark) Color(0xFF38250A) else Color(0xFFDBEAFE)
                    val tagTextColor = if (isDark) AmberLight else Color(0xFF1D4ED8)
                    Text(
                        text = "СЛЕДУЮЩАЯ",
                        color = tagTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(tagBg)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                else -> {
                    Text(
                        text = "Пара ${lesson.lentaNum}",
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Time
            val timeColor = when {
                status == LessonStatus.LIVE_ACTIVE -> ActiveGreen
                status == LessonStatus.NEXT_UPCOMING && !isDark -> Color(0xFF1D4ED8)
                else -> colorScheme.onSurfaceVariant
            }

            Text(
                text = "${lesson.startTime} – ${lesson.endTime}",
                color = timeColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Subject Title
        val titleColor = when {
            status == LessonStatus.LIVE_ACTIVE && isDark -> Color.White
            status == LessonStatus.NEXT_UPCOMING && isDark -> Color.White
            else -> colorScheme.onSurface
        }

        Text(
            text = lesson.subject.ifEmpty { "Занятие" },
            color = titleColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Bottom row: Group on Left, Room + Location on Right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Group badge (Left)
            if (lesson.group.isNotEmpty()) {
                Text(
                    text = lesson.group,
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Room with branch color dot (Right)
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(branchColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                val cleanRoom = lesson.room.replace(Regex("\\s*\\([^)]*\\)"), "").trim()
                Text(
                    text = "$cleanRoom • ${lesson.branchName}",
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}
