package ru.topacademy.omni.ui.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.topacademy.omni.data.db.OmniDatabase
import ru.topacademy.omni.domain.model.Lesson
import ru.topacademy.omni.domain.model.LessonStatus
import ru.topacademy.omni.domain.util.LessonStateCalculator
import ru.topacademy.omni.ui.theme.OmniTheme

class LessonDialogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val lessonId = intent.getStringExtra("EXTRA_LESSON_ID") ?: run {
            finish()
            return
        }

        setContent {
            OmniTheme(isDark = true) {
                var lessonState by remember { mutableStateOf<Lesson?>(null) }
                var statusState by remember { mutableStateOf(LessonStatus.SCHEDULED) }

                LaunchedEffect(lessonId) {
                    val db = OmniDatabase.getInstance(this@LessonDialogActivity)
                    val lessonEntity = withContext(Dispatchers.IO) {
                        db.lessonDao().getLessonByIdSync(lessonId)
                    }
                    if (lessonEntity != null) {
                        val lesson = lessonEntity.toDomain()
                        val todayLessons = withContext(Dispatchers.IO) {
                            db.lessonDao().getLessonsForDateSync(lesson.date).map { it.toDomain() }
                        }
                        lessonState = lesson
                        statusState = LessonStateCalculator.determineStatus(lesson, todayLessons)
                    } else {
                        finish()
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .clickable { finish() },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val currentLesson = lessonState
                    if (currentLesson != null) {
                        LessonDetailSheet(
                            lesson = currentLesson,
                            status = statusState,
                            onDismiss = { finish() }
                        )
                    }
                }
            }
        }
    }
}
