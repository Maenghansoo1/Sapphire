import SwiftUI

/// Figma `Checkbox` — 22×22 · 모서리 6 · 테두리 1.5
/// Off: 흰 바탕 + controlBorder / On: ink 바탕 + 흰 체크(14)
/// 라벨을 넘기면 한 줄 전체(높이 44)가 눌려요.
struct HCheckbox<Label: View>: View {
    @Binding private var isOn: Bool
    private let label: Label

    init(isOn: Binding<Bool>, @ViewBuilder label: () -> Label) {
        self._isOn = isOn
        self.label = label()
    }

    var body: some View {
        Button {
            isOn.toggle()
        } label: {
            HStack(spacing: 10) {
                HCheckboxMark(isOn: isOn)
                label
            }
            .frame(minWidth: HLayout.minTouch, minHeight: HLayout.minTouch, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isOn ? .isSelected : [])
    }
}

extension HCheckbox where Label == EmptyView {
    init(isOn: Binding<Bool>) {
        self.init(isOn: isOn) { EmptyView() }
    }
}

/// 체크박스 모양만 (상태 표시용)
struct HCheckboxMark: View {
    let isOn: Bool

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: HRadius.checkbox, style: .continuous)
        ZStack {
            shape.fill(isOn ? .ink : .bg)
            shape.strokeBorder(isOn ? .ink : .controlBorder, lineWidth: 1.5)
            if isOn {
                HIconView(icon: .checkSmall, size: 14, color: .onAccent)
            }
        }
        .frame(width: 22, height: 22)
    }
}

#Preview {
    @Previewable @State var all = false
    @Previewable @State var terms = true
    VStack(alignment: .leading, spacing: 0) {
        HCheckbox(isOn: $all) {
            Text("전체 동의").font(.plex(15, .bold)).foregroundStyle(.ink)
        }
        HCheckbox(isOn: $terms) {
            Text("[필수] 서비스 이용약관 동의").font(.plex(14)).foregroundStyle(.ink)
        }
        HCheckbox(isOn: $terms)
    }
    .padding(20)
}
