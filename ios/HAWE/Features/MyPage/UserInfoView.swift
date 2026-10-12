import SwiftUI

/// 17-2 유저 정보 (Figma 1:1649)
/// 닉네임, 이메일, 가입일, 로그인 연결(카카오·Apple·Google 연결/해제), 비밀번호 변경(이메일 가입자만)
struct UserInfoView: View {
    @Binding var path: [MainRoute]

    @Environment(\.dismiss) private var dismiss
    @Environment(AppSession.self) private var session
    @State private var unlinking: LoginProvider?
    @State private var toast: String?

    private var account: MyAccount { session.account }

    var body: some View {
        VStack(spacing: 0) {
            HNavBar("유저 정보", leading: .back { dismiss() })

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    sectionLabel("기본 정보", top: 14)
                    // 프로필 수정 API 가 생기면 ProfileEditView 로 보내요.
                    HListRow("닉네임", value: account.profile.nickname, height: 56) {
                        toast = ComingSoon.message
                    }
                    infoRow("이메일", value: account.email ?? "없음")
                    infoRow("가입일", value: account.joinedAt.formatted(.dateTime.year().month(.defaultDigits).day().locale(Locale(identifier: "ko_KR"))))

                    sectionLabel("로그인 연결", top: 26)
                    Text("여러 방법을 연결해 두면 어떤 방법으로든 같은 계정에 로그인할 수 있어요.")
                        .font(.caption12)
                        .plexLineHeight(1.5, size: 12)
                        .foregroundStyle(.textSecondary)
                    providerRow(.kakao)
                    providerRow(.apple)
                    providerRow(.google)

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
        .hToast($toast)
        .confirmationDialog(
            "\(unlinking?.displayName ?? "") 연결을 해제할까요?",
            isPresented: Binding(get: { unlinking != nil }, set: { if !$0 { unlinking = nil } }),
            titleVisibility: .visible
        ) {
            Button("연결 해제", role: .destructive) {
                // TODO: 서버 연결 시 연결 해제 요청
                if let unlinking { session.account.linkedProviders.remove(unlinking) }
            }
            Button("취소", role: .cancel) {}
        } message: {
            Text("해제하면 이 방법으로는 로그인할 수 없어요.")
        }
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

    /// 높이 56 · 아이콘 원 32(안 16) · 이름 15 · 연결됨 13 SemiBold / 연결하기 알약 34
    private func providerRow(_ provider: LoginProvider) -> some View {
        let isLinked = account.linkedProviders.contains(provider)
        // 마지막 로그인 방법은 해제할 수 없어요 — 로그인할 방법이 사라지니까요.
        let canUnlink = isLinked && account.linkedProviders.count > 1
        return HStack(spacing: 12) {
            providerIcon(provider)
            Text(provider.displayName)
                .font(.body15)
                .foregroundStyle(.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
            if isLinked {
                // 간편 로그인 연결·해제는 MVP 범위 밖이라 준비 중이에요. (API 가 생기면 unlinking 확인창을 다시 써요)
                Button { toast = ComingSoon.message } label: {
                    Text("연결됨")
                        .font(.plex(13, .semibold))
                        .foregroundStyle(.ink)
                        .frame(minHeight: HLayout.minTouch)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(!canUnlink)
                .accessibilityHint(canUnlink ? "눌러서 연결 해제" : "마지막 로그인 방법이라 해제할 수 없어요")
            } else {
                Button {
                    toast = ComingSoon.message
                } label: {
                    Text("연결하기")
                        .font(.plex(13, .semibold))
                        .foregroundStyle(.ink)
                        .padding(.horizontal, 14)
                        .frame(height: 34)
                        .overlay(Capsule().strokeBorder(.border, lineWidth: 1))
                        .contentShape(Rectangle().inset(by: -5))
                }
                .buttonStyle(.plain)
            }
        }
        .frame(height: 56)
        .overlay(alignment: .bottom) {
            Rectangle().fill(.line).frame(height: 1)
        }
    }

    @ViewBuilder
    private func providerIcon(_ provider: LoginProvider) -> some View {
        let icon = Image("social-\(provider.rawValue)").resizable().scaledToFit().frame(width: 16, height: 16)
        switch provider {
        case .kakao:
            icon.frame(width: 32, height: 32).background(.kakaoContainer, in: Circle())
        case .apple:
            icon.frame(width: 32, height: 32).background(.ink, in: Circle())
        default:
            icon.frame(width: 32, height: 32)
                .background(.bg, in: Circle())
                .overlay(Circle().strokeBorder(.border, lineWidth: 1))
        }
    }

    /// 높이 60 · 15 + 설명 12 + 꺾쇠. 이메일 가입자가 아니면 흐리게 막아요.
    private var passwordRow: some View {
        let enabled = account.canChangePassword
        // 비밀번호 변경 API 가 생기면 ChangePasswordView(.changePassword)로 보내요.
        return Button { toast = ComingSoon.message } label: {
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
struct ChangePasswordView: View {
    @Environment(\.dismiss) private var dismiss

    @State private var current = ""
    @State private var new = ""
    @State private var confirm = ""

    private var isNewValid: Bool {
        new.count >= 8 && new.contains(where: { $0.isASCII && $0.isLetter }) && new.contains(where: \.isNumber)
    }

    private var canSave: Bool {
        !current.isEmpty && isNewValid && new == confirm && new != current
    }

    var body: some View {
        VStack(spacing: 0) {
            HNavBar("비밀번호 변경", leading: .back { dismiss() })
                .padding(.horizontal, HLayout.screenPadding)

            HCTAScaffold {
                VStack(alignment: .leading, spacing: 18) {
                    HTextField("현재 비밀번호", placeholder: "지금 쓰는 비밀번호", text: $current, isSecure: true, contentType: .password)
                    HTextField(
                        "새 비밀번호", placeholder: "8자 이상, 영문·숫자 포함", text: $new, isSecure: true, contentType: .newPassword,
                        error: new.isEmpty || isNewValid ? nil : "8자 이상, 영문과 숫자를 함께 써 주세요"
                    )
                    HTextField(
                        "새 비밀번호 확인", placeholder: "한 번 더 입력해 주세요", text: $confirm, isSecure: true, contentType: .newPassword,
                        error: confirm.isEmpty || confirm == new ? nil : "비밀번호가 일치하지 않아요"
                    )
                }
                .padding(.top, 12)
            } cta: {
                // TODO: 서버 연결 시 비밀번호 변경 요청
                HButton("변경하기") { dismiss() }
                    .disabled(!canSave)
            }
        }
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
    }
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
}
