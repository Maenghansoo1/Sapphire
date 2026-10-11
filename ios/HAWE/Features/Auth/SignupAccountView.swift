import SwiftUI

/// A2 회원가입 · 계정 정보 (Figma 1:222)
struct SignupAccountView: View {
    @Binding var path: [AuthRoute]
    @Environment(\.dismiss) private var dismiss

    @State private var model = SignupAccountModel()
    @State private var openedLink: WebLink?

    var body: some View {
        VStack(spacing: 0) {
            HNavBar(leading: .back { dismiss() }) { HNavStep(text: "1 / 2") }
                .padding(.horizontal, HLayout.screenPadding)

            HCTAScaffold {
                VStack(alignment: .leading, spacing: 18) {
                    Text("계정을 만들어요")
                        .font(.screenTitle)
                        .foregroundStyle(.ink)
                    HTextField("이메일", placeholder: "example@email.com", text: $model.email, keyboard: .emailAddress, contentType: .username, error: model.emailError)
                    HTextField("비밀번호", placeholder: "8자 이상, 영문·숫자 포함", text: $model.password, isSecure: true, contentType: .newPassword, error: model.passwordError)
                    HTextField("비밀번호 확인", placeholder: "한 번 더 입력해 주세요", text: $model.passwordConfirm, isSecure: true, contentType: .newPassword, error: model.confirmError)
                    terms
                }
                .padding(.top, 12)
            } cta: {
                HButton("다음") { path.append(.signupProfile(viaSocial: false)) }
                    .disabled(!model.canProceed)
            }
        }
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
        .safariSheet($openedLink)
    }

    private var terms: some View {
        VStack(spacing: 0) {
            HCheckbox(isOn: $model.agreeAll) {
                termLabel("전체 동의", font: .plex(15, .bold))
            }
            termRow("[필수] 서비스 이용약관 동의", isOn: $model.agreeService, detail: AppLinks.termsOfService)
            termRow("[필수] 개인정보 처리방침 동의", isOn: $model.agreePrivacy, detail: AppLinks.privacyPolicy)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 6)
        .overlay(
            RoundedRectangle(cornerRadius: HRadius.button, style: .continuous)
                .strokeBorder(.line, lineWidth: 1)
        )
    }

    private func termRow(_ title: String, isOn: Binding<Bool>, detail: URL?) -> some View {
        HStack(spacing: 10) {
            HCheckbox(isOn: isOn) {
                termLabel(title, font: .plex(14))
            }
            if let detail {
                Button("보기") { openedLink = WebLink(url: detail) }
                    .font(.caption13)
                    .underline()
                    .foregroundStyle(.textSecondary)
                    .buttonStyle(.plain)
                    .frame(minHeight: HLayout.minTouch)
            }
        }
    }

    private func termLabel(_ title: String, font: Font) -> some View {
        Text(title)
            .font(font)
            .foregroundStyle(.ink)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// A2 입력·약관 상태
@Observable
final class SignupAccountModel {
    var email = ""
    var password = ""
    var passwordConfirm = ""

    var agreeService = false
    var agreePrivacy = false

    /// 전체 동의 — 켜고 끄면 두 항목이 함께 바뀌고, 두 항목이 모두 켜지면 같이 켜져요.
    var agreeAll: Bool {
        get { agreeService && agreePrivacy }
        set {
            agreeService = newValue
            agreePrivacy = newValue
        }
    }

    // MARK: 입력 검사 — 빈 칸일 때는 오류를 보여 주지 않아요.

    var isEmailValid: Bool {
        email.wholeMatch(of: /[^@\s]+@[^@\s]+\.[^@\s]+/) != nil
    }

    /// 8자 이상, 영문·숫자 포함 (Figma 안내 문구 기준)
    var isPasswordValid: Bool {
        password.count >= 8
            && password.contains(where: \.isASCIILetter)
            && password.contains(where: \.isNumber)
    }

    var isConfirmValid: Bool {
        !passwordConfirm.isEmpty && passwordConfirm == password
    }

    var emailError: String? {
        email.isEmpty || isEmailValid ? nil : "이메일 형식이 올바르지 않아요"
    }

    var passwordError: String? {
        password.isEmpty || isPasswordValid ? nil : "8자 이상, 영문과 숫자를 함께 써 주세요"
    }

    var confirmError: String? {
        passwordConfirm.isEmpty || passwordConfirm == password ? nil : "비밀번호가 일치하지 않아요"
    }

    /// '다음' 활성화: 입력이 모두 올바르고 [필수] 2개 동의 (CLAUDE.md §4 A2)
    var canProceed: Bool {
        isEmailValid && isPasswordValid && isConfirmValid && agreeService && agreePrivacy
    }
}

private extension Character {
    var isASCIILetter: Bool { isASCII && isLetter }
}

#Preview {
    NavigationStack {
        SignupAccountView(path: .constant([]))
    }
}
