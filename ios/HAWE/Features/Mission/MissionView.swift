import SwiftUI

/// 05 그룹 · 오늘의 미션 (Figma 1:542)
/// 오늘 유형·주제, 다음 미션까지 카운트다운, 유형별 CTA, 멤버별 완료 여부, 안 한 멤버 콕 찌르기(하루 1회)
struct MissionView: View {
    @Binding var path: [MainRoute]

    @Environment(\.dismiss) private var dismiss
    @State private var mission: TodayMission
    @State private var isPerforming = false
    @State private var toast: String?

    init(mission: TodayMission, path: Binding<[MainRoute]>) {
        self._mission = State(initialValue: mission)
        self._path = path
    }

    var body: some View {
        VStack(spacing: 0) {
            HNavBar(mission.group.name, leading: .back { dismiss() }) {
                HStack(spacing: 16) {
                    // TODO: 15 기록 캘린더 연결
                    headerButton(.calendar, label: "기록 캘린더") {}
                    headerButton(.more, label: "그룹 정보") { path.append(.groupInfo(mission.group)) }
                }
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    missionCard
                    statusHeader
                    memberList
                    if !mission.didSubmit {
                        lockNotice.padding(.top, 20)
                    }
                }
                .padding(.bottom, 32)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .padding(.horizontal, HLayout.screenPaddingCompact)
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
        .fullScreenCover(isPresented: $isPerforming) {
            MissionPerformView(type: mission.type, topic: mission.topic, onSubmit: submitted)
        }
        .overlay(alignment: .bottom) {
            if let toast {
                HToast(text: toast)
                    .padding(.bottom, 24)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.snappy, value: toast)
    }

    private func headerButton(_ icon: HIcon, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HIconView(icon: icon, size: 22)
                .contentShape(Rectangle().inset(by: -11))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    // MARK: - Mission card

    /// 테두리 line · 모서리 24 · 안쪽 22 · 간격 18
    private var missionCard: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack {
                HStack(spacing: 8) {
                    HIconView(icon: mission.type.icon, size: 16)
                    Text("오늘은 \(mission.type.displayName)")
                        .font(.plex(13, .semibold))
                        .foregroundStyle(.ink)
                }
                .padding(.horizontal, 12)
                .frame(height: 32)
                .background(.surface, in: Capsule())

                Spacer()

                Text("\(Self.dateText(mission.opensAt)) · #\(mission.number)")
                    .font(.caption13)
                    .foregroundStyle(.textSecondary)
            }

            Text(mission.topic)
                .font(.plex(24, .bold))
                .plexLineHeight(1.35, size: 24, weight: .bold)
                .foregroundStyle(.ink)
                .fixedSize(horizontal: false, vertical: true)

            TimelineView(.periodic(from: .now, by: 1)) { context in
                Text("다음 미션까지 \(Self.countdown(to: mission.closesAt, now: context.date))")
                    .font(.caption13)
                    .monospacedDigit()
                    .foregroundStyle(.textSecondary)
            }

            if mission.didSubmit {
                // TODO: 10~13 결과 공개 화면 연결 (5단계)
                HButton("친구들 작품 보기") {}
            } else {
                HButton(mission.type.actionTitle) { isPerforming = true }
            }
        }
        .padding(22)
        .overlay(
            RoundedRectangle(cornerRadius: HRadius.largeCard, style: .continuous)
                .strokeBorder(.line, lineWidth: 1)
        )
    }

    // MARK: - Members

    private var statusHeader: some View {
        HStack {
            Text("참여 현황")
                .font(.sectionTitle)
                .foregroundStyle(.ink)
            Spacer()
            Text("\(mission.doneCount) / \(mission.members.count)명 완료")
                .font(.caption13)
                .foregroundStyle(.textSecondary)
        }
        .padding(.top, 28)
        .padding(.bottom, 4)
    }

    /// 줄 높이 56 · 아바타 38 · 아래 구분선
    private var memberList: some View {
        VStack(spacing: 0) {
            ForEach($mission.members) { $member in
                HStack(spacing: 12) {
                    HAvatar(content: member.profile.avatar, size: 38)
                    Text(member.isMe ? "\(member.profile.nickname) (나)" : member.profile.nickname)
                        .font(.plex(15, .medium))
                        .foregroundStyle(.ink)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    status(for: $member)
                }
                .frame(height: 56)
                .overlay(alignment: .bottom) {
                    Rectangle().fill(.line).frame(height: 1)
                }
            }
        }
    }

    @ViewBuilder
    private func status(for member: Binding<MissionMember>) -> some View {
        if member.wrappedValue.isDone {
            HStack(spacing: 4) {
                HIconView(icon: .check, size: 16)
                Text("완료")
                    .font(.plex(13, .semibold))
                    .foregroundStyle(.ink)
            }
        } else if member.wrappedValue.isMe {
            Text("아직 안 했어요")
                .font(.caption13)
                .foregroundStyle(.textSecondary)
        } else {
            let poked = member.wrappedValue.pokedToday
            Button {
                // TODO: 서버 연결 시 pokes 저장 + 푸시 (하루 1회는 서버에서도 막아요)
                member.wrappedValue.pokedToday = true
                show(toast: "\(member.wrappedValue.profile.nickname)님을 콕 찔렀어요")
            } label: {
                Text(poked ? "찔렀어요" : "콕 찌르기")
                    .font(.plex(13, .semibold))
                    .foregroundStyle(poked ? .textTertiary : .ink)
                    .padding(.horizontal, 14)
                    .frame(height: 34)
                    .overlay(Capsule().strokeBorder(poked ? .line : .border, lineWidth: 1))
                    .contentShape(Rectangle().inset(by: -5))
            }
            .buttonStyle(.plain)
            .disabled(poked)
        }
    }

    /// surface · 모서리 18 · 안쪽 18 · 자물쇠 22 + 14
    private var lockNotice: some View {
        HStack(spacing: 14) {
            HIconView(icon: .lock, size: 22, color: .textSecondary)
            Text("내 미션을 올리면 친구들의 \(mission.type.subjectForm) 열려요.")
                .font(.plex(14))
                .foregroundStyle(.textBody)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(18)
        .background(.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    // MARK: - Helpers

    private func submitted() {
        if let index = mission.members.firstIndex(where: \.isMe) {
            mission.members[index].isDone = true
        }
        show(toast: "오늘의 미션을 올렸어요")
    }

    private func show(toast text: String) {
        toast = text
        Task {
            try? await Task.sleep(for: .seconds(1.6))
            if toast == text { toast = nil }
        }
    }

    private static func dateText(_ date: Date) -> String {
        var style = Date.FormatStyle.dateTime.month(.defaultDigits).day().locale(Locale(identifier: "ko_KR"))
        style.timeZone = TodayMission.seoul
        return date.formatted(style)
    }

    /// 남은 시간 HH:mm:ss
    static func countdown(to end: Date, now: Date) -> String {
        let seconds = max(0, Int(end.timeIntervalSince(now)))
        return String(format: "%02d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)
    }
}

#Preview("내 미션 남음") {
    NavigationStack {
        MissionView(mission: .mock(for: GroupSummary.mock[0]), path: .constant([]))
    }
}

#Preview("올린 뒤") {
    NavigationStack {
        MissionView(mission: .mock(for: GroupSummary.mock[1]), path: .constant([]))
    }
}
