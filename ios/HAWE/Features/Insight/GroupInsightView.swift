import SwiftUI

/// 우리 그룹 이야기 — 그룹 활동 인사이트 (A9, 임시 UI: Figma 디자인이 정해지면 카드 표현만 바꿔요)
/// 활동 지수·가장 빨리 낸 사람·많이 쓰인 단어·가장 반응이 많았던 글·하이라이트. 최근 12달까지 볼 수 있어요.
/// 서버가 계산 중(202)이면 몇 번 다시 물어보고, 지난 값이면 먼저 보여 준 뒤 새 값으로 바꿔요.
struct GroupInsightView: View {
    let groupID: Int
    @Binding var path: [MainRoute]
    /// 그룹에서 빠졌어요 — 홈으로 가요.
    var onLeft: () -> Void = {}

    @Environment(\.dismiss) private var dismiss
    @Environment(DataStore.self) private var store
    @Environment(Services.self) private var services
    @State private var month: Date
    @State private var outcome: InsightOutcome?
    @State private var failed = false
    @State private var retry = 0
    @State private var opening = false
    @State private var toast: String?

    private struct LoadKey: Equatable {
        let month: Date
        let retry: Int
    }

    private let current: Date

    init(groupID: Int, path: Binding<[MainRoute]>, onLeft: @escaping () -> Void = {}) {
        self.groupID = groupID
        self._path = path
        self.onLeft = onLeft
        let current = InsightFormat.monthStart(AppClock.now())
        self.current = current
        self._month = State(initialValue: current)
    }

    var body: some View {
        VStack(spacing: 0) {
            HNavBar("우리 그룹 이야기", leading: .back { dismiss() })

            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    monthHeader
                    content
                }
                .padding(.bottom, 32)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .padding(.horizontal, HLayout.screenPaddingCompact)
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
        .hToast($toast)
        .task(id: LoadKey(month: month, retry: retry)) { await load() }
    }

    @ViewBuilder
    private var content: some View {
        if failed {
            statusPanel("불러오지 못했어요")
        } else {
            switch outcome {
            case .timedOut:
                statusPanel("아직 준비 중이에요. 잠시 후 다시 열어 주세요")
            case .ready(let insight, let updating):
                if updating {
                    Text("업데이트 중…")
                        .font(.caption13)
                        .foregroundStyle(.textSecondary)
                }
                if insight.empty {
                    Text("이 달에는 아직 쌓인 기록이 없어요")
                        .font(.body15)
                        .foregroundStyle(.textSecondary)
                } else {
                    InsightContent(insight: insight, opening: opening, onOpen: open)
                }
            case nil:
                Text("우리 이야기를 정리하고 있어요…")
                    .font(.body15)
                    .foregroundStyle(.textSecondary)
            }
        }
    }

    private var monthHeader: some View {
        HStack {
            monthArrow(.chevronLeft, label: "이전 달", enabled: InsightFormat.canShowPrevious(month, current: current)) { move(-1) }
            Spacer()
            Text(InsightFormat.monthTitle(month))
                .font(.plex(18, .bold))
                .foregroundStyle(.ink)
            Spacer()
            monthArrow(.chevronRight, label: "다음 달", enabled: InsightFormat.canShowNext(month, current: current)) { move(1) }
        }
    }

    private func monthArrow(_ icon: HIcon, label: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HIconView(icon: icon, size: 20, color: enabled ? .ink : .disabled)
                .frame(width: 48, height: 48)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel(label)
    }

    private func statusPanel(_ message: String) -> some View {
        VStack(spacing: 16) {
            Text(message)
                .font(.body15)
                .foregroundStyle(.textSecondary)
                .multilineTextAlignment(.center)
            HButton("다시 시도") { retry += 1 }
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 48)
    }

    private func move(_ value: Int) {
        guard let next = DataStore.calendar.date(byAdding: .month, value: value, to: month) else { return }
        month = next
        outcome = nil
        failed = false
    }

