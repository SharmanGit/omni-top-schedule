import Foundation
import SwiftUI
import WidgetKit

@MainActor
final class MainViewModel: ObservableObject {
    @Published var isLoggedIn: Bool = false
    @Published var isLoading: Bool = false
    @Published var isRefreshing: Bool = false
    @Published var errorMessage: String? = nil

    @Published var weekOffset: Int = 0
    @Published var selectedDayIndex: Int = MainViewModel.currentWeekdayIndex()
    @Published var selectedBranchIds: Set<String> = [] // empty = all branches

    @Published var weekSchedule: WeekSchedule = OmniScheduleParser.getInitialWeekSchedule(weekOffset: 0)
    @Published var branches: [Branch] = []
    @Published var profile: TeacherProfile? = nil
    @Published var taskQueue: TaskQueue = TaskQueue()
    @Published var isDarkTheme: Bool = true
    @Published var lastSyncText: String = "Не синхронизировано"

    // Calendar Sync state
    @Published var calendarSyncEnabled: Bool = false
    @Published var availableCalendars: [DeviceCalendarItem] = []
    @Published var selectedCalendarId: String? = nil
    @Published var calendarLastSyncedCount: Int = 0
    @Published var calendarSyncMessage: String? = nil
    @Published var isCalendarSyncing: Bool = false

    private let apiClient = OmniApiClient()
    private let store = ScheduleStore.shared
    private let calendarSync = EventKitCalendarSync.shared

    init() {
        isDarkTheme = store.isDarkTheme
        calendarSyncEnabled = store.isCalendarSyncEnabled
        selectedCalendarId = store.selectedCalendarIdentifier
        calendarLastSyncedCount = store.lastCalendarSyncedCount

        if let session = KeychainHelper.loadSession() {
            isLoggedIn = true
            profile = store.loadProfile() ?? TeacherProfile(
                id: session.username,
                name: session.teacherName,
                email: session.username,
                branches: session.branches.map {
                    let cat = OmniScheduleParser.classifyBranch(cityName: $0.cityName)
                    return Branch(cityId: $0.cityId, cityName: $0.cityName, category: cat, colorHex: OmniScheduleParser.getBranchColor(category: cat))
                }
            )
            branches = store.loadBranches()
            taskQueue = store.loadTaskQueue()
            loadWeekFromStore()
            updateLastSyncText()
            refreshCalendarState()
            Task { await refreshAll(silent: true) }
        }
    }

    static func currentWeekdayIndex() -> Int {
        let weekday = Calendar(identifier: .gregorian).component(.weekday, from: Date())
        // 1=Sun->6, 2=Mon->0 ... 7=Sat->5
        return (weekday + 5) % 7
    }

    func toggleTheme() {
        isDarkTheme.toggle()
        store.isDarkTheme = isDarkTheme
    }

    func selectWeek(offset: Int) {
        weekOffset = offset
        loadWeekFromStore()
        Task { await refreshScheduleOnly(weekOffset: offset) }
    }

    func toggleBranchFilter(_ cityId: String) {
        if selectedBranchIds.contains(cityId) {
            selectedBranchIds.remove(cityId)
        } else {
            selectedBranchIds.insert(cityId)
        }
    }

    func clearBranchFilter() {
        selectedBranchIds.removeAll()
    }

    func filteredLessonsForSelectedDay() -> [Lesson] {
        guard selectedDayIndex >= 0 && selectedDayIndex < weekSchedule.days.count else { return [] }
        let dayLessons = weekSchedule.days[selectedDayIndex].lessons
        if selectedBranchIds.isEmpty { return dayLessons }
        return dayLessons.filter { selectedBranchIds.contains($0.branchId) }
    }

    // MARK: - Auth

    func login(username: String, password: String) async {
        let trimmedUser = username.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedUser.isEmpty, !password.isEmpty else {
            errorMessage = "Введите логин и пароль"
            return
        }

        isLoading = true
        errorMessage = nil
        do {
            let session = try await apiClient.login(username: trimmedUser, password: password)
            KeychainHelper.saveSession(session)
            KeychainHelper.saveCredentials(username: trimmedUser, password: password)

            let branchModels = session.branches.map {
                let cat = OmniScheduleParser.classifyBranch(cityName: $0.cityName)
                return Branch(cityId: $0.cityId, cityName: $0.cityName, category: cat, colorHex: OmniScheduleParser.getBranchColor(category: cat))
            }
            let teacherProfile = TeacherProfile(
                id: session.username,
                name: session.teacherName,
                email: session.username,
                branches: branchModels
            )
            store.saveBranches(branchModels)
            store.saveProfile(teacherProfile)

            branches = branchModels
            profile = teacherProfile
            isLoggedIn = true
            isLoading = false

            await refreshAll(silent: false)
        } catch {
            isLoading = false
            errorMessage = error.localizedDescription
        }
    }

