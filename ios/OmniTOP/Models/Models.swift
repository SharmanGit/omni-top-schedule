import Foundation

struct Branch: Codable, Identifiable, Hashable {
    var id: String { cityId }
    let cityId: String
    let cityName: String
    let category: String // "COLLEGE", "ACADEMY", "SCHOOL", "ONLINE"
    let colorHex: String
}

struct Lesson: Codable, Identifiable, Hashable {
    let id: String
    let date: String        // "YYYY-MM-DD"
    let dayOfWeek: Int      // 1..7 (1 = Mon)
    let lentaNum: Int
    let startTime: String   // "09:40"
    let endTime: String     // "11:10"
    let subject: String
    let room: String
    let group: String
    let branchId: String
    let branchName: String
    let branchCategory: String
    let branchColor: String
    var isAttendanceMarked: Bool = false
}

enum LessonStatus {
    case liveActive
    case nextUpcoming
    case scheduled
    case pastMarked
    case pastUnmarked
}

struct TeacherProfile: Codable {
    let id: String
    let name: String
    let email: String
    let branches: [Branch]
}

struct ReviewStudent: Codable, Identifiable, Hashable {
    var id: String { "\(name)_\(group)_\(subject)" }
    let name: String
    let group: String
    let subject: String
    let deadline: String
}

struct TaskQueue: Codable {
    var newHomeworkCount: Int = 0
    var pendingReviewsCount: Int = 0
    var oldestHwWaitDays: Int = 0
    var oldestReviewWaitDays: Int = 0
    var oldestHwWaitHours: Int = 0
    var reviewStudents: [ReviewStudent] = []
}

struct DaySchedule: Codable, Identifiable {
    var id: Int { dayOfWeek }
    let date: String
    let dayOfWeek: Int
    var lessons: [Lesson]
}

struct WeekSchedule: Codable {
    let weekOffset: Int
    let weekRangeLabel: String
    var days: [DaySchedule]
}
