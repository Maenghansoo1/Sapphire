import SwiftUI

/// 16 그룹 정보 · 설정 (Figma 1:1454)
/// 초대 코드 복사·링크 공유, 멤버 목록(내 줄에 '내 프로필 편집'), 주제 도착 시간(방장만 변경), 그룹 알림, 그룹 나가기
struct GroupInfoView: View {
    var onLeft: () -> Void = {}

    @Environment(\.dismiss) private var dismiss
    @State private var group: GroupDetail
    @State private var isEditingProfile = false
    @State private var showsTimePicker = false
    @State private var showsLeaveAlert = false
    @State private var toast: String?

    init(group: GroupDetail, onLeft: @escaping () -> Void = {}) {
        self._group = State(initialValue: group)
        self.onLeft = onLeft
    }

    var body: some View {
        VStack(spacing: 0) {
            HNavBar("그룹 정보", leading: .back { dismiss() })

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    header
                    inviteCard
                    membersHeader
                    members
                    settings
                }
                .padding(.bottom, 32)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .padding(.horizontal, HLayout.screenPaddingCompact)
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(isPresented: $isEditingProfile) {
            if let index = group.members.firstIndex(where: \.isMe) {
                GroupProfileView(groupName: group.name, member: $group.members[index])
            }
        }
        .sheet(isPresented: $showsTimePicker) {
            TopicTimeSheet(time: $group.topicTime)
        }
        .alert("그룹에서 나갈까요?", isPresented: $showsLeaveAlert) {
            Button("취소", role: .cancel) {}
            // TODO: 서버 연결 시 나가기 요청
            Button("나가기", role: .destructive) { onLeft() }
        } message: {
            Text(leaveMessage)
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

    // MARK: - Sections

    /// 그룹 이름 24 Bold + 관계 · 시작일 · 미션 횟수 14
    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(group.name)
                .font(.plex(24, .bold))
                .foregroundStyle(.ink)
            Text("\(group.relationship.displayName) · \(Self.dateText(group.createdAt))부터 · 미션 \(group.missionCount)회")
                .font(.plex(14))
                .foregroundStyle(.textSecondary)
        }
        .padding(.top, 12)
        .padding(.bottom, 20)
    }

    /// 초대 코드 카드 — surface · 모서리 20 · 안쪽 18 · 간격 14
    private var inviteCard: some View {
        VStack(spacing: 14) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("초대 코드")
                        .font(.plex(13, .semibold))
                        .foregroundStyle(.textSecondary)
                    Text(group.inviteCode)
                        .font(.plex(26, .bold))
                        .tracking(3.12)
                        .foregroundStyle(.ink)
                        .textSelection(.enabled)
                }
                Spacer()
                Button(action: copyCode) {
                    HIconView(icon: .copy, size: 20)
                        .frame(width: HLayout.minTouch, height: HLayout.minTouch)
                        .background(.bg, in: Circle())
                        .overlay(Circle().strokeBorder(.border, lineWidth: 1))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("초대 코드 복사")
            }

