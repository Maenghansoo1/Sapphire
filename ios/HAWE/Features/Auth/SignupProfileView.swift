import SwiftUI

/// A3 회원가입 · 프로필 (Figma 1:256)
/// 캐릭터 7종 + 내 사진 중 선택(기본 몽실이), 닉네임 2–10자 입력 시 '시작하기' 활성화
struct SignupProfileView: View {
    /// 가입 방법 — 이메일이면 A2 에서 넣은 이메일을 같이 받아요.
    var provider: LoginProvider = .email
    var email: String?

    /// 간편 로그인으로 바로 온 경우엔 A2 를 건너뛰어 "2 / 2" 를 숨겨요.
    private var showsStep: Bool { provider == .email }

    @Environment(\.dismiss) private var dismiss
    @Environment(AppSession.self) private var session
    @Environment(DataStore.self) private var store

    @State private var avatar: AvatarContent = .character(.default)
    @State private var nickname = ""

    private var canStart: Bool {
        (2...10).contains(nickname.trimmingCharacters(in: .whitespaces).count)
    }

    var body: some View {
        VStack(spacing: 0) {
            HNavBar(leading: .back { dismiss() }) {
                if showsStep { HNavStep(text: "2 / 2") }
            }
            .padding(.horizontal, HLayout.screenPadding)

            HCTAScaffold {
                VStack(alignment: .leading, spacing: 18) {
                    title
                    preview
                    CharacterPicker(selection: $avatar)
                    HTextField(
                        "닉네임",
                        placeholder: "닉네임을 입력해 주세요",
                        text: $nickname,
                        helper: "캐릭터와 이름은 나중에 바꿀 수 있어요",
                        maxLength: 10,
                        contentType: .nickname
                    )
                }
                .padding(.top, 8)
            } cta: {
                // TODO: 서버 연결 시 프로필 저장. 새 계정이라 그룹이 없어서 01 시작으로 가요.
                HButton("시작하기") {
                    let profile = Profile(avatar: avatar, nickname: nickname.trimmingCharacters(in: .whitespaces))
                    // TODO: 간편 로그인 이메일은 서버 연결 시 각 SDK 에서 받아와요. 지금은 비워 둬요.
                    session.account = MyAccount(
                        profile: profile,
                        email: email,
                        joinedAt: .now,
                        signUpProvider: provider,
                        linkedProviders: [provider]
                    )
                    store.reset()
                    store.setMyProfile(profile)
                    session.signIn()
                }
                    .disabled(!canStart)
            }
        }
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
    }

    private var title: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("어떻게 불러드릴까요?")
                .font(.screenTitle)
                .foregroundStyle(.ink)
            Text("캐릭터를 고르고 이름을 정해주세요.")
                .font(.body15)
                .foregroundStyle(.textSecondary)
        }
    }

    private var preview: some View {
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
    }
}

#Preview {
    NavigationStack {
        SignupProfileView()
    }
    .environment(AppSession())
    .environment(DataStore())
}
