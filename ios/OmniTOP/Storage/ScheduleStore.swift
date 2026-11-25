import Foundation

final class ScheduleStore {
    static let appGroupId = "group.ru.topacademy.omni"
    static let shared = ScheduleStore()

    private let defaults: UserDefaults
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    private enum Keys {
        static let lessons = "omni_lessons_v1"
        static let branches = "omni_branches_v1"
        static let profile = "omni_profile_v1"
        static let taskQueue = "omni_tasks_v1"
        static let isDarkTheme = "omni_dark_theme"
        static let lastSyncTimestamp = "omni_last_sync_at"
        static let calendarSyncEnabled = "omni_calendar_sync_enabled"
        static let selectedCalendarId = "omni_selected_calendar_id"
        static let lastCalendarSyncedCount = "omni_calendar_last_count"
    }

    init(suiteName: String = ScheduleStore.appGroupId) {
        self.defaults = UserDefaults(suiteName: suiteName) ?? .standard
    }

    // MARK: - Lessons & Branches

    func loadLessons() -> [Lesson] {
        guard let data = defaults.data(forKey: Keys.lessons),
              let decoded = try? decoder.decode([Lesson].self, from: data) else {
            return []
        }
        return decoded
    }

    func saveWeekLessons(_ newLessons: [Lesson], weekDatesISO: [String]) {
        var existing = loadLessons()
        let weekSet = Set(weekDatesISO)
        existing.removeAll { weekSet.contains($0.date) }
        existing.append(contentsOf: newLessons)
        if let data = try? encoder.encode(existing) {
            defaults.set(data, forKey: Keys.lessons)
        }
    }

    func loadBranches() -> [Branch] {
        guard let data = defaults.data(forKey: Keys.branches),
              let decoded = try? decoder.decode([Branch].self, from: data) else {
            return []
        }
        return decoded
    }

    func saveBranches(_ branches: [Branch]) {
        if let data = try? encoder.encode(branches) {
            defaults.set(data, forKey: Keys.branches)
        }
    }

    func loadProfile() -> TeacherProfile? {
        guard let data = defaults.data(forKey: Keys.profile),
              let decoded = try? decoder.decode(TeacherProfile.self, from: data) else {
            return nil
        }
        return decoded
    }

    func saveProfile(_ profile: TeacherProfile) {
        if let data = try? encoder.encode(profile) {
            defaults.set(data, forKey: Keys.profile)
        }
    }

    func loadTaskQueue() -> TaskQueue {
        guard let data = defaults.data(forKey: Keys.taskQueue),
              let decoded = try? decoder.decode(TaskQueue.self, from: data) else {
            return TaskQueue()
        }
        return decoded
    }

    func saveTaskQueue(_ queue: TaskQueue) {
        if let data = try? encoder.encode(queue) {
            defaults.set(data, forKey: Keys.taskQueue)
        }
    }

    // MARK: - Theme & Sync Metadata

    var isDarkTheme: Bool {
        get {
            if defaults.object(forKey: Keys.isDarkTheme) == nil { return true }
            return defaults.bool(forKey: Keys.isDarkTheme)
        }
        set {
            defaults.set(newValue, forKey: Keys.isDarkTheme)
        }
    }

    var lastSyncDate: Date? {
        get {
            let ts = defaults.double(forKey: Keys.lastSyncTimestamp)
            return ts > 0 ? Date(timeIntervalSince1970: ts) : nil
        }
        set {
            defaults.set(newValue?.timeIntervalSince1970 ?? 0, forKey: Keys.lastSyncTimestamp)
        }
    }

    // MARK: - Calendar Sync Settings

    var isCalendarSyncEnabled: Bool {
        get { defaults.bool(forKey: Keys.calendarSyncEnabled) }
        set { defaults.set(newValue, forKey: Keys.calendarSyncEnabled) }
    }

    var selectedCalendarIdentifier: String? {
        get { defaults.string(forKey: Keys.selectedCalendarId) }
        set { defaults.set(newValue, forKey: Keys.selectedCalendarId) }
    }

    var lastCalendarSyncedCount: Int {
        get { defaults.integer(forKey: Keys.lastCalendarSyncedCount) }
        set { defaults.set(newValue, forKey: Keys.lastCalendarSyncedCount) }
    }

    func clearAllUserData() {
        [
            Keys.lessons,
            Keys.branches,
            Keys.profile,
            Keys.taskQueue,
            Keys.lastSyncTimestamp,
            Keys.calendarSyncEnabled,
            Keys.selectedCalendarId,
            Keys.lastCalendarSyncedCount
        ].forEach { defaults.removeObject(forKey: $0) }
    }
}
