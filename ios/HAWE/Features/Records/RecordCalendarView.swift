import SwiftUI

/// 하루치 기록 (15 캘린더 칸)
struct DayRecord: Hashable {
    var date: Date
    var type: MissionType
    var topic: String
    var doneCount: Int
    var memberCount: Int
    /// 내가 그날 올렸는지 — 안 올린 날은 결과를 볼 수 없어요 (CLAUDE.md §5-2, 사용자 결정 2026-10-12)
    var didSubmit: Bool
    /// '모아보기'로 열 미션
    var missionID: UUID? = nil
}

/// 15 기록 캘린더 (Figma 1:1311)
/// 그룹별 월간 캘린더. 기록 있는 날에 유형 이름(그림/사진/글/음성), 오늘 테두리,
/// 날짜를 고르면 아래 카드 → '모아보기'로 그날 결과 공개 화면 (내가 올린 날만)
struct RecordCalendarView: View {
    let groupID: UUID
    @Binding var path: [MainRoute]

    @Environment(\.dismiss) private var dismiss
    @Environment(DataStore.self) private var store
    @State private var month: Date
    @State private var selected: Date?

    private static let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TodayMission.seoul
        calendar.locale = Locale(identifier: "ko_KR")
        calendar.firstWeekday = 1
        return calendar
    }()

    init(groupID: UUID, path: Binding<[MainRoute]>) {
        self.groupID = groupID
        self._path = path
        self._month = State(initialValue: Self.calendar.dateInterval(of: .month, for: .now)?.start ?? .now)
    }

    private var group: GroupDetail? { store.group(groupID) }

    /// 날짜(그날 0시) → 기록
    private var records: [Date: DayRecord] {
        Dictionary(store.records(groupID).map { (calendar.startOfDay(for: $0.date), $0) }, uniquingKeysWith: { a, _ in a })
    }

    private var calendar: Calendar { Self.calendar }
    private var today: Date { calendar.startOfDay(for: .now) }

    var body: some View {
        VStack(spacing: 0) {
            HNavBar("우리의 기록", leading: .back { dismiss() })

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    monthHeader.padding(.top, 12)
                    grid.padding(.top, 12)
                    if let selected, let record = records[selected] {
                        selectedCard(record).padding(.top, 20)
                    }
                }
                .padding(.bottom, 32)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .padding(.horizontal, HLayout.screenPaddingCompact)
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
        // 처음에는 가장 최근 기록이 있는 날을 골라 둬요.
        .onAppear { if selected == nil { selected = records.keys.max() } }
    }

    // MARK: - Month

    private var firstMonth: Date {
        calendar.dateInterval(of: .month, for: group?.createdAt ?? .now)?.start ?? month
    }

    private var lastMonth: Date {
        calendar.dateInterval(of: .month, for: .now)?.start ?? month
    }

    /// 화살표 20 (44 칸) + "2026년 10월" 18 Bold
    private var monthHeader: some View {
        HStack {
            monthButton(.chevronLeft, label: "이전 달", enabled: month > firstMonth) { moveMonth(-1) }
            Spacer()
            Text(month.formatted(.dateTime.year().month(.defaultDigits).locale(Locale(identifier: "ko_KR"))))
                .font(.plex(18, .bold))
                .foregroundStyle(.ink)
            Spacer()
            monthButton(.chevronRight, label: "다음 달", enabled: month < lastMonth) { moveMonth(1) }
        }
    }

    private func monthButton(_ icon: HIcon, label: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HIconView(icon: icon, size: 20, color: enabled ? .ink : .controlBorder)
                .frame(width: HLayout.minTouch, height: HLayout.minTouch)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel(label)
    }

    private func moveMonth(_ value: Int) {
        guard let next = calendar.date(byAdding: .month, value: value, to: month) else { return }
        month = next
    }

    // MARK: - Grid

    /// 요일 줄 28 + 주마다 칸 52 (모서리 14), 간격 4
    private var grid: some View {
        VStack(spacing: 4) {
            HStack(spacing: 0) {
                ForEach(["일", "월", "화", "수", "목", "금", "토"], id: \.self) { weekday in
                    Text(weekday)
                        .font(.plex(12, .semibold))
                        .foregroundStyle(.textSecondary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 28)
                }
            }
            ForEach(Array(weeks.enumerated()), id: \.offset) { _, week in
                HStack(spacing: 0) {
                    ForEach(Array(week.enumerated()), id: \.offset) { _, day in
                        if let day {
                            dayCell(day)
                        } else {
                            Color.clear.frame(maxWidth: .infinity).frame(height: 52)
                        }
                    }
                }
            }
        }
    }

    /// 이 달의 날짜를 주 단위(일요일 시작)로 나눠요. 빈칸은 nil.
    private var weeks: [[Date?]] {
        guard let range = calendar.range(of: .day, in: .month, for: month) else { return [] }
        let leading = calendar.component(.weekday, from: month) - calendar.firstWeekday
        var days: [Date?] = Array(repeating: nil, count: (leading + 7) % 7)
        days += range.compactMap { calendar.date(byAdding: .day, value: $0 - 1, to: month) }
        while days.count % 7 != 0 { days.append(nil) }
        return stride(from: 0, to: days.count, by: 7).map { Array(days[$0..<$0 + 7]) }
    }

    @ViewBuilder
    private func dayCell(_ date: Date) -> some View {
        let day = calendar.startOfDay(for: date)
        let record = records[day]
        let isFuture = day > today
        let isToday = day == today
        let isSelected = day == selected && record != nil
        let shape = RoundedRectangle(cornerRadius: 14, style: .continuous)

        Button {
            selected = day
        } label: {
            VStack(spacing: 2) {
                Text("\(calendar.component(.day, from: date))")
                    .font(.plex(15, record != nil ? .bold : .medium))
                    .foregroundStyle(isSelected ? Color.onAccent : isFuture ? Color.textDisabled : Color.ink)
                // 글자가 없어도 높이를 맞춰요.
                // 유형 이름을 그대로 써요 (그림·사진·글·음성, 사용자 요청)
                Text(record?.type.displayName ?? " ")
                    .font(.plex(10, .semibold))
                    .foregroundStyle(isSelected ? Color.onAccent : Color.textSecondary)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .background(isSelected ? Color.ink : Color.clear, in: shape)
            .overlay {
                if isToday && !isSelected {
                    shape.strokeBorder(.ink, lineWidth: 1.5)
                }
            }
            .contentShape(shape)
        }
        .buttonStyle(.plain)
        // 기록이 있는 날만 고를 수 있어요.
        .disabled(record == nil)
        .accessibilityLabel(accessibilityText(date: date, record: record, isToday: isToday))
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private func accessibilityText(date: Date, record: DayRecord?, isToday: Bool) -> String {
        var text = date.koreanMonthDay
        if isToday { text += ", 오늘" }
        if let record { text += ", \(record.type.displayName) 기록" }
        return text
    }

    // MARK: - Selected day

    /// 테두리 line · 모서리 20 · 안쪽 18 · 간격 10
    private func selectedCard(_ record: DayRecord) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("\(record.date.koreanMonthDay) · \(record.type.displayName)")
                .font(.caption13)
                .foregroundStyle(.textSecondary)
            Text(record.topic)
                .font(.plex(17, .bold))
                .foregroundStyle(.ink)
            HStack {
                Text("\(record.doneCount) / \(record.memberCount)명 참여")
                    .font(.caption13)
                    .foregroundStyle(.textSecondary)
                Spacer()
                if record.didSubmit {
                    Button {
                        if let missionID = record.missionID {
                            path.append(.results(groupID: groupID, missionID: missionID))
                        }
                    } label: {
                        Text("모아보기")
                            .font(.plex(13, .semibold))
                            .foregroundStyle(.ink)
                            .padding(.horizontal, 14)
                            .frame(height: 36)
                            .background(.surface, in: Capsule())
                            .contentShape(Rectangle().inset(by: -4))
                    }
                    .buttonStyle(.plain)
                } else {
                    // Figma 에 없는 상태라 버튼 자리에 안내 글자만 둬요.
                    Text("내가 올리지 않은 날이라 볼 수 없어요")
                        .font(.caption13)
                        .foregroundStyle(.textTertiary)
                        .frame(height: 36)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(18)
        .overlay(
            RoundedRectangle(cornerRadius: HRadius.card, style: .continuous)
                .strokeBorder(.line, lineWidth: 1)
        )
    }
}

#Preview {
    let store = DataStore.preview()
    NavigationStack {
        RecordCalendarView(groupID: store.groups[0].id, path: .constant([]))
    }
    .environment(store)
}
