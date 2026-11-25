import SwiftUI

struct MainView: View {
    @EnvironmentObject private var viewModel: MainViewModel
    @State private var selectedLesson: Lesson? = nil
    @State private var showTasksSheet = false
    @State private var showSourcesSheet = false
    @State private var showProfileSheet = false

    private let dayNames = ["ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС"]

    var body: some View {
        ZStack(alignment: .bottom) {
            OmniTheme.background(isDark: viewModel.isDarkTheme)
                .ignoresSafeArea()

            VStack(spacing: 0) {
                headerBar
                weekNavBar
                dayRibbon

                if let error = viewModel.errorMessage {
                    Text(error)
                        .font(.caption)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                        .padding(.horizontal, 12)
                        .background(OmniTheme.unmarkedRed.opacity(0.9))
                }

                lessonsList
            }

            bottomLocationBar
        }
        .sheet(item: $selectedLesson) { lesson in
            LessonDetailSheet(lesson: lesson)
        }
        .sheet(isPresented: $showTasksSheet) {
            TasksSheet()
        }
        .sheet(isPresented: $showSourcesSheet) {
            SourcesSheet()
        }
        .sheet(isPresented: $showProfileSheet) {
            ProfileSheet()
        }
    }

    // MARK: - Header

    private var headerBar: some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Omni TOP")
                    .font(.title3.bold())
                    .foregroundColor(OmniTheme.textPrimary(isDark: viewModel.isDarkTheme))
                Text("Обновлено: \(viewModel.lastSyncText)")
                    .font(.caption2)
                    .foregroundColor(OmniTheme.textSecondary(isDark: viewModel.isDarkTheme))
            }

            Spacer()

            Button {
                showTasksSheet = true
            } label: {
                HStack(spacing: 5) {
                    Image(systemName: "checklist")
                        .font(.caption.bold())
                    let totalTasks = viewModel.taskQueue.newHomeworkCount + viewModel.taskQueue.pendingReviewsCount
                    Text("\(totalTasks)")
                        .font(.caption.bold())
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(OmniTheme.surfaceSecondary(isDark: viewModel.isDarkTheme))
                .foregroundColor(OmniTheme.accent)
                .clipShape(Capsule())
            }

            Button {
                Task { await viewModel.refreshAll(silent: false) }
            } label: {
                if viewModel.isRefreshing {
                    ProgressView()
                        .frame(width: 32, height: 32)
                } else {
                    Image(systemName: "arrow.clockwise")
                        .font(.body.weight(.semibold))
                        .foregroundColor(OmniTheme.textPrimary(isDark: viewModel.isDarkTheme))
                        .frame(width: 32, height: 32)
                }
            }

            Button {
                showProfileSheet = true
            } label: {
                Image(systemName: "person.crop.circle.fill")
                    .font(.title2)
                    .foregroundColor(OmniTheme.accent)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(OmniTheme.surface(isDark: viewModel.isDarkTheme))
    }

    // MARK: - Week Nav

    private var weekNavBar: some View {
        HStack {
            Button {
                viewModel.selectWeek(offset: viewModel.weekOffset - 1)
            } label: {
                Image(systemName: "chevron.left")
                    .padding(8)
            }

            Spacer()

            Button {
                viewModel.selectWeek(offset: 0)
            } label: {
                VStack(spacing: 1) {
                    Text(viewModel.weekSchedule.weekRangeLabel)
                        .font(.subheadline.bold())
                        .foregroundColor(OmniTheme.textPrimary(isDark: viewModel.isDarkTheme))
                    if viewModel.weekOffset != 0 {
                        Text(viewModel.weekOffset > 0 ? "+\(viewModel.weekOffset) нед." : "\(viewModel.weekOffset) нед.")
                            .font(.caption2)
                            .foregroundColor(OmniTheme.accent)
                    }
                }
            }

            Spacer()

            Button {
                viewModel.selectWeek(offset: viewModel.weekOffset + 1)
            } label: {
                Image(systemName: "chevron.right")
                    .padding(8)
            }
        }
        .foregroundColor(OmniTheme.textPrimary(isDark: viewModel.isDarkTheme))
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(OmniTheme.surface(isDark: viewModel.isDarkTheme))
    }

    // MARK: - Day Ribbon

    private var dayRibbon: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(0..<min(7, viewModel.weekSchedule.days.count), id: \.self) { index in
                    let day = viewModel.weekSchedule.days[index]
                    let isSelected = index == viewModel.selectedDayIndex
                    let dayNum = day.date.split(separator: "-").last.map(String.init) ?? ""
                    let count = day.lessons.filter {
                        viewModel.selectedBranchIds.isEmpty || viewModel.selectedBranchIds.contains($0.branchId)
                    }.count

                    Button {
                        viewModel.selectedDayIndex = index
                    } label: {
                        VStack(spacing: 4) {
                            Text(dayNames[index])
                                .font(.caption2.bold())
                            Text(dayNum)
                                .font(.subheadline.bold())
                            Circle()
                                .fill(count > 0 ? (isSelected ? Color.black : OmniTheme.accent) : Color.clear)
                                .frame(width: 5, height: 5)
                        }
                        .frame(width: 46, height: 58)
                        .background(isSelected ? OmniTheme.accent : OmniTheme.surfaceSecondary(isDark: viewModel.isDarkTheme))
                        .foregroundColor(isSelected ? .black : OmniTheme.textPrimary(isDark: viewModel.isDarkTheme))
                        .cornerRadius(12)
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
    }

    // MARK: - Lessons List

    private var lessonsList: some View {
        let lessons = viewModel.filteredLessonsForSelectedDay()
        let todayAllLessons = viewModel.selectedDayIndex < viewModel.weekSchedule.days.count
            ? viewModel.weekSchedule.days[viewModel.selectedDayIndex].lessons
            : []

        return Group {
            if lessons.isEmpty {
                VStack(spacing: 12) {
                    Spacer()
                    Image(systemName: "cup.and.saucer.fill")
                        .font(.system(size: 40))
                        .foregroundColor(OmniTheme.textSecondary(isDark: viewModel.isDarkTheme))
                    Text("На этот день занятий нет")
                        .font(.headline)
                        .foregroundColor(OmniTheme.textSecondary(isDark: viewModel.isDarkTheme))
                    Spacer()
                }
                .padding(.bottom, 70)
            } else {
                ScrollView {
                    LazyVStack(spacing: 10) {
                        ForEach(lessons) { lesson in
                            let status = LessonStateCalculator.determineStatus(
                                lesson: lesson,
                                todayLessons: todayAllLessons
                            )
                            LessonCardView(
                                lesson: lesson,
                                status: status,
                                isDark: viewModel.isDarkTheme
                            )
                            .onTapGesture {
                                selectedLesson = lesson
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 4)
                    .padding(.bottom, 84)
                }
                .refreshable {
                    await viewModel.refreshAll(silent: false)
                }
            }
        }
    }

    // MARK: - Bottom Bar

    private var bottomLocationBar: some View {
        HStack {
            Button {
                showSourcesSheet = true
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "building.2.fill")
                        .foregroundColor(OmniTheme.accent)
                    if viewModel.selectedBranchIds.isEmpty {
                        Text("Все филиалы (\(viewModel.branches.count))")
                    } else {
                        Text("Выбрано филиалов: \(viewModel.selectedBranchIds.count)")
                    }
                    Spacer()
                    Image(systemName: "line.3.horizontal.decrease.circle")
                        .foregroundColor(OmniTheme.accent)
                }
                .font(.subheadline.weight(.medium))
                .foregroundColor(OmniTheme.textPrimary(isDark: viewModel.isDarkTheme))
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(OmniTheme.surface(isDark: viewModel.isDarkTheme))
                .cornerRadius(16)
                .shadow(color: Color.black.opacity(0.25), radius: 8, y: 2)
            }
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 10)
    }
}

private struct LessonCardView: View {
    let lesson: Lesson
    let status: LessonStatus
    let isDark: Bool

    var body: some View {
        HStack(spacing: 0) {
            Rectangle()
                .fill(Color(hex: lesson.branchColor))
                .frame(width: 5)

            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("\(lesson.lentaNum) пара • \(lesson.startTime) – \(lesson.endTime)")
                        .font(.caption.bold())
                        .foregroundColor(OmniTheme.textSecondary(isDark: isDark))

                    Spacer()

                    statusBadge
                }

                Text(lesson.subject)
                    .font(.headline)
                    .foregroundColor(OmniTheme.textPrimary(isDark: isDark))
                    .lineLimit(2)

                HStack(spacing: 12) {
                    if !lesson.group.isEmpty {
                        Label(lesson.group, systemImage: "person.3.fill")
                    }
                    if !lesson.room.isEmpty {
                        Label(lesson.room, systemImage: "mappin.and.ellipse")
                    }
                }
                .font(.caption)
                .foregroundColor(OmniTheme.textSecondary(isDark: isDark))

                Text(lesson.branchName)
                    .font(.caption2.bold())
                    .foregroundColor(Color(hex: lesson.branchColor))
            }
            .padding(14)
        }
        .background(OmniTheme.surface(isDark: isDark))
        .cornerRadius(14)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(borderColor, lineWidth: status == .liveActive ? 1.5 : 0)
        )
    }

    private var borderColor: Color {
        status == .liveActive ? OmniTheme.liveGreen : .clear
    }

    @ViewBuilder
    private var statusBadge: some View {
        switch status {
        case .liveActive:
            badge("ИДЁТ СЕЙЧАС", color: OmniTheme.liveGreen)
        case .nextUpcoming:
            badge("СЛЕДУЮЩАЯ", color: OmniTheme.nextBlue)
        case .pastMarked:
            badge("ОТМЕЧЕНО", color: OmniTheme.liveGreen.opacity(0.8))
        case .pastUnmarked:
            badge("НЕ ОТМЕЧЕНО", color: OmniTheme.unmarkedRed)
        case .scheduled:
            EmptyView()
        }
    }

    private func badge(_ text: String, color: Color) -> some View {
        Text(text)
            .font(.system(size: 10, weight: .bold))
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(color.opacity(0.18))
            .foregroundColor(color)
            .clipShape(Capsule())
    }
}
