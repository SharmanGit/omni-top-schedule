import EventKit
import Foundation

struct DeviceCalendarItem: Identifiable, Hashable {
    var id: String { calendarIdentifier }
    let calendarIdentifier: String
    let title: String
    let accountName: String
    let isDefault: Bool
}

final class EventKitCalendarSync {
    static let shared = EventKitCalendarSync()
    private static let omniTagPrefix = "[omni-id:"
    private static let omniTagSuffix = "]"

    private let store = EKEventStore()

    var hasAccess: Bool {
        let status = EKEventStore.authorizationStatus(for: .event)
        if #available(iOS 17.0, *) {
            return status == .fullAccess
        } else {
            return status == .authorized
        }
    }

    func requestAccess() async -> Bool {
        if hasAccess { return true }
        do {
            if #available(iOS 17.0, *) {
                return try await store.requestFullAccessToEvents()
            } else {
                return try await withCheckedThrowingContinuation { continuation in
                    store.requestAccess(to: .event) { granted, error in
                        if let error = error {
                            continuation.resume(throwing: error)
                        } else {
                            continuation.resume(returning: granted)
                        }
                    }
                }
            }
        } catch {
            return false
        }
    }

    func getAvailableCalendars() -> [DeviceCalendarItem] {
        guard hasAccess else { return [] }
        let defaultId = store.defaultCalendarForNewEvents?.calendarIdentifier
        return store.calendars(for: .event)
            .filter { $0.allowsContentModifications }
            .map { cal in
                DeviceCalendarItem(
                    calendarIdentifier: cal.calendarIdentifier,
                    title: cal.title,
                    accountName: cal.source?.title ?? "",
                    isDefault: cal.calendarIdentifier == defaultId
                )
            }
            .sorted { lhs, rhs in
                if lhs.isDefault != rhs.isDefault { return lhs.isDefault && !rhs.isDefault }
                return lhs.title.localizedCaseInsensitiveCompare(rhs.title) == .orderedAscending
            }
    }

    func ensureSelectedCalendarIdentifier(in scheduleStore: ScheduleStore = .shared) -> String? {
        let calendars = getAvailableCalendars()
        guard !calendars.isEmpty else { return nil }
        if let saved = scheduleStore.selectedCalendarIdentifier,
           calendars.contains(where: { $0.calendarIdentifier == saved }) {
            return saved
        }
        let chosen = calendars.first!
        scheduleStore.selectedCalendarIdentifier = chosen.calendarIdentifier
        return chosen.calendarIdentifier
    }

    @discardableResult
    func syncCurrentAndNextWeek(from scheduleStore: ScheduleStore = .shared) -> Result<Int, Error> {
        guard scheduleStore.isCalendarSyncEnabled else { return .success(0) }
        guard hasAccess else {
            return .failure(NSError(domain: "OmniCalendar", code: 1, userInfo: [NSLocalizedDescriptionKey: "Нет разрешения на доступ к календарю"]))
        }
        guard let calendarId = ensureSelectedCalendarIdentifier(in: scheduleStore),
              let targetCalendar = store.calendar(withIdentifier: calendarId) else {
            return .failure(NSError(domain: "OmniCalendar", code: 2, userInfo: [NSLocalizedDescriptionKey: "На устройстве не найдено доступных календарей"]))
        }

        let currentWeek = OmniScheduleParser.getWeekDates(weekOffset: 0)
        let nextWeek = OmniScheduleParser.getWeekDates(weekOffset: 1)
        guard let firstDay = currentWeek.first,
              let lastDay = nextWeek.last else {
            return .success(0)
        }

        let startIso = OmniScheduleParser.formatISO(firstDay)
        let endIso = OmniScheduleParser.formatISO(lastDay)
        let lessons = scheduleStore.loadLessons().filter { $0.date >= startIso && $0.date <= endIso }

        let windowStart = Calendar.current.date(byAdding: .day, value: -1, to: firstDay) ?? firstDay
        let windowEnd = Calendar.current.date(byAdding: .day, value: 2, to: lastDay) ?? lastDay

        let result = syncLessons(calendar: targetCalendar, lessons: lessons, windowStart: windowStart, windowEnd: windowEnd)
        if case .success(let count) = result {
            scheduleStore.lastCalendarSyncedCount = count
        }
        return result
    }

    func syncLessons(
        calendar: EKCalendar,
        lessons: [Lesson],
        windowStart: Date,
        windowEnd: Date
    ) -> Result<Int, Error> {
        do {
            let predicate = store.predicateForEvents(withStart: windowStart, end: windowEnd, calendars: [calendar])
            let existingEvents = store.events(matching: predicate)

            var existingByOmniId: [String: EKEvent] = [:]
            for event in existingEvents {
                guard let notes = event.notes, let omniId = extractOmniId(from: notes) else { continue }
                if existingByOmniId[omniId] != nil {
                    try store.remove(event, span: .thisEvent, commit: false)
                } else {
                    existingByOmniId[omniId] = event
                }
            }

            var seenOmniIds = Set<String>()
            var syncedCount = 0

            for lesson in lessons {
                guard let startDate = BranchTimeZoneResolver.lessonStartDate(lesson),
                      let endDate = BranchTimeZoneResolver.lessonEndDate(lesson),
                      startDate >= windowStart && startDate <= windowEnd else {
                    continue
                }

                seenOmniIds.insert(lesson.id)
                let title = buildTitle(lesson)
                let location = buildLocation(lesson)
                let notes = buildDescription(lesson)
                let timeZone = BranchTimeZoneResolver.resolveTimeZone(branchName: lesson.branchName)

                if let existing = existingByOmniId[lesson.id] {
                    let changed = existing.title != title ||
                        (existing.location ?? "") != location ||
                        (existing.notes ?? "") != notes ||
                        abs(existing.startDate.timeIntervalSince(startDate)) > 1 ||
                        abs(existing.endDate.timeIntervalSince(endDate)) > 1 ||
                        existing.timeZone?.identifier != timeZone.identifier

                    if changed {
                        existing.title = title
                        existing.location = location
                        existing.notes = notes
                        existing.startDate = startDate
                        existing.endDate = endDate
                        existing.timeZone = timeZone
                        try store.save(existing, span: .thisEvent, commit: false)
                    }
                    syncedCount += 1
                } else {
                    let event = EKEvent(eventStore: store)
                    event.calendar = calendar
                    event.title = title
                    event.location = location
                    event.notes = notes
                    event.startDate = startDate
                    event.endDate = endDate
                    event.timeZone = timeZone
                    try store.save(event, span: .thisEvent, commit: false)
                    syncedCount += 1
                }
            }

            for (omniId, event) in existingByOmniId where !seenOmniIds.contains(omniId) {
                try store.remove(event, span: .thisEvent, commit: false)
            }

            try store.commit()
            return .success(syncedCount)
        } catch {
            return .failure(error)
        }
    }

    @discardableResult
    func clearSyncedEvents(calendarIdentifier: String? = nil) -> Result<Int, Error> {
        guard hasAccess else { return .success(0) }
        do {
            let now = Date()
            let start = Calendar.current.date(byAdding: .year, value: -1, to: now) ?? now
            let end = Calendar.current.date(byAdding: .year, value: 2, to: now) ?? now
            let calendars: [EKCalendar]? = calendarIdentifier.flatMap { store.calendar(withIdentifier: $0) }.map { [$0] }

            let predicate = store.predicateForEvents(withStart: start, end: end, calendars: calendars)
            let events = store.events(matching: predicate)
            var deleted = 0
            for event in events {
                if let notes = event.notes, notes.contains(Self.omniTagPrefix) {
                    try store.remove(event, span: .thisEvent, commit: false)
                    deleted += 1
                }
            }
            if deleted > 0 {
                try store.commit()
            }
            ScheduleStore.shared.lastCalendarSyncedCount = 0
            return .success(deleted)
        } catch {
            return .failure(error)
        }
    }

    private func extractOmniId(from description: String) -> String? {
        guard let startRange = description.range(of: Self.omniTagPrefix),
              let endRange = description.range(of: Self.omniTagSuffix, range: startRange.upperBound..<description.endIndex) else {
            return nil
        }
        let id = String(description[startRange.upperBound..<endRange.lowerBound]).trimmingCharacters(in: .whitespacesAndNewlines)
        return id.isEmpty ? nil : id
    }

    private func buildTitle(_ lesson: Lesson) -> String {
        let subj = lesson.subject.isEmpty ? "Занятие" : lesson.subject
        return lesson.group.isEmpty ? subj : "\(subj) (\(lesson.group))"
    }

    private func buildLocation(_ lesson: Lesson) -> String {
        let cleanRoom = lesson.room.replacingOccurrences(of: #"\s*\([^)]*\)"#, with: "", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return [cleanRoom, lesson.branchName]
            .filter { !$0.isEmpty && $0 != "—" }
            .joined(separator: " • ")
    }

    private func buildDescription(_ lesson: Lesson) -> String {
        var lines: [String] = []
        if !lesson.group.isEmpty { lines.append("Группа: \(lesson.group)") }
        if !lesson.room.isEmpty && lesson.room != "—" { lines.append("Аудитория: \(lesson.room)") }
        if !lesson.branchName.isEmpty { lines.append("Филиал: \(lesson.branchName)") }
        lines.append("")
        lines.append("\(Self.omniTagPrefix)\(lesson.id)\(Self.omniTagSuffix)")
        return lines.joined(separator: "\n")
    }
}
