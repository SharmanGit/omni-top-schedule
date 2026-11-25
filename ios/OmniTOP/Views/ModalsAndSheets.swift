import SwiftUI

// MARK: - Lesson Detail

struct LessonDetailSheet: View {
    let lesson: Lesson
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section("Занятие") {
                    LabeledContent("Предмет", value: lesson.subject)
                    LabeledContent("Дата", value: lesson.date)
                    LabeledContent("Время", value: "\(lesson.startTime) – \(lesson.endTime) (\(lesson.lentaNum) пара)")
                    if !lesson.group.isEmpty {
                        LabeledContent("Группа", value: lesson.group)
                    }
                    if !lesson.room.isEmpty {
                        LabeledContent("Аудитория", value: lesson.room)
                    }
                }

                Section("Филиал и отметка") {
                    LabeledContent("Филиал", value: lesson.branchName)
                    LabeledContent("Часовой пояс", value: BranchTimeZoneResolver.resolveTimeZone(branchName: lesson.branchName).identifier)
                    LabeledContent("Посещаемость", value: lesson.isAttendanceMarked ? "Отмечена" : "Нет данных / не отмечена")
                }

                Section {
                    Link(destination: URL(string: OmniApiClient.baseURL)!) {
                        Label("Открыть в Omni TOP", systemImage: "safari")
                    }
                }
            }
            .navigationTitle("Детали пары")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { dismiss() }
                }
            }
        }
    }
}

// MARK: - Tasks Sheet

struct TasksSheet: View {
    @EnvironmentObject private var viewModel: MainViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section("Сводка") {
                    LabeledContent("Новые ДЗ на проверке", value: "\(viewModel.taskQueue.newHomeworkCount)")
                    if viewModel.taskQueue.oldestHwWaitDays > 0 {
                        LabeledContent("Макс. ожидание ДЗ", value: "\(viewModel.taskQueue.oldestHwWaitDays) дн.")
                    } else if viewModel.taskQueue.oldestHwWaitHours > 0 {
                        LabeledContent("Макс. ожидание ДЗ", value: "\(viewModel.taskQueue.oldestHwWaitHours) ч.")
                    }
                    LabeledContent("Отзывы о студентах", value: "\(viewModel.taskQueue.pendingReviewsCount)")
                    if viewModel.taskQueue.oldestReviewWaitDays > 0 {
                        LabeledContent("Макс. ожидание отзывов", value: "\(viewModel.taskQueue.oldestReviewWaitDays) дн.")
                    }
                }

                if !viewModel.taskQueue.reviewStudents.isEmpty {
                    Section("Очередь отзывов") {
                        ForEach(sortedStudents) { student in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(student.name)
                                    .font(.subheadline.bold())
                                HStack {
                                    if !student.group.isEmpty {
                                        Text(student.group)
                                    }
                                    if !student.subject.isEmpty {
                                        Text("• \(student.subject)")
                                    }
                                }
                                .font(.caption)
                                .foregroundColor(.secondary)
                                if !student.deadline.isEmpty {
                                    Text("Срок: \(student.deadline)")
                                        .font(.caption2)
                                        .foregroundColor(OmniTheme.accent)
                                }
                            }
                            .padding(.vertical, 2)
                        }
                    }
                }

                Section {
                    Link(destination: URL(string: OmniApiClient.baseURL)!) {
                        Label("Открыть в Omni TOP", systemImage: "safari")
                    }
                }
            }
            .navigationTitle("Задачи")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Закрыть") { dismiss() }
                }
            }
        }
    }

    private var sortedStudents: [ReviewStudent] {
        viewModel.taskQueue.reviewStudents.sorted { lhs, rhs in
            if lhs.deadline.isEmpty != rhs.deadline.isEmpty {
                return !lhs.deadline.isEmpty
            }
            return lhs.deadline < rhs.deadline
        }
    }
}

// MARK: - Sources (Branch Filter) Sheet