            ShareLink(
                item: AppLinks.invite(code: group.inviteCode),
                message: Text("HA'WE '\(group.name)' 그룹에 초대할게요. 초대 코드: \(group.inviteCode)")
            ) {
                Text("초대 링크 공유하기")
            }
            .buttonStyle(HButtonStyle(kind: .primary))
        }
        .padding(18)
        .background(.surface, in: RoundedRectangle(cornerRadius: HRadius.card, style: .continuous))
    }

    private var membersHeader: some View {
        HStack {
            Text("멤버")
                .font(.sectionTitle)
                .foregroundStyle(.ink)
            Spacer()
            Text("\(group.members.count) / \(group.relationship.maxMembers)명")
                .font(.caption13)
                .foregroundStyle(.textSecondary)
        }
        .padding(.top, 24)
        .padding(.bottom, 6)
    }

    /// 멤버 줄 — 높이 52 · 아바타 36 · 간격 12
    private var members: some View {
        VStack(spacing: 0) {
            ForEach(group.members) { member in
                HStack(spacing: 12) {
                    HAvatar(content: member.displayed.avatar, size: 36)
                    HStack(spacing: 6) {
                        Text(member.displayed.nickname)
                            .font(.plex(15, .medium))
                            .foregroundStyle(.ink)
                            .lineLimit(1)
                        if let badge = badge(for: member) {
                            Text(badge)
                                .font(.caption12)
                                .foregroundStyle(.textSecondary)
                                .fixedSize()
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    if member.isMe {
                        Button { isEditingProfile = true } label: {
                            Text("내 프로필 편집")
                                .font(.plex(13, .semibold))
                                .foregroundStyle(.ink)
                                .padding(.horizontal, 14)
                                .frame(height: 34)
                                .overlay(Capsule().strokeBorder(.border, lineWidth: 1))
                                .contentShape(Rectangle().inset(by: -5))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .frame(height: 52)
            }
        }
    }

    /// "나 · 방장" — 나와 방장에게만 붙여요.
    private func badge(for member: GroupMember) -> String? {
        let parts = [member.isMe ? "나" : nil, member.isOwner ? "방장" : nil].compactMap { $0 }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    /// 위 구분선 + 위 12 · 목록 줄 52
    private var settings: some View {
        VStack(spacing: 0) {
            // 주제 도착 시간은 방장만 바꿀 수 있어요 — 방장이 아니면 꺾쇠 없이 값만 보여요.
            HListRow("주제 도착 시간", value: Self.timeText(group.topicTime), showsChevron: group.amIOwner) {
                if group.amIOwner { showsTimePicker = true }
            }

            // Figma 에 그룹 알림 설정 화면이 없어서, 누르면 켜짐 ↔ 꺼짐이 바로 바뀌어요.
            HListRow("알림", value: group.notify ? "켜짐" : "꺼짐") {
                group.notify.toggle()
                show(toast: group.notify ? "이 그룹 알림을 켰어요" : "이 그룹 알림을 껐어요")
            }

            Button { showsLeaveAlert = true } label: {
                Text("그룹 나가기")
                    .font(.body15)
                    .foregroundStyle(.danger)
                    .frame(maxWidth: .infinity, minHeight: 52, alignment: .leading)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(.top, 12)
        .overlay(alignment: .top) {
            Rectangle().fill(.line).frame(height: 1)
        }
    }

    // MARK: - Helpers

    private var leaveMessage: String {
        var text = "'\(group.name)'에서 나가면 다시 들어올 때 초대 코드가 필요해요."
        if group.amIOwner, group.members.count > 1 {
            text += "\n방장은 가장 먼저 들어온 멤버에게 넘어가요."
        }
        return text
    }

    private func copyCode() {
        UIPasteboard.general.string = group.inviteCode
        show(toast: "초대 코드를 복사했어요")
    }

    private func show(toast text: String) {
        toast = text
        Task {
            try? await Task.sleep(for: .seconds(1.6))
            if toast == text { toast = nil }
        }
    }

    private static let korean = Locale(identifier: "ko_KR")

    private static func dateText(_ date: Date) -> String {
        date.formatted(.dateTime.year().month(.defaultDigits).day().locale(korean))
    }

    static func timeText(_ time: DateComponents) -> String {
        let date = Calendar.current.date(from: time) ?? .now
        return date.formatted(.dateTime.hour().minute().locale(korean))
    }
}

/// 주제 도착 시간 고르기
private struct TopicTimeSheet: View {
    @Binding var time: DateComponents
    @Environment(\.dismiss) private var dismiss

    @State private var date = Date.now

    var body: some View {
        VStack(spacing: 0) {
            HNavBar("주제 도착 시간") {
                Button("완료") {
                    time = Calendar.current.dateComponents([.hour, .minute], from: date)
                    dismiss()
                }
                .font(.plex(16, .semibold))
                .foregroundStyle(.ink)
                .buttonStyle(.plain)
            }
            .padding(.horizontal, HLayout.screenPaddingCompact)

            Text("매일 이 시간에 그룹의 새 미션이 열려요.")
                .font(.caption13)
                .foregroundStyle(.textSecondary)
                .padding(.top, 4)

            DatePicker("주제 도착 시간", selection: $date, displayedComponents: .hourAndMinute)
                .datePickerStyle(.wheel)
                .labelsHidden()
                .environment(\.locale, Locale(identifier: "ko_KR"))

            Spacer(minLength: 0)
        }
        .padding(.top, 8)
        .background(.bg)
        .presentationDetents([.height(340)])
        .presentationDragIndicator(.visible)
        .onAppear {
            date = Calendar.current.date(from: time) ?? .now
        }
    }
}

/// 잠깐 떴다 사라지는 안내 — Figma 에 없어서 ink 알약 모양으로 정했어요.
struct HToast: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.plex(14, .semibold))
            .foregroundStyle(.onAccent)
            .padding(.horizontal, 18)
            .frame(height: 44)
            .background(Color.ink.opacity(0.9), in: Capsule())
            .accessibilityAddTraits(.updatesFrequently)
    }
}

#Preview("방장") {
    NavigationStack {
        GroupInfoView(group: .mock)
    }
}

#Preview("방금 만든 그룹") {
    NavigationStack {
        GroupInfoView(group: .newlyCreated(name: "우리 둘", relationship: .lover))
    }
}
