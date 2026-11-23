package ru.topacademy.omni.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.topacademy.omni.data.parser.OmniScheduleParser
import ru.topacademy.omni.domain.model.LessonStatus
import ru.topacademy.omni.domain.util.LessonStateCalculator
import ru.topacademy.omni.ui.MainUiState
import ru.topacademy.omni.ui.MainViewModel
import ru.topacademy.omni.ui.components.BottomLocationBar
import ru.topacademy.omni.ui.components.DayRibbon
import ru.topacademy.omni.ui.components.HeaderBar
import ru.topacademy.omni.ui.components.LessonCard
import ru.topacademy.omni.ui.components.WeekNavBar

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    uiState: MainUiState
) {
    val selectedDaySchedule = uiState.weekSchedule.days.find { it.dayOfWeek == uiState.selectedDayOfWeek }
    val rawLessons = selectedDaySchedule?.lessons ?: emptyList()
    val filteredLessons = if (uiState.selectedBranchIds.isEmpty()) {
        rawLessons
    } else {
        rawLessons.filter { uiState.selectedBranchIds.contains(it.branchId) }
    }

    val allWeekLessons = uiState.weekSchedule.days.flatMap { it.lessons }
    val filteredWeekLessons = if (uiState.selectedBranchIds.isEmpty()) {
        allWeekLessons
    } else {
        allWeekLessons.filter { uiState.selectedBranchIds.contains(it.branchId) }
    }

    Scaffold(
        topBar = {
            HeaderBar(
                profile = uiState.profile,
                tasks = uiState.taskQueue,
                isSyncing = uiState.isSyncing,
                isDark = uiState.isDarkTheme,
                onTasksClick = { viewModel.openTasksSheet() },
                onRefreshClick = { viewModel.refresh() },
                onProfileClick = { viewModel.openProfileModal() }
            )
        },
        bottomBar = {
            BottomLocationBar(
                totalLessonsCount = filteredWeekLessons.size,
                selectedBranchIds = uiState.selectedBranchIds,
                branches = uiState.branches,
                onClick = { viewModel.openSourcesModal() }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Centered Week Navigation Bar
            WeekNavBar(
                weekRangeLabel = OmniScheduleParser.getWeekRangeLabel(uiState.selectedWeekOffset),
                weekOffset = uiState.selectedWeekOffset,
                isDark = uiState.isDarkTheme,
                onPrevWeek = { viewModel.selectWeek(uiState.selectedWeekOffset - 1) },
                onNextWeek = { viewModel.selectWeek(uiState.selectedWeekOffset + 1) }
            )

            // Days Ribbon under week nav
            DayRibbon(
                days = uiState.weekSchedule.days,
                selectedDayOfWeek = uiState.selectedDayOfWeek,
                weekOffset = uiState.selectedWeekOffset,
                isDark = uiState.isDarkTheme,
                selectedBranchIds = uiState.selectedBranchIds,
                onDayClick = { viewModel.selectDay(it) }
            )

            // Lessons List
            if (filteredLessons.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "В этот день пар нет",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Отличное время для отдыха или проверки ДЗ",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                val todayDayOfWeek = java.time.LocalDate.now().dayOfWeek.value
                val isCurrentDay = (uiState.selectedWeekOffset == 0 && uiState.selectedDayOfWeek == todayDayOfWeek)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredLessons, key = { it.id }) { lesson ->
                        val status = LessonStateCalculator.determineStatus(lesson, filteredLessons)
                        LessonCard(
                            lesson = lesson,
                            status = status,
                            isDark = uiState.isDarkTheme,
                            onClick = { viewModel.openLessonDetails(lesson) }
                        )
                    }
                }
            }
        }
    }

    // Sources Modal
    if (uiState.isSourcesModalOpen) {
        SourcesModal(
            branches = uiState.branches,
            allLessons = allWeekLessons,
            selectedBranchIds = uiState.selectedBranchIds,
            onApply = { branches -> viewModel.applySelectedBranches(branches) },
            onDismiss = { viewModel.closeSourcesModal() }
        )
    }

    // Tasks Sheet
    if (uiState.isTasksSheetOpen) {
        TasksBottomSheet(
            taskQueue = uiState.taskQueue,
            onDismiss = { viewModel.closeTasksSheet() }
        )
    }

    // Lesson Details Sheet
    uiState.selectedLessonDetails?.let { lesson ->
        val status = LessonStateCalculator.determineStatus(lesson, filteredLessons)
        LessonDetailSheet(
            lesson = lesson,
            status = status,
            onDismiss = { viewModel.closeLessonDetails() }
        )
    }

    // Profile & Theme Modal
    if (uiState.isProfileModalOpen) {
        ProfileBottomSheet(
            profile = uiState.profile,
            isDarkTheme = uiState.isDarkTheme,
            onThemeChange = { isDark -> viewModel.setTheme(isDark) },
            onLogout = {
                viewModel.closeProfileModal()
                viewModel.logout()
            },
            onDismiss = { viewModel.closeProfileModal() }
        )
    }
}