    func logout() {
        calendarSync.clearSyncedEvents()
        store.clearAllUserData()
        KeychainHelper.clearAll()
        isLoggedIn = false
        weekOffset = 0
        selectedBranchIds.removeAll()
        weekSchedule = OmniScheduleParser.getInitialWeekSchedule(weekOffset: 0)
        branches = []
        profile = nil
        taskQueue = TaskQueue()
        calendarSyncEnabled = false
        calendarLastSyncedCount = 0
        WidgetCenter.shared.reloadAllTimelines()
    }

    // MARK: - Sync

    func refreshAll(silent: Bool = false) async {
        guard var session = KeychainHelper.loadSession() else { return }
        if !silent { isRefreshing = true }
        errorMessage = nil

        do {
            session = try await ensureValidSession(session)
            // Fetch current week and next week when weekOffset == 0 so widget & calendar sync stay fresh
            let weeksToFetch = weekOffset == 0 ? [0, 1] : [weekOffset, 0, 1]
            let uniqueWeeks = Array(Set(weeksToFetch))

            for w in uniqueWeeks {
                try await fetchAndSaveWeek(session: &session, weekOffset: w)
            }

            if let tasksData = try? await apiClient.fetchTasks(cookieStr: session.cookieStr, idLocalHash: session.idLocalHash) {
                let parsedTasks = OmniScheduleParser.parseTasks(rawData: tasksData)
                store.saveTaskQueue(parsedTasks)
                taskQueue = parsedTasks
            }

            KeychainHelper.saveSession(session)
            store.lastSyncDate = Date()
            updateLastSyncText()
            loadWeekFromStore()
            WidgetCenter.shared.reloadAllTimelines()

            if calendarSync.hasAccess && store.isCalendarSyncEnabled {
                _ = calendarSync.syncCurrentAndNextWeek(from: store)
                calendarLastSyncedCount = store.lastCalendarSyncedCount
            }
        } catch OmniApiError.sessionExpired {
            if await reauthenticateIfPossible() {
                await refreshAll(silent: silent)
                return
            } else {
                errorMessage = "Сессия истекла. Войдите снова."
            }
        } catch {
            if !silent {
                errorMessage = error.localizedDescription
            }
        }
        isRefreshing = false
    }

    private func refreshScheduleOnly(weekOffset targetWeek: Int) async {
        guard var session = KeychainHelper.loadSession() else { return }
        isRefreshing = true
        do {
            try await fetchAndSaveWeek(session: &session, weekOffset: targetWeek)
            KeychainHelper.saveSession(session)
            loadWeekFromStore()
            WidgetCenter.shared.reloadAllTimelines()
        } catch OmniApiError.sessionExpired {
            if await reauthenticateIfPossible() {
                await refreshScheduleOnly(weekOffset: targetWeek)
                return
            }
        } catch {
            errorMessage = error.localizedDescription
        }
        isRefreshing = false
    }

    private func ensureValidSession(_ session: UserSession) async throws -> UserSession {
        return session
    }

    private func reauthenticateIfPossible() async -> Bool {
        guard let creds = KeychainHelper.loadCredentials() else { return false }
        do {
            let newSession = try await apiClient.login(username: creds.username, password: creds.password)
            KeychainHelper.saveSession(newSession)
            return true
        } catch {
            return false
        }
    }

