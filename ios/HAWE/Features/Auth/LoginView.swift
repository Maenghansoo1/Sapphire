import SwiftUI

/// A1 로그인 (Figma 1:190)
struct LoginView: View {
    @Binding var path: [AuthRoute]
    @Environment(AppSession.self) private var session
    @Environment(DataStore.self) private var store
    @Environment(Services.self) private var services

    @State private var email = ""
    @State private var password = ""
    @State private var isLoading = false
    @State private var toast: String?

    private var canLogIn: Bool {
        !isLoading && !email.trimmingCharacters(in: .whitespaces).isEmpty && !password.isEmpty
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 32) {
                intro
                form
                divider
                socialButtons
            }
            .padding(.horizontal, HLayout.screenPadding)
            .padding(.top, HLayout.introTopInset)
            .padding(.bottom, HLayout.ctaBottomInset)
        }
        .scrollBounceBehavior(.basedOnSize)
        .scrollDismissesKeyboard(.interactively)
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
        .hToast($toast)
    }

    private func logIn() {
        guard canLogIn else { return }
        isLoading = true
        Task {
            defer { isLoading = false }
            do {
                let account = try await services.auth.login(email: email, password: password)
                store.reset()
                store.setMyProfile(account.profile)
                session.signIn(account)
            } catch is CancellationError {
            } catch {
                toast = error.userMessage
            }
        }
    }

    private var intro: some View {
        VStack(alignment: .leading, spacing: 14) {
            HLogoLockup()
            Text("다시 만나서 반가워요")
                .font(.plex(28, .bold))
                .plexLineHeight(1.35, size: 28, weight: .bold)
                .foregroundStyle(.ink)
        }
    }

    private var form: some View {
        VStack(spacing: 12) {
            HTextField(placeholder: "이메일", text: $email, keyboard: .emailAddress, contentType: .username)
            HTextField(placeholder: "비밀번호", text: $password, isSecure: true, contentType: .password)
            HButton("로그인", action: logIn)
                .disabled(!canLogIn)
            links
        }
    }

    private var links: some View {
        HStack(spacing: 16) {
            Button("회원가입") { path.append(.signupAccount) }
                .font(.plex(14, .semibold))
                .foregroundStyle(.ink)
            Text("|")
                .font(.plex(14))
                .foregroundStyle(.controlOff)
            // 비밀번호 재설정 API 가 생기면 PasswordResetView 로 보내요.
            Button("비밀번호 찾기") { toast = ComingSoon.message }
                .font(.plex(14))
                .foregroundStyle(.textSecondary)
        }
        .buttonStyle(.plain)
        .frame(maxWidth: .infinity, minHeight: HLayout.minTouch)
    }

    private var divider: some View {
        HStack(spacing: 12) {
            Rectangle().fill(.line).frame(height: 1)
            Text("간편 로그인")
                .font(.caption13)
                .foregroundStyle(.textSecondary)
                .fixedSize()
            Rectangle().fill(.line).frame(height: 1)
        }
    }

    private var socialButtons: some View {
        VStack(spacing: 10) {
            // 간편 로그인은 MVP 범위 밖이라 아직 준비 중이에요.
            SocialLoginButton(provider: .apple) { toast = ComingSoon.message }
            SocialLoginButton(provider: .kakao) { toast = ComingSoon.message }
            SocialLoginButton(provider: .google) { toast = ComingSoon.message }
        }
    }
}

/// 간편 로그인 버튼 — 높이 52 · 모서리 14 · 아이콘 18 + 16 SemiBold
struct SocialLoginButton: View {
    enum Provider {
        case apple, kakao, google

        var title: String {
            switch self {
            case .apple: "Apple로 계속하기"
            case .kakao: "카카오로 계속하기"
            case .google: "Google로 계속하기"
            }
        }

        var iconName: String {
            switch self {
            case .apple: "social-apple"
            case .kakao: "social-kakao"
            case .google: "social-google"
            }
        }

        var background: Color {
            switch self {
            case .apple: .ink
            case .kakao: .kakaoContainer
            case .google: .bg
            }
        }

        var foreground: Color {
            switch self {
            case .apple: .onAccent
            case .kakao: .kakaoLabel
            case .google: .ink
            }
        }
    }

    let provider: Provider
    let action: () -> Void

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: HRadius.input, style: .continuous)
        Button(action: action) {
            HStack(spacing: 8) {
                Image(provider.iconName)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 18, height: 18)
                Text(provider.title)
                    .font(.plex(16, .semibold))
            }
            .foregroundStyle(provider.foreground)
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .background(provider.background, in: shape)
            .overlay {
                if provider == .google {
                    shape.strokeBorder(.border, lineWidth: 1)
                }
            }
            .contentShape(shape)
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    NavigationStack {
        LoginView(path: .constant([]))
    }
    .environment(AppSession())
    .environment(DataStore())
    .environment(Services.preview)
}
