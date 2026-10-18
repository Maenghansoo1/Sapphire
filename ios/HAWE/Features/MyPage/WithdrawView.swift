import SwiftUI

/// 17-5 회원 탈퇴 (Figma 1:1782)
/// 주의 사항 3개, 동의 체크 시 '탈퇴하기'(danger) 활성화. 작품·댓글 삭제, 모든 그룹에서 나감, 복구 불가 (CLAUDE.md §5-5)
/// 다른 멤버가 있는 그룹의 그룹장이면 먼저 그룹장을 넘겨야 해요 (서버도 OWNER_MUST_TRANSFER 로 막아요).
struct WithdrawView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppSession.self) private var session
    @Environment(DataStore.self) private var store
    @Environment(Services.self) private var services
    @State private var agreed = false
    @State private var isLoading = false
    @State private var toast: String?

    private let notices = [
        "그동안 남긴 그림, 사진, 글과 댓글이 모두 삭제되고 되돌릴 수 없어요.",
        "참여 중인 모든 그룹에서 나가게 돼요. 다른 멤버가 있는 그룹의 그룹장이라면 먼저 그룹장을 넘기고 탈퇴해 주세요. 혼자 있는 그룹은 함께 삭제돼요.",
        "같은 계정으로 다시 가입해도 이전 기록은 복구되지 않아요.",
    ]

    /// 그룹장을 넘겨야 하는 그룹 이름
    static func blockers(_ groups: [StoredGroup]) -> [String] {
        groups.compactMap { $0.detail.amIOwner && $0.detail.members.count > 1 ? $0.detail.name : nil }
    }

    var body: some View {
        let blockers = Self.blockers(store.groups)
        VStack(spacing: 0) {
            HNavBar("회원 탈퇴", leading: .back { dismiss() })

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("정말 떠나시나요?")
                            .font(.plex(22, .bold))
                            .foregroundStyle(.ink)
                        Text("탈퇴하기 전에 아래 내용을 꼭 확인해 주세요.")
                            .font(.plex(14))
                            .foregroundStyle(.textSecondary)
                    }
                    .padding(.top, 12)
                    .padding(.bottom, 16)

                    // surface · 모서리 16 · 안쪽 16 · 간격 10, 빨간 점 + 14 (줄 높이 1.5)
                    VStack(alignment: .leading, spacing: 10) {
                        ForEach(notices, id: \.self) { notice in
                            HStack(alignment: .top, spacing: 8) {
                                // Figma 점 자리 5×13 — 첫 줄 가운데에 맞춰요.
                                Circle()
                                    .fill(.danger)
                                    .frame(width: 5, height: 5)
                                    .frame(height: 21)
                                Text(notice)
                                    .font(.plex(14))
                                    .plexLineHeight(1.5, size: 14)
                                    .foregroundStyle(.ink)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16)
                    .background(.surface, in: RoundedRectangle(cornerRadius: HRadius.button, style: .continuous))

                    if !blockers.isEmpty {
                        Text("먼저 다음 그룹의 그룹장을 넘겨 주세요: \(blockers.joined(separator: ", "))")
                            .font(.plex(14))
                            .foregroundStyle(.danger)
                            .padding(.top, 12)
                    }
                }
            }
            .scrollBounceBehavior(.basedOnSize)

            HCheckbox(isOn: $agreed) {
                Text("위 내용을 모두 확인했고, 탈퇴에 동의해요")
                    .font(.plex(14))
                    .foregroundStyle(.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            HStack(spacing: 8) {
                HButton("계속 쓸게요", kind: .gray, height: 54) { dismiss() }
                HButton("탈퇴하기", kind: .danger, height: 54, action: withdraw)
                    .disabled(!agreed || !blockers.isEmpty || isLoading)
            }
            .padding(.top, 10)
        }
        .padding(.horizontal, HLayout.screenPaddingCompact)
        .padding(.bottom, 32)
        .ignoresSafeArea(.container, edges: .bottom)
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
        .hToast($toast)
    }

    private func withdraw() {
        guard !isLoading else { return }
        isLoading = true
        Task {
            defer { isLoading = false }
            do {
                try await services.auth.withdraw()
                session.withdraw()
            } catch is CancellationError {
            } catch {
                toast = error.userMessage
            }
        }
    }
}

#Preview {
    NavigationStack {
        WithdrawView()
    }
    .environment(AppSession(state: .signedIn))
    .environment(DataStore())
    .environment(Services.preview)
}
