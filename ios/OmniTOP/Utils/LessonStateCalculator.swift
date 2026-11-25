import Foundation

enum LessonStateCalculator {
    static func determineStatus(
        lesson: Lesson,
        todayLessons: [Lesson],
        now: Date = Date()
    ) -> LessonStatus {
        let branchToday = BranchTimeZoneResolver.branchLocalTodayISO(branchName: lesson.branchName, now: now)

        if !branchToday.isEmpty {
            if lesson.date < branchToday {
                return lesson.isAttendanceMarked ? .pastMarked : .pastUnmarked
            }
            if lesson.date > branchToday {
                return .scheduled
            }
        }

        if let start = BranchTimeZoneResolver.lessonStartDate(lesson),
           let end = BranchTimeZoneResolver.lessonEndDate(lesson) {
            if now >= start && now <= end {
                return .liveActive
            }
            if now > end {
                return lesson.isAttendanceMarked ? .pastMarked : .pastUnmarked
            }
        }

        let nextUpcoming = todayLessons
            .compactMap { candidate -> (Lesson, Date)? in
                guard let cStart = BranchTimeZoneResolver.lessonStartDate(candidate),
                      cStart > now else {
                    return nil
                }
                return (candidate, cStart)
            }
            .min(by: { $0.1 < $1.1 })?
            .0

        if nextUpcoming?.id == lesson.id {
            return .nextUpcoming
        }

        return .scheduled
    }
}
