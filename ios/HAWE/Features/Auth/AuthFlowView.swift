import SwiftUI

/// 로그인·회원가입 흐름: A1 로그인 → A2 계정 정보 → A3 프로필
/// 간편 로그인 신규 가입자는 A2 를 건너뛰고 A3 로 가요 (CLAUDE.md §4).
enum AuthRoute: Hashable {
    case signupAccount
    case signupProfile(viaSocial: Bool)
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
                    case .signupProfile(let viaSocial):
                        SignupProfileView(showsStep: !viaSocial)
                    }
                }
        }
    }
}

#Preview {
    AuthFlowView().environment(AppSession())
}
