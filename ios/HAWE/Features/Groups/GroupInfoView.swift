import SwiftUI

/// 16 그룹 정보 · 설정 (Figma 1:1454)
/// 초대 코드 복사·링크 공유, 멤버 목록, 그룹 나가기.
/// 그룹 내 프로필·주제 도착 시간·그룹 알림은 서버 API 가 생기기 전까지 '준비 중'이에요.
struct GroupInfoView: View {
    let groupID: Int
    var onLeft: () -> Void = {}

    @Environment(\.dismiss) private var dismiss
    @Environment(DataStore.self) private var store
    @Environment(AppSession.self) private var session
    @Environment(Services.self) private var services
    @State private var showsLeaveAlert = false
    @State private var isLeaving = false
    @State private var toast: String?

    /// 나간 직후처럼 그룹이 사라졌을 때는 빈 값으로 그려요.
    private var group: GroupDetail {
        store.group(groupID) ?? GroupDetail(
            id: groupID, name: "", relationship: .friend, createdAt: .now, missionCount: 0,
            inviteCode: "", topicTime: DateComponents(hour: 9, minute: 0), notify: false, members: []
        )
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
        .alert("그룹에서 나갈까요?", isPresented: $showsLeaveAlert) {
            Button("취소", role: .cancel) {}
            Button("나가기", role: .destructive, action: leave)
        } message: {
            Text(leaveMessage)
        }
        .hToast($toast)
        // 들어올 때마다 멤버·초대 코드를 서버에서 다시 받아요.
        .task { await reload() }
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
                        // 그룹 내 프로필 API 가 생기면 16-1 GroupProfileView 로 보내요.
                        Button { toast = ComingSoon.message } label: {
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
            // 주제 도착 시간 변경·그룹 알림은 서버 API 가 생기면 TopicTimeSheet·토글로 다시 열어요.
            HListRow("주제 도착 시간", value: Self.timeText(group.topicTime), showsChevron: false) {
                toast = ComingSoon.message
            }

            HListRow("알림", value: "준비 중") {
                toast = ComingSoon.message
            }

            Button {
                // 그룹장 넘기기(후임자 선택)는 아직 준비 중이에요.
                if group.amIOwner && group.members.count > 1 {
                    toast = "그룹장은 다른 멤버에게 그룹장을 넘긴 뒤 나갈 수 있어요. 지금은 준비 중이에요"
                } else {
                    showsLeaveAlert = true
                }
            } label: {
                Text("그룹 나가기")
                    .font(.body15)
                    .foregroundStyle(.danger)
                    .frame(maxWidth: .infinity, minHeight: 52, alignment: .leading)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(isLeaving)
        }
        .padding(.top, 12)
        .overlay(alignment: .top) {
            Rectangle().fill(.line).frame(height: 1)
        }
    }

    // MARK: - Helpers

    private var leaveMessage: String {
        // 혼자 남은 방장이 나가면 서버가 그룹을 지워요.
        if group.amIOwner && group.members.count == 1 {
            return "나가면 이 그룹이 삭제돼요. 초대 코드도 더는 쓸 수 없어요."
        }
        // 나간 뒤에도 내가 올린 작품·댓글은 그룹에 남아요 (사용자 결정 2026-10-12).
        return "'\(group.name)'에서 나가도 내가 올린 작품과 댓글은 그룹에 남아요. 다시 들어오려면 초대 코드가 필요해요."
    }

    private func reload() async {
        do {
            let detail = try await services.groups.detail(groupID: groupID, myUserID: session.account.userID)
            store.applyServerGroup(detail)
        } catch let error as APIError where error.status == 403 || error.status == 404 {
            // 이미 나갔거나 사라진 그룹이에요.
            store.leave(groupID)
            onLeft()
        } catch {
            // 네트워크 오류면 갖고 있던 값을 그대로 보여 줘요.
        }
    }

    private func leave() {
        guard !isLeaving else { return }
        isLeaving = true
        Task {
            do {
                try await services.groups.leave(groupID: groupID)
                store.leave(groupID)
                onLeft()
            } catch let error as APIError where error.code == "GROUP_NOT_FOUND" || error.code == "NOT_GROUP_MEMBER" {
                store.leave(groupID)
                onLeft()
            } catch is CancellationError {
                isLeaving = false
            } catch {
                toast = error.userMessage
                isLeaving = false
            }
        }
    }

    private func copyCode() {
        UIPasteboard.general.string = group.inviteCode
        toast = "초대 코드를 복사했어요"
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

/// 주제 도착 시간 고르기 — 서버에 변경 API 가 생기면 다시 연결해요.
struct TopicTimeSheet: View {
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

#Preview("체험 그룹") {
    let store = DataStore.preview()
    NavigationStack {
        GroupInfoView(groupID: store.groups[0].id)
    }
    .environment(store)
    .environment(AppSession(state: .signedIn))
    .environment(Services.preview)
}

#Preview("방금 만든 그룹") {
    let store = DataStore()
    let id = store.createGroup(name: "우리 둘", relationship: .lover, startDate: .now)
    NavigationStack {
        GroupInfoView(groupID: id)
    }
    .environment(store)
    .environment(AppSession(state: .signedIn))
    .environment(Services.preview)
}
