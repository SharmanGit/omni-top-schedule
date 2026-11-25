import Foundation

enum OmniScheduleParser {
    private static let isoFormatter: DateFormatter = {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    private static let displayFormatter: DateFormatter = {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "dd.MM"
        return f
    }()

    static func getWeekDates(weekOffset: Int, referenceDate: Date = Date()) -> [Date] {
        var calendar = Calendar(identifier: .gregorian)
        calendar.firstWeekday = 2 // Monday
        let startOfToday = calendar.startOfDay(for: referenceDate)
        let weekday = calendar.component(.weekday, from: startOfToday)
        // weekday: 1 = Sun, 2 = Mon, ..., 7 = Sat -> offset from Monday
        let daysFromMonday = (weekday + 5) % 7
        guard let thisMonday = calendar.date(byAdding: .day, value: -daysFromMonday, to: startOfToday),
              let targetMonday = calendar.date(byAdding: .weekOfYear, value: weekOffset, to: thisMonday) else {
            return []
        }
        return (0..<7).compactMap { calendar.date(byAdding: .day, value: $0, to: targetMonday) }
    }

    static func formatISO(_ date: Date) -> String {
        isoFormatter.string(from: date)
    }

    static func formatDisplay(_ date: Date) -> String {
        displayFormatter.string(from: date)
    }

    static func getWeekRangeLabel(weekOffset: Int) -> String {
        let dates = getWeekDates(weekOffset: weekOffset)
        guard let first = dates.first, let last = dates.last else { return "" }
        return "\(formatDisplay(first)) – \(formatDisplay(last))"
    }

    static func classifyBranch(cityName: String, idForm: String? = nil) -> String {
        let lower = cityName.lowercased()
        if lower.contains("колледж") || idForm == "430" || idForm == "2" {
            return "COLLEGE"
        }
        if lower.contains("школа") || idForm == "607" {
            return "SCHOOL"
        }
        if lower.contains("онлайн") || lower.contains("online") {
            return "ONLINE"
        }
        return "ACADEMY"
    }

    static func getBranchColor(category: String) -> String {
        switch category {
        case "COLLEGE": return "#3b82f6"
        case "SCHOOL": return "#10b981"
        case "ONLINE": return "#8b5cf6"
        default: return "#f59e0b"
        }
    }

    static func parseSchedule(rawData: Data, branch: Branch, weekOffset: Int) -> [Lesson] {
        guard let root = try? JSONSerialization.jsonObject(with: rawData) as? [String: Any],
              let body = root["body"] as? [String: Any] else {
            return []
        }

        let weekDates = getWeekDates(weekOffset: weekOffset)
        guard weekDates.count == 7 else { return [] }

        var lessons: [Lesson] = []
        for (lentaKey, dayAny) in body {
            guard let dayObj = dayAny as? [String: Any] else { continue }
            for (dayKey, lessonAny) in dayObj {
                guard let lessonObj = lessonAny as? [String: Any],
                      let weekdayNum = Int(dayKey),
                      (1...7).contains(weekdayNum) else {
                    continue
                }

                let dateIso = formatISO(weekDates[weekdayNum - 1])
                let lentaNum = intValue(lessonObj["lenta"]) ?? Int(lentaKey) ?? 1

                let rawSubject = cleanString(lessonObj["name_spec"])
                let eventName = cleanString(lessonObj["eventName"])
                let eventSubtypeName = cleanString(lessonObj["eventSubtypeName"])

                let eventTitle: String
                if !eventName.isEmpty && !eventSubtypeName.isEmpty {
                    eventTitle = "\(eventName) / \(eventSubtypeName)"
                } else if !eventName.isEmpty {
                    eventTitle = eventName
                } else if !eventSubtypeName.isEmpty {
                    eventTitle = eventSubtypeName
                } else {
                    eventTitle = ""
                }

                let subject: String
                if !rawSubject.isEmpty {
                    subject = rawSubject
                } else if !eventTitle.isEmpty {
                    subject = eventTitle
                } else {
                    subject = "Занятие"
                }

                let group = cleanString(lessonObj["groups"])
                let room = cleanString(lessonObj["num_rooms"])
                let startTime = cleanString(lessonObj["l_start"])
                let endTime = cleanString(lessonObj["l_end"])
                let idForm = cleanString(lessonObj["id_form"])

                if !rawSubject.isEmpty || !eventTitle.isEmpty || !group.isEmpty || (!startTime.isEmpty && !room.isEmpty) {
                    let category = classifyBranch(cityName: branch.cityName, idForm: idForm.isEmpty ? nil : idForm)
                    let color = branch.colorHex.isEmpty ? getBranchColor(category: category) : branch.colorHex
                    let roomDisplay = (!room.isEmpty && !room.lowercased().hasPrefix("ауд")) ? "ауд. \(room)" : room
                    let id = "\(dateIso)_\(lentaNum)_\(branch.cityId)_\(group)_\(deterministicHash(subject))"

                    lessons.append(
                        Lesson(
                            id: id,
                            date: dateIso,
                            dayOfWeek: weekdayNum,
                            lentaNum: lentaNum,
                            startTime: startTime,
                            endTime: endTime,
                            subject: subject,
                            room: roomDisplay,
                            group: group,
                            branchId: branch.cityId,
                            branchName: branch.cityName,
                            branchCategory: category,
                            branchColor: color,
                            isAttendanceMarked: false
                        )
                    )
                }
            }
        }
        return lessons
    }

    static func buildWeekSchedule(allLessons: [Lesson], weekOffset: Int) -> WeekSchedule {
        let weekDates = getWeekDates(weekOffset: weekOffset)
        let days: [DaySchedule] = (1...7).compactMap { weekdayNum in
            guard weekdayNum - 1 < weekDates.count else { return nil }
            let dateIso = formatISO(weekDates[weekdayNum - 1])
            let dayLessons = allLessons
                .filter { $0.date == dateIso }
                .sorted {
                    if $0.lentaNum != $1.lentaNum { return $0.lentaNum < $1.lentaNum }
                    return $0.startTime < $1.startTime
                }
            return DaySchedule(date: dateIso, dayOfWeek: weekdayNum, lessons: dayLessons)
        }
        return WeekSchedule(
            weekOffset: weekOffset,
            weekRangeLabel: getWeekRangeLabel(weekOffset: weekOffset),
            days: days
        )
    }

    static func getInitialWeekSchedule(weekOffset: Int) -> WeekSchedule {
        let weekDates = getWeekDates(weekOffset: weekOffset)
        let days: [DaySchedule] = (1...7).compactMap { weekdayNum in
            guard weekdayNum - 1 < weekDates.count else { return nil }
            return DaySchedule(date: formatISO(weekDates[weekdayNum - 1]), dayOfWeek: weekdayNum, lessons: [])
        }
        return WeekSchedule(
            weekOffset: weekOffset,
            weekRangeLabel: getWeekRangeLabel(weekOffset: weekOffset),
            days: days
        )
    }

    static func parseTasks(rawData: Data) -> TaskQueue {
        guard let root = try? JSONSerialization.jsonObject(with: rawData) as? [String: Any] else {
            return TaskQueue()
        }

        let dzStud = root["dzStud"] as? [String: Any]
        let reviewsStud = root["reviewsStud"] as? [String: Any]

        let newHw = intValue(dzStud?["new_home_work"]) ?? 0
        let pb = dzStud?["progress_bar"] as? [String: Any]
        let factHours = intValue(pb?["factHours"]) ?? 0
        let factDays = intValue(pb?["factDays"]) ?? 0

        let actualHwDays = factHours >= 24 ? factHours / 24 : 0
        let actualHwHours = (1...23).contains(factHours) ? factHours : ((1...23).contains(factDays) && factHours == 0 ? factDays : 0)

        let countReviews = intValue(reviewsStud?["count_students"]) ?? 0
        let reviewPb = reviewsStud?["progress_bar"] as? [String: Any]
        let reviewDays = intValue(reviewPb?["factDays"]) ?? 0

        var studentsList: [ReviewStudent] = []
        if let studentsArr = reviewsStud?["students_list"] as? [[String: Any]] {
            for s in studentsArr {
                let name = firstNonEmpty(cleanString(s["fio_stud"]), cleanString(s["fio_student"]), cleanString(s["name"])) ?? "Студент"
                let group = firstNonEmpty(cleanString(s["name_tgroups"]), cleanString(s["group_name"]), cleanString(s["group"])) ?? ""
                let subject = firstNonEmpty(cleanString(s["name_spec"]), cleanString(s["spec_name"]), cleanString(s["subject"])) ?? ""
                let deadline = firstNonEmpty(cleanString(s["due_date"]), cleanString(s["date_limit"]), cleanString(s["date_vizit"])) ?? ""
                studentsList.append(ReviewStudent(name: name, group: group, subject: subject, deadline: deadline))
            }
        }

        return TaskQueue(
            newHomeworkCount: newHw,
            pendingReviewsCount: countReviews,
            oldestHwWaitDays: actualHwDays,
            oldestReviewWaitDays: reviewDays,
            oldestHwWaitHours: actualHwHours,
            reviewStudents: studentsList
        )
    }

    static func parseAttendanceStatus(rawData: Data) -> Bool? {
        guard let root = try? JSONSerialization.jsonObject(with: rawData) as? [String: Any] else {
            return nil
        }

        let studentsArr = root["students"] as? [[String: Any]]
        let scheduleArr = root["schedule"] as? [Any]
        let groupsObj = root["groups"] as? [String: Any]
        let dataObj = root["data"] as? [String: Any]
        let vizitInfo = (root["vizit_info"] as? [String: Any])
            ?? (dataObj?["vizit_info"] as? [String: Any])
            ?? studentsArr?.first

        if (studentsArr?.isEmpty ?? true) &&
            (scheduleArr?.isEmpty ?? true) &&
            groupsObj == nil &&
            vizitInfo == nil {
            return nil
        }

        let primaryTeachVal = intValue(vizitInfo?["primary_teach"]) ?? intValue(root["primary_teach"])
        let isPrimaryTeacher = primaryTeachVal == 0

        let theme = firstNonEmpty(
            cleanString(vizitInfo?["theme"]),
            cleanString(vizitInfo?["setting_theme"]),
            cleanString(root["theme"]),
            cleanString(root["setting_theme"])
        ) ?? ""

        let visitId = intValue(vizitInfo?["id_vizit"]) ?? intValue(studentsArr?.first?["id_vizit"]) ?? 0
        let hasVisitId = visitId > 0

        if (isPrimaryTeacher || hasVisitId) && !theme.isEmpty {
            return true
        }
        return false
    }

    private static func deterministicHash(_ string: String) -> Int32 {
        var h: Int32 = 0
        for codeUnit in string.utf16 {
            h = 31 &* h &+ Int32(codeUnit)
        }
        return h
    }

    private static func cleanString(_ value: Any?) -> String {
        guard let value = value, !(value is NSNull) else { return "" }
        let str = String(describing: value).trimmingCharacters(in: .whitespacesAndNewlines)
        return str.caseInsensitiveCompare("null") == .orderedSame ? "" : str
    }

    private static func intValue(_ value: Any?) -> Int? {
        if let i = value as? Int { return i }
        if let d = value as? Double { return Int(d) }
        if let s = value as? String { return Int(s.trimmingCharacters(in: .whitespacesAndNewlines)) }
        return nil
    }

    private static func firstNonEmpty(_ values: String...) -> String? {
        values.first { !$0.isEmpty }
    }
}
