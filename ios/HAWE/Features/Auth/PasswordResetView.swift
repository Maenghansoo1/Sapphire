import SwiftUI

/// 비밀번호 찾기 — 가입한 이메일로 재설정 링크를 보내요.
/// Figma 에 프레임이 없어서 A2 와 같은 틀(헤더 + 제목 26 + 입력창 + 하단 CTA)로 만들었어요.
struct PasswordResetView: View {
    var initialEmail = ""

    @Environment(\.dismiss) private var dismiss

    @State private var email = ""
    @State private var sentTo: String?

    private var trimmedEmail: String {
        email.trimmingCharacters(in: .whitespaces)
    }

    var body: some View {
        VStack(spacing: 0) {
            HNavBar(leading: .back { dismiss() })
                .padding(.horizontal, HLayout.screenPadding)

            HCTAScaffold {
                Group {
                    if let sentTo {
                        sentContent(sentTo)
                    } else {
                        formContent
                    }
                }
                .padding(.top, 12)
            } cta: {
                if sentTo != nil {
                    VStack(spacing: 12) {
                        HButton("로그인으로 돌아가기") { dismiss() }
                        // TODO: 서버 연결 시 재전송 요청
                        HButton("다시 보내기", kind: .secondary) {}
                    }
                } else {
                    // TODO: 서버 연결 시 재설정 메일 발송 요청
                    HButton("재설정 링크 보내기") { sentTo = trimmedEmail }
                        .disabled(trimmedEmail.isEmpty)
                }
            }
        }
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
        .onAppear { if email.isEmpty { email = initialEmail } }
        .animation(.default, value: sentTo)
    }

    private var formContent: some View {
        VStack(alignment: .leading, spacing: 18) {
            VStack(alignment: .leading, spacing: 6) {
                Text("비밀번호를 잊으셨나요?")
                    .font(.screenTitle)
                    .foregroundStyle(.ink)
                Text("가입한 이메일로 비밀번호 재설정 링크를 보내드려요.")
                    .font(.body15)
                    .foregroundStyle(.textSecondary)
            }
            HTextField("이메일", placeholder: "example@email.com", text: $email, keyboard: .emailAddress, contentType: .username)
        }
    }

    private func sentContent(_ address: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("메일을 보냈어요")
                .font(.screenTitle)
                .foregroundStyle(.ink)
            Text("\(Text(address).font(.plex(15, .semibold)).foregroundStyle(.ink))(으)로 보낸 링크를 눌러 새 비밀번호를 설정해 주세요. 메일이 보이지 않으면 스팸함도 확인해 주세요.")
                .font(.body15)
                .foregroundStyle(.textSecondary)
        }
    }
}

#Preview {
    NavigationStack {
        PasswordResetView(initialEmail: "haneul@email.com")
    }
}
