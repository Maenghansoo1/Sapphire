import SwiftUI

/// 기본 프로필 편집 — 17 마이페이지 '프로필 편집', 17-2 '닉네임'에서 열려요.
/// Figma 에 화면이 없어서 A3(캐릭터 + 닉네임)와 같은 모양으로 만들었어요.
/// 그룹 내 프로필(16-1)을 쓰지 않는 그룹에 모두 반영돼요 (CLAUDE.md §5-4).
struct ProfileEditView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(AppSession.self) private var session

    @State private var avatar: AvatarContent = .character(.default)
    @State private var nickname = ""
    @State private var loaded = false

    private var canSave: Bool {
        (2...10).contains(nickname.trimmingCharacters(in: .whitespaces).count)
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
                            Text("선택한 캐릭터")
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
                // TODO: 서버 연결 시 profiles 저장 (사진이면 avatars/{user_id}.jpg 업로드)
                HButton("저장") {
                    session.account.profile = Profile(avatar: avatar, nickname: nickname.trimmingCharacters(in: .whitespaces))
                    dismiss()
                }
                .disabled(!canSave)
            }
        }
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
        .onAppear {
            guard !loaded else { return }
            avatar = session.account.profile.avatar
            nickname = session.account.profile.nickname
            loaded = true
        }
    }
}

#Preview {
    NavigationStack {
        ProfileEditView()
    }
    .environment(AppSession(state: .signedIn(hasGroups: true)))
}
