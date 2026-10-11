import SwiftUI

/// Figma `Button` — 하단 고정 CTA 는 primary 만, 보조 행동은 secondary/gray, 탈퇴·로그아웃 확정은 danger.
/// 높이 56(기본) · 모서리 16 · 좌우 20 · 17 SemiBold
struct HButton: View {
    enum Kind {
        case primary, secondary, gray, danger, disabled
    }

    private let title: String
    private let kind: Kind
    private let height: CGFloat
    private let action: () -> Void

    init(_ title: String, kind: Kind = .primary, height: CGFloat = HLayout.ctaHeight, action: @escaping () -> Void) {
        self.title = title
        self.kind = kind
        self.height = height
        self.action = action
    }

    var body: some View {
        Button(title, action: action)
            .buttonStyle(HButtonStyle(kind: kind, height: height))
            .disabled(kind == .disabled)
    }
}

struct HButtonStyle: ButtonStyle {
    let kind: HButton.Kind
    var height: CGFloat = HLayout.ctaHeight

    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        // .disabled(true) 가 걸리면 Disabled 모양으로 그려요 (예: 약관 미동의 시 '다음').
        let current = isEnabled ? kind : .disabled

        configuration.label
            .font(.plex(17, .semibold))
            .lineLimit(1)
            .foregroundStyle(foreground(current))
            .padding(.horizontal, 20)
            .frame(maxWidth: .infinity)
            .frame(height: height)
            .background(background(current, pressed: configuration.isPressed), in: shape)
            .overlay {
                if current == .secondary {
                    shape.strokeBorder(.border, lineWidth: 1)
                }
            }
            .contentShape(shape)
    }

    private var shape: RoundedRectangle {
        RoundedRectangle(cornerRadius: HRadius.button, style: .continuous)
    }

    private func foreground(_ kind: HButton.Kind) -> Color {
        switch kind {
        case .primary, .danger: .onAccent
        case .secondary, .gray: .ink
        case .disabled: .textSecondary
        }
    }

    private func background(_ kind: HButton.Kind, pressed: Bool) -> Color {
        switch kind {
        case .primary: pressed ? .accentPressed : .accent
        case .secondary: pressed ? .surfaceStrong : .bg
        case .gray: pressed ? .disabled : .surfaceStrong
        case .danger: pressed ? .danger.opacity(0.85) : .danger
        case .disabled: .disabled
        }
    }
}

#Preview {
    VStack(spacing: 12) {
        HButton("로그인", kind: .primary) {}
        HButton("초대 코드로 참여하기", kind: .secondary) {}
        HButton("계속 쓸게요", kind: .gray) {}
        HButton("로그아웃", kind: .danger, height: 50) {}
        HButton("탈퇴하기", kind: .disabled) {}
        HButton("다음", kind: .primary) {}.disabled(true)
    }
    .padding(24)
}
