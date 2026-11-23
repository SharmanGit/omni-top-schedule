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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.topacademy.omni.domain.model.DaySchedule
import ru.topacademy.omni.ui.theme.AmberPrimary
import ru.topacademy.omni.ui.theme.BlueAccent
import java.time.LocalDate

@Composable
fun DayRibbon(
    days: List<DaySchedule>,
    selectedDayOfWeek: Int,
    weekOffset: Int,
    isDark: Boolean,
    selectedBranchIds: Set<String> = emptySet(),
    onDayClick: (Int) -> Unit
) {
    val dayNames = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")
    val todayDayOfWeek = LocalDate.now().dayOfWeek.value
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.background)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 1..7) {
            val daySchedule = days.find { it.dayOfWeek == i }
            val isSelected = selectedDayOfWeek == i
            val isToday = (weekOffset == 0 && i == todayDayOfWeek)
            val dayLessons = daySchedule?.lessons ?: emptyList()
            val filteredLessons = if (selectedBranchIds.isEmpty()) dayLessons else dayLessons.filter { selectedBranchIds.contains(it.branchId) }
            val lessonCount = filteredLessons.size

            val dateDisplay = daySchedule?.date?.let {
                val parts = it.split("-")
                if (parts.size == 3) "${parts[2]}.${parts[1]}" else ""
            } ?: ""

            // Background & Border colors
            val cardBg = when {
                isSelected && isToday -> if (isDark) Color(0xFF1D4ED8) else Color(0xFF1D4ED8)
                isSelected -> Color(0xFF2563EB)
                isToday -> if (isDark) colorScheme.surfaceVariant else Color(0xFFEFF6FF)
                else -> colorScheme.surface
            }

            val borderColor = when {
                isSelected && isToday -> if (isDark) Color(0xFFFBBF24) else Color(0xFF93C5FD)
                isSelected -> Color(0xFF3B82F6)
                isToday -> if (isDark) AmberPrimary else Color(0xFF2563EB)
                else -> colorScheme.outline
            }

            val borderWidth = if (isToday || isSelected) 2.dp else 1.dp
            val cardHeight = 84.dp

            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(cardHeight)
                    .clip(RoundedCornerShape(10.dp))
                    .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
                    .background(cardBg)
                    .clickable { onDayClick(i) }
                    .padding(vertical = 4.dp, horizontal = 1.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top tag: "СЕГОДНЯ" for today, else Spacer to maintain aligned baselines
                if (isToday) {
                    Text(
                        text = "СЕГОДНЯ",
                        color = when {
                            isSelected -> Color(0xFFDBEAFE)
                            isDark -> AmberPrimary
                            else -> Color(0xFF1D4ED8)
                        },
                        fontSize = 7.sp,
                        lineHeight = 8.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.2.sp,
                        maxLines = 1
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = dayNames[i - 1],
                    color = when {
                        isSelected -> Color.White
                        isToday && !isDark -> Color(0xFF2563EB)
                        else -> colorScheme.onSurfaceVariant
                    },
                    fontSize = 11.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )

                Text(
                    text = dateDisplay,
                    color = if (isSelected) Color.White else colorScheme.onSurface,
                    fontSize = 11.5.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )

                // Lesson count text
                val countText = when {
                    lessonCount == 0 -> "—"
                    lessonCount == 1 -> "1 пара"
                    lessonCount in 2..4 -> "$lessonCount пары"
                    else -> "$lessonCount пар"
                }

                val countColor = when {
                    isSelected -> Color.White.copy(alpha = 0.9f)
                    lessonCount > 0 -> colorScheme.onSurfaceVariant
                    else -> colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                }

                Text(
                    text = countText,
                    color = countColor,
                    fontSize = 8.5.sp,
                    lineHeight = 10.sp,
                    fontWeight = if (lessonCount > 0 || isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

