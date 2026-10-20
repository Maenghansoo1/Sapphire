import SwiftUI

/// 17-2 유저 정보 (Figma 1:1649)
/// 닉네임, 이메일, 가입일, 비밀번호 변경(이메일 가입자만)
struct UserInfoView: View {
    @Binding var path: [MainRoute]

    @Environment(\.dismiss) private var dismiss
    @Environment(AppSession.self) private var session

    private var account: MyAccount { session.account }

    var body: some View {
        VStack(spacing: 0) {
            HNavBar("유저 정보", leading: .back { dismiss() })

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    sectionLabel("기본 정보", top: 14)
                    HListRow("닉네임", value: account.profile.nickname, height: 56) {
                        path.append(.myPage(.profileEdit))
                    }
                    infoRow("이메일", value: account.email ?? "없음")
                    infoRow("가입일", value: account.joinedAt.formatted(.dateTime.year().month(.defaultDigits).day().locale(Locale(identifier: "ko_KR"))))

                    sectionLabel("보안", top: 26)
                    passwordRow
                }
                .padding(.bottom, 24)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .padding(.horizontal, HLayout.screenPaddingCompact)
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
    }

    private func sectionLabel(_ title: String, top: CGFloat) -> some View {
        Text(title)
            .font(.plex(13, .semibold))
            .foregroundStyle(.textSecondary)
            .padding(.top, top)
            .padding(.bottom, 4)
    }

    /// 높이 56 · 라벨 15 ink + 값 15 textBody
    private func infoRow(_ label: String, value: String) -> some View {
        HStack {
            Text(label).foregroundStyle(.ink)
            Spacer(minLength: 12)
            Text(value).foregroundStyle(.textBody).lineLimit(1)
        }
        .font(.body15)
        .frame(height: 56)
        .overlay(alignment: .bottom) {
            Rectangle().fill(.line).frame(height: 1)
        }
    }

    /// 높이 60 · 15 + 설명 12 + 꺾쇠. 이메일 가입자가 아니면 흐리게 막아요.
    private var passwordRow: some View {
        let enabled = account.canChangePassword
        return Button { path.append(.myPage(.changePassword)) } label: {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("비밀번호 변경")
                        .font(.body15)
                        .foregroundStyle(enabled ? Color.ink : Color.textTertiary)
                    Text("이메일로 가입한 계정만 사용할 수 있어요")
                        .font(.caption12)
                        .foregroundStyle(.textSecondary)
                }
                Spacer()
                HIconView(icon: .chevronRight, size: 16, color: .textTertiary)
            }
            .frame(height: 60)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .overlay(alignment: .bottom) {
            Rectangle().fill(.line).frame(height: 1)
        }
    }
}

/// 비밀번호 변경 — Figma 에 화면이 없어서 A2 입력창 모양으로 만들었어요.
/// 바꾸면 서버가 다른 기기의 로그인을 끊고 이 기기에는 새 토큰을 줘요 (B3).
struct ChangePasswordView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(Services.self) private var services

    @State private var form = ChangePasswordState()
    @State private var isSaving = false
    @State private var currentError: String?
    @State private var toast: String?

    var body: some View {
        VStack(spacing: 0) {
            HNavBar("비밀번호 변경", leading: .back { dismiss() })
                .padding(.horizontal, HLayout.screenPadding)

            HCTAScaffold {
                VStack(alignment: .leading, spacing: 18) {
                    HTextField("현재 비밀번호", placeholder: "지금 쓰는 비밀번호", text: $form.current, isSecure: true,
                               contentType: .password, error: currentError)
                    HTextField("새 비밀번호", placeholder: "8자 이상, 영문·숫자 포함", text: $form.new, isSecure: true,
                               contentType: .newPassword, error: form.newError)
                    HTextField("새 비밀번호 확인", placeholder: "한 번 더 입력해 주세요", text: $form.confirm, isSecure: true,
                               contentType: .newPassword, error: form.confirmError)
                }
                .padding(.top, 12)
            } cta: {
                HButton(isSaving ? "변경 중" : "변경하기", action: save)
                    .disabled(!form.canSave || isSaving)
            }
        }
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
        .hToast($toast)
        .onChange(of: form.current) { _, _ in currentError = nil }
    }

    private func save() {
        guard form.canSave, !isSaving else { return }
        isSaving = true
        Task {
            defer { isSaving = false }
            do {
                try await services.auth.changePassword(current: form.current, new: form.new)
                toast = "비밀번호를 바꿨어요"
                try? await Task.sleep(for: .seconds(1.6))
                dismiss()
            } catch is CancellationError {
            } catch {
                if error.apiCode == "WRONG_CURRENT_PASSWORD" {
                    currentError = "현재 비밀번호가 맞지 않아요"
                } else {
                    toast = error.userMessage
                }
            }
        }
    }
}

/// 비밀번호 변경 입력 규칙 (Android `ChangePasswordState`)
struct ChangePasswordState: Equatable {
    var current = ""
    var new = ""
    var confirm = ""

    var isNewValid: Bool {
        new.count >= 8 && new.contains { $0.isASCII && $0.isLetter } && new.contains { $0.isASCII && $0.isNumber }
    }

    var newError: String? { !new.isEmpty && !isNewValid ? "8자 이상, 영문과 숫자를 함께 써 주세요" : nil }
    var confirmError: String? { !confirm.isEmpty && confirm != new ? "비밀번호가 일치하지 않아요" : nil }
    var canSave: Bool { !current.isEmpty && isNewValid && confirm == new && new != current }
}

#Preview("유저 정보") {
    NavigationStack {
        UserInfoView(path: .constant([]))
    }
    .environment(AppSession(state: .signedIn))
}

#Preview("비밀번호 변경") {
    NavigationStack {
        ChangePasswordView()
    }
    .environment(Services.preview)
}