    private func load() async {
        outcome = nil
        failed = false
        do {
            try await loadInsight({ try await services.insights.load(groupID: groupID, month: month) }) { outcome = $0 }
        } catch is CancellationError {
        } catch {
            if error.apiCode == "NOT_GROUP_MEMBER" || error.apiCode == "GROUP_NOT_FOUND" {
                store.leave(groupID)
                onLeft()
            } else {
                failed = true
            }
        }
    }

    /// 가장 반응이 많았던 글 — 그달 달력을 받아 내가 낸 날이면 결과 화면으로 가요.
    private func open(_ missionID: Int) {
        guard !opening else { return }
        opening = true
        let month = month
        Task {
            defer { opening = false }
            do {
                let records = try await services.calendar.month(groupID: groupID, month: month)
                store.applyCalendar(records, groupID: groupID, month: month)
                switch store.calendarRecord(groupID: groupID, missionID: missionID)?.didSubmit {
                case true?: path.append(.results(groupID: groupID, missionID: missionID))
                case false?: toast = "먼저 미션을 올려야 볼 수 있어요"
                case nil: toast = "이 글은 지금 열 수 없어요"
                }
            } catch is CancellationError {
            } catch {
                if error.apiCode == "NOT_GROUP_MEMBER" || error.apiCode == "GROUP_NOT_FOUND" {
                    store.leave(groupID)
                    onLeft()
                } else {
                    toast = error.userMessage
                }
            }
        }
    }
}

// 임시 UI: Figma 디자인이 정해지면 이 섹션의 표현만 교체해요.
private struct InsightContent: View {
    let insight: GroupInsight
    let opening: Bool
    let onOpen: (Int) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            if let activity = insight.activityIndex {
                InsightCard(title: "활동 지수") {
                    ZStack {
                        Circle().stroke(.line, style: StrokeStyle(lineWidth: 9, lineCap: .round))
                        Circle()
                            .trim(from: 0, to: CGFloat(min(max(activity.score, 0), 100)) / 100)
                            .stroke(.accent, style: StrokeStyle(lineWidth: 9, lineCap: .round))
                            .rotationEffect(.degrees(-90))
                        Text("\(activity.score)")
                            .font(.plex(34, .bold))
                            .foregroundStyle(.ink)
                    }
                    .frame(width: 128, height: 128)
                    .frame(maxWidth: .infinity)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("활동 지수 \(activity.score)")
                    if let delta = InsightFormat.delta(activity.delta) {
                        Text("전월 대비 \(delta)")
                            .font(.caption13)
                            .foregroundStyle(.textSecondary)
                            .frame(maxWidth: .infinity)
                    }
                    RateBar(label: "참여", percent: InsightFormat.percent(activity.participationRate))
                    RateBar(label: "정시", percent: InsightFormat.percent(activity.onTimeRate))
                    RateBar(label: "교류", percent: InsightFormat.percent(activity.interactionRate))
                }
            }
            if let fastest = insight.fastest {
                InsightCard(title: "가장 빨리 낸 사람") {
                    HStack(spacing: 12) {
                        HAvatar(content: fastest.profile.avatar, size: 48)
                        VStack(alignment: .leading, spacing: 0) {
                            Text(fastest.profile.nickname)
                                .font(.plex(17, .bold))
                                .foregroundStyle(.ink)
                            Text("\(fastest.firstCount)번 가장 먼저 냈어요")
                                .font(.body15)
                                .foregroundStyle(.textSecondary)
                        }
                    }
                    Text(InsightFormat.responseTime(fastest.averageResponseSeconds))
                        .font(.body15)
                        .foregroundStyle(.textSecondary)
                }
            }
            if !insight.topWords.isEmpty {
                InsightCard(title: "많이 쓰인 단어") {
                    let maximum = max(1, insight.topWords.map(\.count).max() ?? 1)
                    FlowLayout(spacing: 8) {
                        ForEach(insight.topWords, id: \.word) { word in
                            HStack(spacing: 4) {
                                Text(word.word)
                                    .font(.plex(InsightFormat.wordSize(count: word.count, maximum: maximum), .semibold))
                                    .foregroundStyle(.ink)
                                Text("\(word.count)번")
                                    .font(.caption12)
                                    .foregroundStyle(.textSecondary)
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(.accentSubtle, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        }
                    }
                }
            }
            if let reacted = insight.mostReacted {
                InsightCard(title: "가장 반응이 많았던 글") {
                    Button { onOpen(reacted.missionID) } label: {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(reacted.missionContent)
                                .font(.plex(16, .semibold))
                                .foregroundStyle(.ink)
                                .multilineTextAlignment(.leading)
                            Text("\(reacted.authorNickname) · 반응 \(reacted.reactionCount) · 댓글 \(reacted.commentCount) \(reacted.topSticker?.emoji ?? "")")
                                .font(.caption13)
                                .foregroundStyle(.textSecondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .disabled(opening)
                }
            }
            if !insight.highlights.isEmpty {
                InsightCard(title: "하이라이트") {
                    ForEach(insight.highlights, id: \.self) { highlight in
                        Text("• \(highlight)")
                            .font(.body15)
                            .foregroundStyle(.ink)
                    }
                }
            }
        }
    }
}

private struct InsightCard<Content: View>: View {
    let title: String
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                HIconView(icon: .insightTemp, size: 20, color: .accent)
                Text(title)
                    .font(.sectionTitle)
                    .foregroundStyle(.ink)
            }
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(18)
        .background(.surface, in: RoundedRectangle(cornerRadius: HRadius.button, style: .continuous))
    }
}

