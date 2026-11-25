import SwiftUI
import WidgetKit

struct OmniTimelineEntry: TimelineEntry {
    let date: Date
    let todayLessons: [Lesson]
    let currentLesson: Lesson?
    let nextLesson: Lesson?
}

struct OmniScheduleProvider: TimelineProvider {
    private let store = ScheduleStore.shared

    func placeholder(in context: Context) -> OmniTimelineEntry {
        OmniTimelineEntry(date: Date(), todayLessons: [], currentLesson: nil, nextLesson: nil)
    }

    func getSnapshot(in context: Context, completion: @escaping (OmniTimelineEntry) -> Void) {
        completion(buildEntry(at: Date()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<OmniTimelineEntry>) -> Void) {
        let now = Date()
        let entry = buildEntry(at: now)
        let nextUpdate = Calendar.current.date(byAdding: .minute, value: 15, to: now) ?? now.addingTimeInterval(900)
        completion(Timeline(entries: [entry], policy: .after(nextUpdate)))
    }

    private func buildEntry(at now: Date) -> OmniTimelineEntry {
        let allLessons = store.loadLessons()
        let todayLessons = allLessons
            .filter { lesson in
                let branchToday = BranchTimeZoneResolver.branchLocalTodayISO(branchName: lesson.branchName, now: now)
                return lesson.date == branchToday
            }
            .sorted {
                let d0 = BranchTimeZoneResolver.lessonStartDate($0) ?? .distantFuture
                let d1 = BranchTimeZoneResolver.lessonStartDate($1) ?? .distantFuture
                return d0 < d1
            }

        let currentLesson = todayLessons.first {
            LessonStateCalculator.determineStatus(lesson: $0, todayLessons: todayLessons, now: now) == .liveActive
        }

        let nextLesson = todayLessons.first {
            guard let start = BranchTimeZoneResolver.lessonStartDate($0) else { return false }
            return start > now
        }

        return OmniTimelineEntry(
            date: now,
            todayLessons: todayLessons,
            currentLesson: currentLesson,
            nextLesson: nextLesson
        )
    }
}

struct OmniScheduleWidgetEntryView: View {
    var entry: OmniScheduleProvider.Entry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        Group {
            switch family {
            case .systemSmall:
                smallView
            case .systemMedium:
                mediumView
            default:
                largeView
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .widgetBackground(Color(red: 0.086, green: 0.106, blue: 0.133))
    }

    private var smallView: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("OMNI TOP")
                .font(.caption2.bold())
                .foregroundColor(Color(red: 0.96, green: 0.62, blue: 0.04))

            if let lesson = entry.currentLesson ?? entry.nextLesson {
                Text(entry.currentLesson != nil ? "Сейчас" : "Далее")
                    .font(.caption2.bold())
                    .foregroundColor(entry.currentLesson != nil ? .green : .blue)
                Text(lesson.subject)
                    .font(.footnote.bold())
                    .foregroundColor(.white)
                    .lineLimit(2)
                Spacer()
                Text("\(lesson.startTime)–\(lesson.endTime)")
                    .font(.caption.bold())
                    .foregroundColor(.gray)
                if !lesson.room.isEmpty {
                    Text(lesson.room)
                        .font(.caption2)
                        .foregroundColor(.gray)
                        .lineLimit(1)
                }
            } else {
                Spacer()
                Text("Пар больше нет")
                    .font(.footnote)
                    .foregroundColor(.gray)
                Spacer()
            }
        }
    }

    private var mediumView: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Расписание на сегодня")
                    .font(.caption.bold())
                    .foregroundColor(Color(red: 0.96, green: 0.62, blue: 0.04))
                Spacer()
                Text("\(entry.todayLessons.count) пар")
                    .font(.caption2)
                    .foregroundColor(.gray)
            }

            if entry.todayLessons.isEmpty {
                Spacer()
                Text("На сегодня занятий нет")
                    .font(.subheadline)
                    .foregroundColor(.gray)
                Spacer()
            } else {
                ForEach(entry.todayLessons.prefix(3)) { lesson in
                    HStack(spacing: 8) {
                        Text(lesson.startTime)
                            .font(.caption.bold())
                            .foregroundColor(.white)
                            .frame(width: 42, alignment: .leading)
                        Text(lesson.subject)
                            .font(.caption)
                            .foregroundColor(.white)
                            .lineLimit(1)
                        Spacer()
                        Text(lesson.room)
                            .font(.caption2)
                            .foregroundColor(.gray)
                            .lineLimit(1)
                    }
                }
                Spacer(minLength: 0)
            }
        }
    }

    private var largeView: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("OMNI TOP • Сегодня")
                    .font(.subheadline.bold())
                    .foregroundColor(Color(red: 0.96, green: 0.62, blue: 0.04))
                Spacer()
                Text("\(entry.todayLessons.count) пар")
                    .font(.caption)
                    .foregroundColor(.gray)
            }

            if entry.todayLessons.isEmpty {
                Spacer()
                Text("На сегодня занятий нет")
                    .font(.subheadline)
                    .foregroundColor(.gray)
                Spacer()
            } else {
                ForEach(entry.todayLessons.prefix(6)) { lesson in
                    let status = LessonStateCalculator.determineStatus(lesson: lesson, todayLessons: entry.todayLessons, now: entry.date)
                    HStack(spacing: 8) {
                        Circle()
                            .fill(status == .liveActive ? Color.green : (status == .nextUpcoming ? Color.blue : Color.gray))
                            .frame(width: 7, height: 7)
                        Text("\(lesson.startTime)–\(lesson.endTime)")
                            .font(.caption2.bold())
                            .foregroundColor(.gray)
                            .frame(width: 74, alignment: .leading)
                        Text(lesson.subject)
                            .font(.caption)
                            .foregroundColor(.white)
                            .lineLimit(1)
                        Spacer()
                        Text(lesson.group)
                            .font(.caption2)
                            .foregroundColor(.gray)
                            .lineLimit(1)
                    }
                }
                Spacer(minLength: 0)
            }
        }
    }
}

@main
struct OmniTOPWidgetBundle: WidgetBundle {
    var body: some Widget {
        OmniScheduleWidget()
    }
}

struct OmniScheduleWidget: Widget {
    let kind: String = "OmniScheduleWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: OmniScheduleProvider()) { entry in
            OmniScheduleWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Omni TOP Расписание")
        .description("Текущая и ближайшие пары преподавателя.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

private extension View {
    @ViewBuilder
    func widgetBackground(_ color: Color) -> some View {
        if #available(iOS 17.0, *) {
            containerBackground(color, for: .widget)
        } else {
            background(color)
        }
    }
}
