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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.topacademy.omni.domain.model.TeacherProfile
import ru.topacademy.omni.ui.theme.AmberPrimary
import ru.topacademy.omni.ui.theme.BlueAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileBottomSheet(
    profile: TeacherProfile?,
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // User Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, if (isDarkTheme) AmberPrimary else BlueAccent, CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = if (isDarkTheme) AmberPrimary else BlueAccent,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = profile?.name?.ifEmpty { "Преподаватель" } ?: "Преподаватель",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = profile?.email ?: "teacher@top-academy.ru",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "ПРЕПОДАВАТЕЛЬ",
                            color = BlueAccent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x262563EB))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 1. Theme Switcher Segmented Control
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isDarkTheme) "🌙" else "☀️",
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Тема оформления",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isDarkTheme) "Тёмная тема активна" else "Светлая тема активна",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Segmented buttons: Тёмная / Светлая
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(3.dp)
                ) {
                    // Dark Button
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isDarkTheme) BlueAccent else Color.Transparent)
                            .clickable { onThemeChange(true) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DarkMode,
                            contentDescription = null,
                            tint = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Тёмная",
                            color = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = if (isDarkTheme) FontWeight.Bold else FontWeight.Medium
                        )
                    }

                    // Light Button
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (!isDarkTheme) BlueAccent else Color.Transparent)
                            .clickable { onThemeChange(false) }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LightMode,
                            contentDescription = null,
                            tint = if (!isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Светлая",
                            color = if (!isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = if (!isDarkTheme) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            var showAboutDialog by remember { mutableStateOf(false) }

            Spacer(modifier = Modifier.height(14.dp))

            // About App Button
            OutlinedButton(
                onClick = { showAboutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isDarkTheme) AmberPrimary else BlueAccent
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "О приложении",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Logout Button
            val logoutBg = if (isDarkTheme) Color(0xFF451A1A) else Color(0xFFFEE2E2)
            val logoutBorder = if (isDarkTheme) Color(0xFF7F1D1D) else Color(0xFFFECACA)
            val logoutText = if (isDarkTheme) Color(0xFFFCA5A5) else Color(0xFFDC2626)

            Button(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = logoutBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, logoutBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = null,
                    tint = logoutText,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Выйти из аккаунта",
                    color = logoutText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (showAboutDialog) {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val versionName = remember {
                    try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.0.0"
                    } catch (e: Exception) {
                        "2.0.0"
                    }
                }
                var updateState by remember { mutableStateOf("IDLE") }
                var latestVersionName by remember { mutableStateOf("") }
                var apkDownloadUrl by remember { mutableStateOf("") }
                var updateErrorMsg by remember { mutableStateOf("") }

                fun checkUpdates() {
                    scope.launch(Dispatchers.IO) {
                        updateState = "CHECKING"
                        try {
                            val client = OkHttpClient()
                            val request = Request.Builder()
                                .url("https://api.github.com/repos/SharmanGit/omni-top-schedule/releases/latest")
                                .header("Accept", "application/vnd.github.v3+json")
                                .header("User-Agent", "OmniTOP-Android")
                                .build()
                            val response = client.newCall(request).execute()
                            if (response.isSuccessful) {
                                val jsonStr = response.body?.string() ?: ""
                                val json = JSONObject(jsonStr)
                                val tagName = json.optString("tag_name", "").removePrefix("v")
                                val htmlUrl = json.optString("html_url", "")
                                var downloadUrl = htmlUrl
                                val assets = json.optJSONArray("assets")
                                if (assets != null) {
                                    for (i in 0 until assets.length()) {
                                        val asset = assets.getJSONObject(i)
                                        val name = asset.optString("name", "")
                                        if (name.endsWith(".apk")) {
                                            downloadUrl = asset.optString("browser_download_url", htmlUrl)
                                            break
                                        }
                                    }
                                }

                                val curParts = versionName.removePrefix("v").split("-")[0].split(".").mapNotNull { it.toIntOrNull() }
                                val newParts = tagName.split("-")[0].split(".").mapNotNull { it.toIntOrNull() }
                                var isNewer = false
                                val maxLen = maxOf(curParts.size, newParts.size)
                                for (i in 0 until maxLen) {
                                    val n = newParts.getOrElse(i) { 0 }
                                    val c = curParts.getOrElse(i) { 0 }
                                    if (n > c) {
                                        isNewer = true
                                        break
                                    }
                                    if (n < c) break
                                }

                                withContext(Dispatchers.Main) {
                                    if (isNewer && tagName.isNotEmpty()) {
                                        latestVersionName = tagName
                                        apkDownloadUrl = downloadUrl
                                        updateState = "AVAILABLE"
                                    } else {
                                        updateState = "UP_TO_DATE"
                                    }
                                }
                            } else if (response.code == 404) {
                                withContext(Dispatchers.Main) {
                                    updateState = "UP_TO_DATE"
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    updateErrorMsg = "Ошибка ${response.code}"
                                    updateState = "ERROR"
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                updateErrorMsg = e.localizedMessage ?: "Сетевая ошибка"
                                updateState = "ERROR"
                            }
                        }
                    }
                }

                AlertDialog(
                    onDismissRequest = { showAboutDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = if (isDarkTheme) AmberPrimary else BlueAccent,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "О приложении",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Omni TOP",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Версия $versionName",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            )
                            Text(
                                text = "Разработано DLTD VDB ©",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDarkTheme) AmberPrimary else BlueAccent
                            )
                            Text(
                                text = "Клиент расписания, виджеты рабочего стола и журнал проверки ДЗ для преподавателей Академии ТОП.",
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            )

                            // In-App Update Checker Section
                            when (updateState) {
                                "IDLE" -> {
                                    OutlinedButton(
                                        onClick = { checkUpdates() },
                                        modifier = Modifier.fillMaxWidth().height(36.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Проверить обновления", fontSize = 12.sp)
                                    }
                                }
                                "CHECKING" -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Проверка новой версии...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                "UP_TO_DATE" -> {
                                    Text(
                                        text = "✓ У вас установлена последняя версия",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF10B981)
                                    )
                                }
                                "AVAILABLE" -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFF1F5F9),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "🚀 Доступно обновление: v$latestVersionName",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            color = if (isDarkTheme) AmberPrimary else BlueAccent
                                        )
                                        Button(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkDownloadUrl))
                                                context.startActivity(intent)
                                            },
                                            modifier = Modifier.fillMaxWidth().height(34.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isDarkTheme) AmberPrimary else BlueAccent
                                            ),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text("Скачать и обновить", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                                "ERROR" -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Не удалось проверить",
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        TextButton(onClick = { checkUpdates() }) {
                                            Text("Повторить", fontSize = 11.5.sp)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showAboutDialog = false }) {
                            Text(
                                text = "Закрыть",
                                color = if (isDarkTheme) AmberPrimary else BlueAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.surface
                )
            }
        }
    }
}
