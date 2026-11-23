package ru.topacademy.omni.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.topacademy.omni.domain.model.TaskQueue
import ru.topacademy.omni.ui.theme.BlueAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksBottomSheet(
    taskQueue: TaskQueue,
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
            Text(
                text = "Очередь проверки преподавателя",
                color = colorScheme.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Summary Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Homeworks Card
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorScheme.surfaceVariant)
                        .border(1.dp, colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "${taskQueue.newHomeworkCount}",
                        color = BlueAccent,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Новых ДЗ",
                        color = colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (taskQueue.newHomeworkCount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val waitText = when {
                            taskQueue.oldestHwWaitDays > 0 -> "Самое старое: ${taskQueue.oldestHwWaitDays} дн."
                            taskQueue.oldestHwWaitHours > 0 -> "Самое старое: ${taskQueue.oldestHwWaitHours} ч."
                            else -> "Самое старое: < 1 ч."
                        }
                        Text(
                            text = waitText,
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                // Reviews Card
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorScheme.surfaceVariant)
                        .border(1.dp, colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "${taskQueue.pendingReviewsCount}",
                        color = BlueAccent,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Отзывов студентов",
                        color = colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    val earliestDateIso = taskQueue.reviewStudents
                        .map { it.deadline.trim() }
                        .filter { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
                        .sorted()
                        .firstOrNull()

                    val nearestDeadlineDisplay = if (earliestDateIso != null) {
                        val parts = earliestDateIso.split("-")
                        if (parts.size == 3) "${parts[2]}.${parts[1]}" else earliestDateIso
                    } else null

                    if (nearestDeadlineDisplay != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Дедлайн: $nearestDeadlineDisplay",
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    } else if (taskQueue.oldestReviewWaitDays > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ожидает: ${taskQueue.oldestReviewWaitDays} дн.",
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
