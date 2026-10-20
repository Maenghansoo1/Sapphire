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
            Text("만나서 반가워요")
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

}

#Preview {
    NavigationStack {
        LoginView(path: .constant([]))
    }
    .environment(AppSession())
    .environment(DataStore())
    .environment(Services.preview)
}
