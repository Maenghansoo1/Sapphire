import SwiftUI

/// 하루치 기록 (15 캘린더 칸)
struct DayRecord: Hashable {
    var date: Date
    var type: MissionType
    var topic: String
    var doneCount: Int
    var memberCount: Int
}

/// 15 기록 캘린더 (Figma 1:1311)
/// 그룹별 월간 캘린더. 기록 있는 날에 유형 글자(그/사/글/음), 오늘 테두리,
/// 날짜를 고르면 아래 카드 → '모아보기'로 그날 결과 공개 화면
struct RecordCalendarView: View {
    let group: GroupDetail
    @Binding var path: [MainRoute]

    @Environment(\.dismiss) private var dismiss
    @State private var month: Date
    @State private var selected: Date?
    @State private var records: [Date: DayRecord]

    private static let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TodayMission.seoul
        calendar.locale = Locale(identifier: "ko_KR")
        calendar.firstWeekday = 1
        return calendar
    }()

    init(group: GroupDetail, path: Binding<[MainRoute]>, records: [DayRecord]? = nil) {
        self.group = group
        self._path = path
        let calendar = Self.calendar
        let list = records ?? Self.mockRecords(for: group, calendar: calendar)
        let byDay = Dictionary(list.map { (calendar.startOfDay(for: $0.date), $0) }, uniquingKeysWith: { a, _ in a })
        self._records = State(initialValue: byDay)
        self._month = State(initialValue: calendar.dateInterval(of: .month, for: .now)?.start ?? .now)
        // 처음에는 가장 최근 기록이 있는 날을 골라 둬요.
        self._selected = State(initialValue: byDay.keys.max())
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
                    legend.padding(.top, 12)
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
    }

    // MARK: - Month

    private var firstMonth: Date {
        calendar.dateInterval(of: .month, for: group.createdAt)?.start ?? month
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
                Text(record?.type.letter ?? " ")
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

    /// "그 그림   사 사진   글 글   음 음성" (12 · 간격 14)
    private var legend: some View {
        HStack(spacing: 14) {
            ForEach(MissionType.allCases) { type in
                Text("\(type.letter) \(type.displayName)")
            }
        }
        .font(.caption12)
        .foregroundStyle(.textSecondary)
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
                Button {
                    path.append(.results(.mock(groupName: group.name, type: record.type, topic: record.topic, date: record.date)))
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
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(18)
        .overlay(
            RoundedRectangle(cornerRadius: HRadius.card, style: .continuous)
                .strokeBorder(.line, lineWidth: 1)
        )
    }

    // MARK: - 목업

    /// 그룹을 만든 날부터 어제까지. Figma 10월 화면처럼 10일마다 한 번은 기록이 없어요.
    static func mockRecords(for group: GroupDetail, calendar: Calendar) -> [DayRecord] {
        let pattern: [MissionType?] = [.draw, .photo, .write, .voice, .draw, nil, .photo, .voice, .write, .draw]
        let topics: [MissionType: [String]] = [
            .draw: ["요즘 제일 웃겼던 순간", "오늘 기분을 날씨로 그려줘", "내 방 책상 위"],
            .photo: ["지금 창밖으로 보이는 풍경", "오늘 먹은 것 중 최고", "지금 신고 있는 신발"],
            .write: ["오늘 들은 가장 따뜻한 말", "너에게 해주고 싶은 말 한 줄"],
            .voice: ["오늘 하루를 10초로 말해줘", "요즘 흥얼거리는 노래 한 소절"],
        ]
        var records: [DayRecord] = []
        var day = calendar.startOfDay(for: group.createdAt)
        let today = calendar.startOfDay(for: .now)
        var index = 0
        while day < today {
            let dayOfMonth = calendar.component(.day, from: day)
            if let type = pattern[(dayOfMonth - 1) % pattern.count] {
                let list = topics[type] ?? []
                records.append(DayRecord(
                    date: day,
                    type: type,
                    topic: list.isEmpty ? "" : list[index % list.count],
                    doneCount: index % 4 == 3 ? group.members.count - 1 : group.members.count,
                    memberCount: group.members.count
                ))
                index += 1
            }
            guard let next = calendar.date(byAdding: .day, value: 1, to: day) else { break }
            day = next
        }
        return records
    }
}

#Preview {
    NavigationStack {
        RecordCalendarView(group: .mock, path: .constant([]))
    }
}
