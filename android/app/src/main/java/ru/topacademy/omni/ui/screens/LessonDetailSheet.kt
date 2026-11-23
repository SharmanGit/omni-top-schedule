package ru.topacademy.omni.ui.screens

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import ru.topacademy.omni.ui.theme.AmberLight
import ru.topacademy.omni.ui.theme.AmberPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonDetailSheet(
    lesson: Lesson,
    status: LessonStatus,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val colorScheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header row with Lenta Num & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Пара №${lesson.lentaNum}",
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                // Attendance / Status badge
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
                                text = "ИДЁТ СЕЙЧАС",
                                color = ActiveGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    LessonStatus.NEXT_UPCOMING -> {
                        Text(
                            text = "СЛЕДУЮЩАЯ",
                            color = AmberPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    else -> {
                        Text(
                            text = "${lesson.startTime} – ${lesson.endTime}",
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Subject
            Text(
                text = lesson.subject.ifEmpty { "Занятие" },
                color = colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Details rows
            DetailRow(label = "Время", value = "${lesson.startTime} – ${lesson.endTime}")
            if (lesson.group.isNotEmpty()) {
                DetailRow(label = "Группа", value = lesson.group)
            }
            DetailRow(label = "Аудитория", value = lesson.room)
            DetailRow(label = "Филиал", value = lesson.branchName)
            DetailRow(label = "Дата", value = lesson.date)

            val attendanceLabel = if (lesson.isAttendanceMarked) "Отмечена в журнале" else "Не отмечена"
            val attendanceColor = if (lesson.isAttendanceMarked) Color(0xFF16A34A) else Color(0xFFD97706)
            DetailRow(label = "Посещаемость", value = attendanceLabel, valueColor = attendanceColor)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
