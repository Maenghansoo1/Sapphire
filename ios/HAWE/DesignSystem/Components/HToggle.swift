import SwiftUI

/// Figma `Toggle` — 52×32 트랙(ON ink / OFF controlOff) + 지름 26 흰 손잡이(여백 3)
/// 사용: `Toggle("콕 찌르기", isOn: $on).toggleStyle(.hawe)` 또는 `HToggle(isOn: $on)`
struct HToggleStyle: ToggleStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 12) {
            configuration.label
            Spacer(minLength: 0)
            Button {
                withAnimation(.snappy(duration: 0.2)) { configuration.isOn.toggle() }
            } label: {
                Capsule()
                    .fill(configuration.isOn ? .ink : .controlOff)
                    .frame(width: 52, height: 32)
                    .overlay(alignment: configuration.isOn ? .trailing : .leading) {
                        Circle()
                            .fill(.bg)
                            .frame(width: 26, height: 26)
                            .padding(3)
                    }
                    .frame(minHeight: HLayout.minTouch)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            // 비활성(예: 전체 알림 OFF) 표시는 Figma 에 없어서 흐리게만 처리해요.
            .opacity(isEnabled ? 1 : 0.4)
        }
    }
}

extension ToggleStyle where Self == HToggleStyle {
    static var hawe: HToggleStyle { HToggleStyle() }
}

/// 라벨 없이 스위치만 필요할 때
struct HToggle: View {
    @Binding var isOn: Bool
    var accessibilityLabel: String = ""

    var body: some View {
        Toggle(accessibilityLabel, isOn: $isOn)
            .labelsHidden()
            .toggleStyle(.hawe)
            .fixedSize()
    }
}

#Preview {
    @Previewable @State var on = true
    @Previewable @State var off = false
    VStack(spacing: 16) {
        HToggle(isOn: $on)
        HToggle(isOn: $off)
        Toggle(isOn: $on) { Text("콕 찌르기").font(.body15) }.toggleStyle(.hawe)
        Toggle(isOn: $on) { Text("비활성").font(.body15) }.toggleStyle(.hawe).disabled(true)
    }
    .padding(20)
}