    private func fetchAndSaveWeek(session: inout UserSession, weekOffset: Int) async throws {
        var allLessons: [Lesson] = []
        let weekDatesISO = OmniScheduleParser.getWeekDates(weekOffset: weekOffset).map { OmniScheduleParser.formatISO($0) }

        if session.branches.isEmpty {
            let rawData = try await apiClient.fetchSchedule(cookieStr: session.cookieStr, idLocalHash: session.idLocalHash, weekOffset: weekOffset)
            let fallbackBranch = Branch(cityId: "0", cityName: "Основной филиал", category: "ACADEMY", colorHex: "#f59e0b")
            allLessons.append(contentsOf: OmniScheduleParser.parseSchedule(rawData: rawData, branch: fallbackBranch, weekOffset: weekOffset))
        } else {
            for index in session.branches.indices {
                let branchSession = session.branches[index]
                let switchedCookie = try await apiClient.switchCity(
                    baseCookieStr: session.cookieStr,
                    idLocalHash: session.idLocalHash,
                    cityId: branchSession.cityId
                )
                session.branches[index].cookieStr = switchedCookie

                let cat = OmniScheduleParser.classifyBranch(cityName: branchSession.cityName)
                let branchModel = Branch(
                    cityId: branchSession.cityId,
                    cityName: branchSession.cityName,
                    category: cat,
                    colorHex: OmniScheduleParser.getBranchColor(category: cat)
                )

                let rawData = try await apiClient.fetchSchedule(
                    cookieStr: switchedCookie,
                    idLocalHash: session.idLocalHash,
                    weekOffset: weekOffset
                )
                var parsed = OmniScheduleParser.parseSchedule(rawData: rawData, branch: branchModel, weekOffset: weekOffset)

                // Check attendance for past or today's lessons
                for idx in parsed.indices {
                    let lesson = parsed[idx]
                    let todayIso = BranchTimeZoneResolver.branchLocalTodayISO(branchName: lesson.branchName)
                    if lesson.date <= todayIso {
                        if let presentsData = try? await apiClient.fetchPresents(
                            cookieStr: switchedCookie,
                            idLocalHash: session.idLocalHash,
                            lenta: lesson.lentaNum,
                            date: lesson.date
                        ), let marked = OmniScheduleParser.parseAttendanceStatus(rawData: presentsData) {
                            parsed[idx].isAttendanceMarked = marked
                        }
                    }
                }

                allLessons.append(contentsOf: parsed)
            }
        }

        store.saveWeekLessons(allLessons, weekDatesISO: weekDatesISO)
    }

    private func loadWeekFromStore() {
        let all = store.loadLessons()
        weekSchedule = OmniScheduleParser.buildWeekSchedule(allLessons: all, weekOffset: weekOffset)
    }

    private func updateLastSyncText() {
        guard let date = store.lastSyncDate else {
            lastSyncText = "Не синхронизировано"
            return
        }
        let formatter = DateFormatter()
        formatter.dateFormat = "dd.MM HH:mm"
        lastSyncText = formatter.string(from: date)
    }

    // MARK: - Calendar Sync Actions

    func refreshCalendarState() {
        calendarSyncEnabled = store.isCalendarSyncEnabled
        availableCalendars = calendarSync.getAvailableCalendars()
        selectedCalendarId = store.selectedCalendarIdentifier ?? availableCalendars.first?.calendarIdentifier
        calendarLastSyncedCount = store.lastCalendarSyncedCount
    }

    func toggleCalendarSync(enabled: Bool) async {
        if enabled {
            let granted = await calendarSync.requestAccess()
            guard granted else {
                calendarSyncEnabled = false
                store.isCalendarSyncEnabled = false
                calendarSyncMessage = "Разрешите доступ к календарю в Настройках iPhone"
                return
            }
            store.isCalendarSyncEnabled = true
            calendarSyncEnabled = true
            refreshCalendarState()
            await syncCalendarNow()
        } else {
            store.isCalendarSyncEnabled = false
            calendarSyncEnabled = false
            let res = calendarSync.clearSyncedEvents()
            if case .success(let removed) = res {
                calendarSyncMessage = removed > 0 ? "Удалено событий из календаря: \(removed)" : "Синхронизация отключена"
            }
            calendarLastSyncedCount = 0
        }
    }

    func selectCalendar(_ calendarIdentifier: String) async {
        // Clear previously synced events before switching to another calendar
        if let oldId = store.selectedCalendarIdentifier, oldId != calendarIdentifier {
            _ = calendarSync.clearSyncedEvents(calendarIdentifier: oldId)
        }
        store.selectedCalendarIdentifier = calendarIdentifier
        selectedCalendarId = calendarIdentifier
        if calendarSyncEnabled {
            await syncCalendarNow()
        }
    }

    func syncCalendarNow() async {
        isCalendarSyncing = true
        let result = calendarSync.syncCurrentAndNextWeek(from: store)
        isCalendarSyncing = false
        switch result {
        case .success(let count):
            calendarLastSyncedCount = count
            calendarSyncMessage = "Синхронизировано занятий: \(count)"
        case .failure(let error):
            calendarSyncMessage = error.localizedDescription
        }
    }

    func clearCalendarEventsNow() {
        let res = calendarSync.clearSyncedEvents()
        switch res {
        case .success(let count):
            calendarLastSyncedCount = 0
            calendarSyncMessage = "Удалено событий: \(count)"
        case .failure(let error):
            calendarSyncMessage = error.localizedDescription
        }
    }
}
