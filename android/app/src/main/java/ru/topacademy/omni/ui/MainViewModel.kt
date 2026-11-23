package ru.topacademy.omni.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.topacademy.omni.data.parser.OmniScheduleParser
import ru.topacademy.omni.data.repository.OmniRepository
import ru.topacademy.omni.domain.model.Branch
import ru.topacademy.omni.domain.model.Lesson
import ru.topacademy.omni.domain.model.TaskQueue
import ru.topacademy.omni.domain.model.TeacherProfile
import ru.topacademy.omni.domain.model.WeekSchedule
import java.time.LocalDate

data class MainUiState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val isDarkTheme: Boolean = true,
    val loginError: String? = null,
    val selectedWeekOffset: Int = 0,
    val selectedDayOfWeek: Int = LocalDate.now().dayOfWeek.value, // 1..7
    val selectedBranchIds: Set<String> = emptySet(), // empty = All locations
    val profile: TeacherProfile? = null,
    val branches: List<Branch> = emptyList(),
    val weekSchedule: WeekSchedule = WeekSchedule(0, ""),
    val taskQueue: TaskQueue = TaskQueue(),
    val selectedLessonDetails: Lesson? = null,
    val isTasksSheetOpen: Boolean = false,
    val isSourcesModalOpen: Boolean = false,
    val isProfileModalOpen: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = OmniRepository(application)
    private val themePrefs = application.getSharedPreferences("omni_theme_pref", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        MainUiState(
            isLoggedIn = repository.isLoggedIn(),
            isDarkTheme = themePrefs.getBoolean("key_is_dark", true),
            selectedDayOfWeek = LocalDate.now().dayOfWeek.value
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        observeData()
        if (repository.isLoggedIn()) {
            refresh()
        }
    }

    private var observeProfileJob: Job? = null
    private var observeBranchesJob: Job? = null
    private var observeTasksJob: Job? = null
    private var observeScheduleJob: Job? = null
    private var syncWeekJob: Job? = null

    private fun observeData() {
        observeProfileJob?.cancel()
        observeBranchesJob?.cancel()
        observeTasksJob?.cancel()

        observeProfileJob = viewModelScope.launch {
            repository.getTeacherProfile().collectLatest { prof ->
                _uiState.update { it.copy(profile = prof) }
            }
        }
        observeBranchesJob = viewModelScope.launch {
            repository.getBranches().collectLatest { brs ->
                _uiState.update { it.copy(branches = brs) }
            }
        }
        observeTasksJob = viewModelScope.launch {
            repository.getTaskQueue().collectLatest { tasks ->
                _uiState.update { it.copy(taskQueue = tasks) }
            }
        }
        observeScheduleForWeek(0)
    }

    private fun observeScheduleForWeek(weekOffset: Int) {
        observeScheduleJob?.cancel()
        observeScheduleJob = viewModelScope.launch {
            repository.getWeekSchedule(weekOffset).collectLatest { week ->
                if (_uiState.value.selectedWeekOffset == weekOffset) {
                    _uiState.update { it.copy(weekSchedule = week) }
                }
            }
        }
    }

    fun setTheme(isDark: Boolean) {
        themePrefs.edit().putBoolean("key_is_dark", isDark).apply()
        _uiState.update { it.copy(isDarkTheme = isDark) }
    }

    fun toggleTheme() {
        setTheme(!_uiState.value.isDarkTheme)
    }

    fun selectWeek(offset: Int) {
        if (offset > 2) return
        val clamped = offset.coerceAtLeast(-8)
        if (clamped == _uiState.value.selectedWeekOffset && _uiState.value.weekSchedule.days.isNotEmpty()) return

        // Immediately update offset and prefill initial week structure
        val initialWeek = OmniScheduleParser.getInitialWeekSchedule(clamped)
        _uiState.update {
            it.copy(
                selectedWeekOffset = clamped,
                weekSchedule = initialWeek
            )
        }

        observeScheduleForWeek(clamped)
        syncWeek(clamped)
    }

    fun selectDay(dayOfWeek: Int) {
        _uiState.update { it.copy(selectedDayOfWeek = dayOfWeek) }
        viewModelScope.launch {
            repository.syncAttendanceForDay(dayOfWeek, _uiState.value.selectedWeekOffset)
        }
    }

    fun openSourcesModal() {
        _uiState.update { it.copy(isSourcesModalOpen = true) }
    }

    fun closeSourcesModal() {
        _uiState.update { it.copy(isSourcesModalOpen = false) }
    }

    fun applySelectedBranches(branches: Set<String>) {
        _uiState.update { it.copy(selectedBranchIds = branches, isSourcesModalOpen = false) }
    }

    fun openProfileModal() {
        _uiState.update { it.copy(isProfileModalOpen = true) }
    }

    fun closeProfileModal() {
        _uiState.update { it.copy(isProfileModalOpen = false) }
    }

    fun openLessonDetails(lesson: Lesson) {
        _uiState.update { it.copy(selectedLessonDetails = lesson) }
        viewModelScope.launch {
            val isMarked = repository.checkLessonAttendance(lesson)
            val current = _uiState.value.selectedLessonDetails
            if (current?.id == lesson.id) {
                _uiState.update { it.copy(selectedLessonDetails = current.copy(isAttendanceMarked = isMarked)) }
            }
        }
    }

    fun toggleLessonAttendance(lessonId: String, isMarked: Boolean) {
        viewModelScope.launch {
            repository.toggleAttendance(lessonId, isMarked)
            // If the currently open sheet is this lesson, update its copy in uiState as well
            val current = _uiState.value.selectedLessonDetails
            if (current?.id == lessonId) {
                _uiState.update { it.copy(selectedLessonDetails = current.copy(isAttendanceMarked = isMarked)) }
            }
        }
    }

    fun closeLessonDetails() {
        _uiState.update { it.copy(selectedLessonDetails = null) }
    }

    fun openTasksSheet() {
        _uiState.update { it.copy(isTasksSheetOpen = true) }
    }

    fun closeTasksSheet() {
        _uiState.update { it.copy(isTasksSheetOpen = false) }
    }

    fun login(username: String, pass: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loginError = null) }
            val res = repository.login(username, pass)
            if (res.isSuccess) {
                _uiState.update {
                    it.copy(
                        isLoggedIn = true,
                        isLoading = false,
                        loginError = null
                    )
                }
                observeData()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loginError = res.exceptionOrNull()?.localizedMessage ?: "Ошибка авторизации"
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            observeProfileJob?.cancel()
            observeBranchesJob?.cancel()
            observeTasksJob?.cancel()
            observeScheduleJob?.cancel()
            syncWeekJob?.cancel()
            _uiState.update { MainUiState(isLoggedIn = false, isDarkTheme = _uiState.value.isDarkTheme) }
        }
    }

    fun refresh() {
        val currentWeek = _uiState.value.selectedWeekOffset
        syncWeek(currentWeek)
        syncTasks()
        viewModelScope.launch {
            repository.syncAttendanceForPastWeekLessons(currentWeek)
        }
    }

    private fun syncWeek(weekOffset: Int) {
        syncWeekJob?.cancel()
        syncWeekJob = viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            repository.syncWeekSchedule(weekOffset)
            _uiState.update { it.copy(isSyncing = false) }
        }
    }

    private fun syncTasks() {
        viewModelScope.launch {
            repository.syncTasks()
        }
    }
}
