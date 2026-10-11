import SwiftUI

/// 로그인·회원가입 흐름: A1 로그인 → A2 계정 정보 → A3 프로필
/// 간편 로그인 신규 가입자는 A2 를 건너뛰고 A3 로 가요 (CLAUDE.md §4).
enum AuthRoute: Hashable {
    case signupAccount
    /// 이메일 가입이면 A2 에서 넣은 이메일, 간편 로그인이면 그 방법
    case signupProfile(provider: LoginProvider, email: String?)
    case passwordReset(email: String)
}

struct AuthFlowView: View {
    @State private var path: [AuthRoute] = []

    var body: some View {
        NavigationStack(path: $path) {
            LoginView(path: $path)
                .navigationDestination(for: AuthRoute.self) { route in
                    switch route {
                    case .signupAccount:
                        SignupAccountView(path: $path)
                    case .signupProfile(let provider, let email):
                        SignupProfileView(provider: provider, email: email)
                    case .passwordReset(let email):
                        PasswordResetView(initialEmail: email)
                    }
                }
        }
    }
}

#Preview {
    AuthFlowView()
        .environment(AppSession())
        .environment(DataStore())
}
