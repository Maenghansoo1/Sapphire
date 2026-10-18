import SwiftUI

/// 읽기 전용 슬라이더 막대 위의 점 하나. `value` 는 0~100.
struct HSliderMark: Hashable {
    var value: Int
    var color: Color
    var size: CGFloat = 14
}

/// 읽기 전용 슬라이더 막대 (임시 UI: Figma 디자인 확정 시 교체).
/// 0~100 위치에 점을 찍고, `average` 가 있으면 그 자리에 세로선을 그어요. VoiceOver 설명은 부르는 쪽에서 붙여요.
struct HSliderTrack: View {
    let marks: [HSliderMark]
    var average: Double? = nil

    var body: some View {
        let largest = marks.map(\.size).max() ?? 14
        Canvas { context, size in
            let radius = largest / 2
            let width = max(1, size.width - radius * 2)
            let trackHeight: CGFloat = 6
            let centerY = size.height / 2
            func x(_ value: Double) -> CGFloat { radius + width * CGFloat(min(max(value, 0), 100) / 100) }

            context.fill(
                Path(roundedRect: CGRect(x: radius, y: centerY - trackHeight / 2, width: width, height: trackHeight),
                     cornerRadius: trackHeight / 2),
                with: .color(.disabled)
            )
            if let average {
                var line = Path()
                line.move(to: CGPoint(x: x(average), y: 0))
                line.addLine(to: CGPoint(x: x(average), y: size.height))
                context.stroke(line, with: .color(.ink), lineWidth: 2)
            }
            for mark in marks {
                let center = CGPoint(x: x(Double(mark.value)), y: centerY)
                let outer = mark.size / 2 + 1.5
                context.fill(Path(ellipseIn: CGRect(x: center.x - outer, y: center.y - outer, width: outer * 2, height: outer * 2)),
                             with: .color(.bg))
                let inner = mark.size / 2
                context.fill(Path(ellipseIn: CGRect(x: center.x - inner, y: center.y - inner, width: inner * 2, height: inner * 2)),
                             with: .color(mark.color))
            }
        }
        .frame(height: largest + 8)
        .frame(maxWidth: .infinity)
        .accessibilityHidden(true)
    }
}

/// 슬라이더 양 끝 라벨. 길면 두 줄까지 보이고 말줄임해요.
struct HSliderEndLabels: View {
    let left: String?
    let right: String?

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text(left ?? "")
                .frame(maxWidth: .infinity, alignment: .leading)
            Text(right ?? "")
                .multilineTextAlignment(.trailing)
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .font(.caption13)
        .foregroundStyle(.textSecondary)
        .lineLimit(2)
    }
}

#Preview {
    VStack(spacing: 12) {
        HSliderTrack(marks: [HSliderMark(value: 20, color: .textTertiary), HSliderMark(value: 70, color: .accent)], average: 45)
        HSliderEndLabels(left: "집콕", right: "밖으로")
    }
    .padding(24)
}
