import SwiftUI

/// A1 로그인 (Figma 1:190)
struct LoginView: View {
    @Binding var path: [AuthRoute]
    @Environment(AppSession.self) private var session

    @State private var email = ""
    @State private var password = ""

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
            // TODO: 서버 연결 시 실제 로그인. 지금은 그룹이 있는 사용자로 들어가요.
            HButton("로그인") { session.signIn(hasGroups: true) }
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
            // TODO: 비밀번호 찾기 화면은 Figma 에 없어요 — 방식 확인 필요
            Button("비밀번호 찾기") {}
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
            // TODO: 서버 연결 시 실제 간편 로그인. 지금은 신규 가입자로 보고 A3 로 가요.
            SocialLoginButton(provider: .apple) { path.append(.signupProfile(viaSocial: true)) }
            SocialLoginButton(provider: .kakao) { path.append(.signupProfile(viaSocial: true)) }
            SocialLoginButton(provider: .google) { path.append(.signupProfile(viaSocial: true)) }
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
}