struct SourcesSheet: View {
    @EnvironmentObject private var viewModel: MainViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Button {
                        viewModel.clearBranchFilter()
                    } label: {
                        HStack {
                            Text("Все филиалы")
                            Spacer()
                            if viewModel.selectedBranchIds.isEmpty {
                                Image(systemName: "checkmark")
                                    .foregroundColor(OmniTheme.accent)
                            }
                        }
                    }
                }

                Section("Филиалы") {
                    ForEach(viewModel.branches) { branch in
                        Button {
                            viewModel.toggleBranchFilter(branch.cityId)
                        } label: {
                            HStack {
                                Circle()
                                    .fill(Color(hex: branch.colorHex))
                                    .frame(width: 10, height: 10)
                                Text(branch.cityName)
                                    .foregroundColor(.primary)
                                Spacer()
                                if viewModel.selectedBranchIds.contains(branch.cityId) {
                                    Image(systemName: "checkmark")
                                        .foregroundColor(OmniTheme.accent)
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Фильтр филиалов")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { dismiss() }
                }
            }
        }
    }
}

// MARK: - Profile & Calendar Sync Sheet

struct ProfileSheet: View {
    @EnvironmentObject private var viewModel: MainViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                if let profile = viewModel.profile {
                    Section("Преподаватель") {
                        LabeledContent("Имя", value: profile.name)
                        LabeledContent("Аккаунт", value: profile.email)
                        LabeledContent("Филиалов", value: "\(viewModel.branches.count)")
                    }
                }

                Section("Оформление") {
                    Toggle("Тёмная тема", isOn: Binding(
                        get: { viewModel.isDarkTheme },
                        set: { _ in viewModel.toggleTheme() }
                    ))
                }

                Section("Синхронизация с Календарём iPhone") {
                    Toggle("Выгружать расписание в Календарь", isOn: Binding(
                        get: { viewModel.calendarSyncEnabled },
                        set: { newValue in
                            Task { await viewModel.toggleCalendarSync(enabled: newValue) }
                        }
                    ))

                    if viewModel.calendarSyncEnabled && !viewModel.availableCalendars.isEmpty {
                        Picker("Целевой календарь", selection: Binding(
                            get: { viewModel.selectedCalendarId ?? "" },
                            set: { newId in
                                Task { await viewModel.selectCalendar(newId) }
                            }
                        )) {
                            ForEach(viewModel.availableCalendars) { cal in
                                Text(cal.accountName.isEmpty ? cal.title : "\(cal.title) (\(cal.accountName))")
                                    .tag(cal.calendarIdentifier)
                            }
                        }

                        Button {
                            Task { await viewModel.syncCalendarNow() }
                        } label: {
                            HStack {
                                Label("Синхронизировать сейчас", systemImage: "arrow.triangle.2.circlepath")
                                Spacer()
                                if viewModel.isCalendarSyncing {
                                    ProgressView()
                                } else if viewModel.calendarLastSyncedCount > 0 {
                                    Text("\(viewModel.calendarLastSyncedCount)")
                                        .foregroundColor(.secondary)
                                }
                            }
                        }

                        Button(role: .destructive) {
                            viewModel.clearCalendarEventsNow()
                        } label: {
                            Label("Удалить все события Omni TOP", systemImage: "trash")
                        }
                    }

                    if let msg = viewModel.calendarSyncMessage {
                        Text(msg)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }

                Section("О приложении") {
                    LabeledContent("Версия", value: "2.0.2 (iOS)")
                }

                Section {
                    Button(role: .destructive) {
                        viewModel.logout()
                        dismiss()
                    } label: {
                        Label("Выйти из аккаунта", systemImage: "rectangle.portrait.and.arrow.right")
                    }
                }
            }
            .navigationTitle("Профиль")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear {
                viewModel.refreshCalendarState()
            }
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { dismiss() }
                }
            }
        }
    }
}
