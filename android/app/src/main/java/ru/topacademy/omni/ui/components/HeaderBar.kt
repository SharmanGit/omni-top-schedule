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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import ru.topacademy.omni.domain.model.TaskQueue
import ru.topacademy.omni.domain.model.TeacherProfile

@Composable
fun HeaderBar(
    profile: TeacherProfile?,
    tasks: TaskQueue,
    isSyncing: Boolean,
    isDark: Boolean,
    onTasksClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Teacher Info: Avatar + 2-line name (clickable area rounded)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onProfileClick() }
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(1.dp, colorScheme.outline, CircleShape)
                    .background(colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile",
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                val displayName = when {
                    !profile?.name.isNullOrBlank() -> profile!!.name
                    else -> "Преподаватель"
                }
                val nameParts = displayName.trim().split("\\s+".toRegex())
                if (nameParts.size >= 2) {
                    Text(
                        text = nameParts[0],
                        color = colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 16.sp
                    )
                    Text(
                        text = nameParts.drop(1).joinToString(" "),
                        color = colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 16.sp
                    )
                } else {
                    Text(
                        text = displayName,
                        color = colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Actions: Tasks Pill (2 lines, neutral colors, spacious) & Sync
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, colorScheme.outline, RoundedCornerShape(14.dp))
                    .background(colorScheme.surfaceVariant)
                    .clickable { onTasksClick() }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Assignment,
                    contentDescription = "Tasks",
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(9.dp))
                Column {
                    Text(
                        text = "${tasks.newHomeworkCount} ДЗ",
                        color = colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 15.sp
                    )
                    Text(
                        text = "${tasks.pendingReviewsCount} отз.",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = colorScheme.onSurfaceVariant,
                    strokeWidth = 2.dp
                )
            } else {
                IconButton(onClick = onRefreshClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
