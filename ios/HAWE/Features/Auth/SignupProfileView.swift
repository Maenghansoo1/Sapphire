import SwiftUI

/// A3 회원가입 · 프로필 (Figma 1:256)
/// 캐릭터 7종 중 선택(기본 몽실이), 닉네임 2–10자 입력 시 '시작하기' 활성화.
/// '시작하기'를 누르면 가입 → 로그인 → 캐릭터 저장을 서버에 요청해요.
struct SignupProfileView: View {
    /// 가입 방법 — 이메일이면 A2 에서 넣은 이메일을 같이 받아요.
    var provider: LoginProvider = .email
    var email: String?
    /// A2 의 비밀번호·약관 동의
    var signup = SignupAccountModel()

    /// 간편 로그인으로 바로 온 경우엔 A2 를 건너뛰어 "2 / 2" 를 숨겨요.
    private var showsStep: Bool { provider == .email }

    @Environment(\.dismiss) private var dismiss
    @Environment(AppSession.self) private var session
    @Environment(DataStore.self) private var store
    @Environment(Services.self) private var services

    @State private var avatar: AvatarContent = .character(.default)
    @State private var nickname = ""
    @State private var isLoading = false
    /// 가입은 됐는데 그다음 단계에서 실패한 경우 — 다시 누르면 가입을 건너뛰어요.
    @State private var registered = false
    @State private var toast: String?

    private var canStart: Bool {
        !isLoading && (2...10).contains(nickname.trimmingCharacters(in: .whitespaces).count)
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
                    // 프로필 사진 업로드 API 가 생기기 전까지 캐릭터만 골라요.
                    CharacterPicker(selection: $avatar, showsPhoto: false)
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
                HButton("시작하기", action: start)
                    .disabled(!canStart)
            }
        }
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
        .hToast($toast)
    }

    private func start() {
        guard canStart, case .character(let character) = avatar else { return }
        isLoading = true
        Task {
            defer { isLoading = false }
            do {
                let account = try await services.auth.signUp(
                    email: email ?? "", password: signup.password, nickname: nickname, character: character,
                    agreeTerms: signup.agreeService, agreePrivacy: signup.agreePrivacy,
                    skipRegister: registered, onRegistered: { registered = true }
                )
                store.reset()
                store.setMyProfile(account.profile)
                session.signIn(account)
            } catch is CancellationError {
            } catch {
                toast = error.userMessage
                // 이미 가입된 이메일이면 A2 로 돌아가 다른 이메일을 넣게 해요.
                if error.apiCode == "EMAIL_TAKEN" {
                    try? await Task.sleep(for: .seconds(1.6))
                    dismiss()
                }
            }
        }
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
    .environment(Services.preview)
}