private struct RateBar: View {
    let label: String
    let percent: Int

    var body: some View {
        HStack(spacing: 8) {
            Text(label)
                .font(.caption13)
                .foregroundStyle(.textSecondary)
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 3).fill(.line)
                    RoundedRectangle(cornerRadius: 3).fill(.accent)
                        .frame(width: proxy.size.width * CGFloat(percent) / 100)
                }
            }
            .frame(height: 6)
            Text("\(percent)%")
                .font(.caption13)
                .foregroundStyle(.textSecondary)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(label) \(percent)퍼센트")
    }
}

/// 인사이트 문구 (Android `InsightFormat.kt`)
enum InsightFormat {
    /// 서울 기준 그달 1일
    static func monthStart(_ date: Date) -> Date {
        DataStore.calendar.dateInterval(of: .month, for: date)?.start ?? date
    }

    static func monthTitle(_ month: Date) -> String {
        let parts = DataStore.calendar.dateComponents([.year, .month], from: month)
        return "\(parts.year ?? 0)년 \(parts.month ?? 0)월"
    }

    /// 최근 12달(이번 달 포함)까지 거슬러 볼 수 있어요.
    static func canShowPrevious(_ month: Date, current: Date) -> Bool {
        guard let limit = DataStore.calendar.date(byAdding: .month, value: -12, to: current) else { return false }
        return monthStart(month) > monthStart(limit)
    }

    static func canShowNext(_ month: Date, current: Date) -> Bool {
        monthStart(month) < monthStart(current)
    }

    static func percent(_ rate: Double) -> Int { min(max(Int((rate * 100).rounded()), 0), 100) }

    static func delta(_ delta: Int?) -> String? {
        guard let delta else { return nil }
        if delta > 0 { return "+\(delta)" }
        if delta < 0 { return "\(delta)" }
        return "변화 없음"
    }

    static func responseTime(_ seconds: Int) -> String {
        let minutes = max(1, Int((Double(max(seconds, 0)) / 60).rounded(.up)))
        return minutes < 60 ? "보통 \(minutes)분 안에" : "보통 \(minutes / 60)시간 \(minutes % 60)분 안에"
    }

    static func wordSize(count: Int, maximum: Int) -> CGFloat {
        if count * 3 >= maximum * 2 { return 18 }
        if count * 3 >= maximum { return 15 }
        return 13
    }
}

#Preview {
    let store = DataStore.preview()
    NavigationStack {
        GroupInsightView(groupID: store.groups[0].id, path: .constant([]))
    }
    .environment(store)
    .environment(Services.preview)
}
