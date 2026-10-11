import SwiftUI

/// Figma `Input` — 높이 54 · 모서리 14 · 테두리 border · 좌우 16 · 16pt (placeholder textTertiary)
/// 위에 라벨(14 SemiBold), 아래에 안내 문구·글자 수(13)를 붙일 수 있어요.
struct HTextField: View {
    private let label: String?
    private let placeholder: String
    @Binding private var text: String
    private let isSecure: Bool
    private let helper: String?
    private let maxLength: Int?

    init(
        _ label: String? = nil,
        placeholder: String,
        text: Binding<String>,
        isSecure: Bool = false,
        helper: String? = nil,
        maxLength: Int? = nil
    ) {
        self.label = label
        self.placeholder = placeholder
        self._text = text
        self.isSecure = isSecure
        self.helper = helper
        self.maxLength = maxLength
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let label {
                Text(label)
                    .font(.fieldLabel)
                    .foregroundStyle(.textSecondary)
            }

            field
                .font(.plex(16))
                .foregroundStyle(.ink)
                .tint(.ink)
                .padding(.horizontal, 16)
                .frame(height: 54)
                .background(.bg, in: shape)
                .overlay(shape.strokeBorder(.border, lineWidth: 1))

            if helper != nil || maxLength != nil {
                HStack {
                    if let helper { Text(helper) }
                    Spacer(minLength: 8)
                    if let maxLength { Text("\(text.count) / \(maxLength)") }
                }
                .font(.caption13)
                .foregroundStyle(.textSecondary)
            }
        }
        .onChange(of: text) { _, newValue in
            if let maxLength, newValue.count > maxLength {
                text = String(newValue.prefix(maxLength))
            }
        }
    }

    @ViewBuilder
    private var field: some View {
        let prompt = Text(placeholder).foregroundStyle(.textTertiary)
        if isSecure {
            SecureField("", text: $text, prompt: prompt)
        } else {
            TextField("", text: $text, prompt: prompt)
        }
    }

    private var shape: RoundedRectangle {
        RoundedRectangle(cornerRadius: HRadius.input, style: .continuous)
    }
}

#Preview {
    @Previewable @State var email = ""
    @Previewable @State var nickname = "하늘"
    VStack(spacing: 18) {
        HTextField("이메일", placeholder: "example@email.com", text: $email)
        HTextField("비밀번호", placeholder: "8자 이상, 영문·숫자 포함", text: .constant(""), isSecure: true)
        HTextField("닉네임", placeholder: "닉네임", text: $nickname, helper: "캐릭터와 이름은 나중에 바꿀 수 있어요", maxLength: 10)
    }
    .padding(24)
}
