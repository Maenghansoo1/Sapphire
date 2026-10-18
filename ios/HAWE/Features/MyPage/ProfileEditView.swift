import SwiftUI

/// 기본 프로필 편집 — 17 마이페이지 '프로필 편집', 17-2 '닉네임'에서 열려요.
/// Figma 에 화면이 없어서 A3(캐릭터·사진 + 닉네임)와 같은 모양으로 만들었어요.
/// 저장하면 닉네임 → 캐릭터(또는 사진) 순서로 서버에 올리고 내 정보를 다시 받아요 (8A).
/// 그룹 내 프로필(16-1)을 쓰지 않는 그룹에 모두 반영돼요 (CLAUDE.md §5-4).
struct ProfileEditView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppSession.self) private var session
    @Environment(DataStore.self) private var store
    @Environment(Services.self) private var services

    @State private var avatar: AvatarContent = .character(.default)
    @State private var nickname = ""
    @State private var loaded = false
    @State private var isSaving = false
    @State private var toast: String?

    private var trimmed: String { nickname.trimmingCharacters(in: .whitespaces) }

    private var canSave: Bool {
        (2...10).contains(trimmed.count) && !isSaving
            && Profile(avatar: avatar, nickname: trimmed) != session.account.profile
    }

    var body: some View {
        VStack(spacing: 0) {
            HNavBar("프로필 편집", leading: .back { dismiss() })
                .padding(.horizontal, HLayout.screenPadding)

            HCTAScaffold {
                VStack(alignment: .leading, spacing: 18) {
                    HStack(spacing: 16) {
                        HAvatar(content: avatar, size: 88, characterScale: 0.8)
                        VStack(alignment: .leading, spacing: 4) {
                            Text("선택한 프로필")
                                .font(.caption13)
                                .foregroundStyle(.textSecondary)
                            Text(avatar.choiceName)
                                .font(.plex(20, .bold))
                                .foregroundStyle(.ink)
                        }
                    }
                    CharacterPicker(selection: $avatar)
                    HTextField(
                        "닉네임",
                        placeholder: "닉네임을 입력해 주세요",
                        text: $nickname,
                        maxLength: 10,
                        contentType: .nickname
                    )
                }
                .padding(.top, 8)
            } cta: {
                HButton(isSaving ? "저장 중" : "저장", action: save)
                    .disabled(!canSave)
            }
        }
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
        .hToast($toast)
        .onAppear {
            guard !loaded else { return }
            avatar = session.account.profile.avatar
            nickname = session.account.profile.nickname
            loaded = true
        }
    }

    private func save() {
        guard canSave else { return }
        isSaving = true
        let current = session.account.profile
        let nickname = trimmed
        let avatar = avatar
        Task {
            defer { isSaving = false }
            do {
                let result = try await services.auth.saveProfile(current: current, nickname: nickname, avatar: avatar)
                // 일부만 저장됐어도 서버의 최신 값으로 맞춰요.
                if let account = result.account {
                    session.account = account
                    store.setMyProfile(account.profile)
                }
                if let error = result.error {
                    toast = error is AvatarPhotoError ? "사진을 불러오지 못했어요" : error.userMessage
                } else {
                    dismiss()
                }
            } catch is CancellationError {
            } catch {
                toast = error.userMessage
            }
        }
    }
}

#Preview {
    NavigationStack {
        ProfileEditView()
    }
    .environment(AppSession(state: .signedIn))
    .environment(DataStore())
    .environment(Services.preview)
}
