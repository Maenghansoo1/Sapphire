import SwiftUI

/// 그룹장 넘기고 나가기 — 다른 멤버가 있는 그룹의 그룹장이 나갈 때 그룹장을 받을 멤버를 골라요.
/// 임시 UI: 정식 후임자 선택 디자인이 나오기 전까지 기존 컴포넌트로 선택 흐름을 제공해요 (Android 와 같아요).
struct SuccessorPickerView: View {
    let groupID: Int
    /// 나갔거나 그룹이 사라졌어요 — 홈으로 가요.
    var onLeft: () -> Void = {}

    @Environment(\.dismiss) private var dismiss
    @Environment(DataStore.self) private var store
    @Environment(AppSession.self) private var session
    @Environment(Services.self) private var services

    @State private var selectedID: Int?
    @State private var confirms = false
    @State private var isLeaving = false
    @State private var toast: String?

    private var candidates: [GroupMember] { store.group(groupID)?.members.filter { !$0.isMe } ?? [] }
    private var target: GroupMember? { candidates.first { $0.id == selectedID } }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HNavBar("그룹장 넘기기", leading: .back { dismiss() })

            Text("그룹장을 받을 멤버를 골라 주세요")
                .font(.plex(18, .bold))
                .foregroundStyle(.ink)
                .padding(.top, 20)
                .padding(.bottom, 16)

            ScrollView {
                VStack(spacing: 8) {
                    ForEach(candidates) { member in
                        let selected = selectedID == member.id
                        Button { selectedID = member.id } label: {
                            HStack(spacing: 12) {
                                HAvatar(content: member.displayed.avatar, size: 40)
                                Text(member.displayed.nickname)
                                    .font(.body15)
                                    .foregroundStyle(.ink)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                HCheckboxMark(isOn: selected)
                            }
                            .padding(12)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .strokeBorder(selected ? Color.ink : Color.border, lineWidth: 1)
                            )
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(selected ? .isSelected : [])
                    }
                }
            }

            HButton(isLeaving ? "나가는 중" : "넘기고 나가기", kind: .danger) { confirms = true }
                .disabled(target == nil || isLeaving)
                .padding(.bottom, 24)
        }
        .padding(.horizontal, HLayout.screenPaddingCompact)
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
        .overlay {
            if confirms, let target {
                HAlert(
                    title: "\(target.displayed.nickname)님에게 그룹장을 넘기고 나갈까요?",
                    message: "그룹에서 나가도 내가 올린 작품과 댓글은 남아요.",
                    confirmTitle: "넘기고 나가기",
                    onCancel: { confirms = false },
                    onConfirm: {
                        confirms = false
                        leave(to: target)
                    }
                )
            }
        }
        .animation(.easeOut(duration: 0.2), value: confirms)
        .hToast($toast)
        .task { await refresh(showsError: true) }
    }

    /// 멤버를 다시 받아요. 다른 멤버가 다 나갔으면 그룹 정보로 돌아가요.
    private func refresh(showsError: Bool, fallback: String? = nil) async {
        do {
            store.applyServerGroup(try await services.groups.detail(groupID: groupID, myUserID: session.account.userID))
            if candidates.isEmpty {
                toast = "다른 멤버가 없어졌어요. 그룹 정보를 다시 확인해 주세요"
                try? await Task.sleep(for: .seconds(1.2))
                dismiss()
            } else if let fallback {
                toast = fallback
            }
        } catch is CancellationError {
        } catch {
            if ["GROUP_NOT_FOUND", "NOT_GROUP_MEMBER"].contains(error.apiCode ?? "") {
                store.leave(groupID)
                onLeft()
            } else if showsError {
                toast = error.userMessage
            }
        }
    }

    private func leave(to target: GroupMember) {
        guard !isLeaving else { return }
        isLeaving = true
        Task {
            defer { isLeaving = false }
            do {
                try await services.groups.leave(groupID: groupID, successorUserID: target.id)
                store.leave(groupID)
                onLeft()
            } catch is CancellationError {
            } catch {
                switch error.apiCode {
                case "GROUP_NOT_FOUND", "NOT_GROUP_MEMBER":
                    store.leave(groupID)
                    onLeft()
                case "INVALID_SUCCESSOR", "SUCCESSOR_REQUIRED":
                    // 고른 멤버가 그사이 나갔어요 — 다시 받아서 고르게 해요.
                    selectedID = nil
                    await refresh(showsError: true, fallback: error.userMessage)
                default:
                    toast = error.userMessage
                }
            }
        }
    }
}

#Preview {
    let store = DataStore.preview()
    NavigationStack {
        SuccessorPickerView(groupID: store.groups[0].id)
    }
    .environment(store)
    .environment(AppSession(state: .signedIn))
    .environment(Services.preview)
}
