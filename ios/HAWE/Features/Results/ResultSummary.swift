import SwiftUI

/// 결과 카드 상태. 잠금·권한 오류는 피드 쪽이 화면을 닫으므로 여기서는 숨기기만 해요.
enum AggregateLoad: Equatable {
    case loading, failed, hidden
    case loaded(MissionAggregate)
}

/// 집계 틀(밸런스 게임)의 "그룹 결과" 카드 (임시 UI: Figma 확정 시 교체). 값은 서버가 계산한 A/B 비율 그대로예요.
struct AggregateCard: View {
    let load: AggregateLoad
    let spec: MissionSpec
    let onRetry: () -> Void

    var body: some View {
        if load != .hidden {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text("그룹 결과")
                        .font(.sectionTitle)
                        .foregroundStyle(.ink)
                    Spacer()
                    if case .loaded(let value) = load {
                        Text("\(value.totalCount)명 참여")
                            .font(.caption13)
                            .foregroundStyle(.textSecondary)
                    }
                }
                switch load {
                case .loading:
                    Text("결과를 불러오는 중이에요")
                        .font(.caption13)
                        .foregroundStyle(.textSecondary)
                case .failed:
                    HStack {
                        Text("결과를 불러오지 못했어요")
                            .font(.plex(14))
                            .foregroundStyle(.textBody)
                        Spacer()
                        Button("다시 시도", action: onRetry)
                            .font(.plex(14, .semibold))
                            .foregroundStyle(.accentPressed)
                            .frame(minHeight: HLayout.minTouch)
                            .buttonStyle(.plain)
                    }
                case .hidden:
                    EmptyView()
                case .loaded(.balance(let value)):
                    BalanceSummary(value: value, spec: spec)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(20)
            .overlay(
                RoundedRectangle(cornerRadius: HRadius.largeCard, style: .continuous)
                    .strokeBorder(.line, lineWidth: 1)
            )
        }
    }
}

private struct BalanceSummary: View {
    let value: MissionAggregate.Balance
    let spec: MissionSpec

    var body: some View {
        let percents = ResultsFormat.balancePercents(countA: value.countA, countB: value.countB)
        VStack(spacing: 14) {
            BalanceBar(letter: "A", option: spec.optionA, count: value.countA, percent: percents.a, mine: value.myChoice == "A")
            BalanceBar(letter: "B", option: spec.optionB, count: value.countB, percent: percents.b, mine: value.myChoice == "B")
        }
    }
}

private struct BalanceBar: View {
    let letter: String
    let option: String?
    let count: Int
    let percent: Int
    let mine: Bool

    var body: some View {
        let name = option.nonBlank ?? letter
        VStack(spacing: 6) {
            HStack(spacing: 8) {
                Text("\(letter) · \(name)")
                    .font(.plex(15, .semibold))
                    .foregroundStyle(.ink)
                    .lineLimit(2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if mine { MineTag(text: "내 선택") }
                Text("\(count)명 · \(percent)%")
                    .font(.caption13)
                    .monospacedDigit()
                    .foregroundStyle(.textSecondary)
            }
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Capsule().fill(.surfaceStrong)
                    if percent > 0 {
                        Capsule().fill(mine ? Color.accent : Color.textTertiary)
                            .frame(width: proxy.size.width * CGFloat(percent) / 100)
                    }
                }
            }
            .frame(height: 10)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(letter) \(name), \(count)명, \(percent) 퍼센트" + (mine ? ", 내 선택" : ""))
    }
}

private struct MineTag: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.plex(12, .semibold))
            .foregroundStyle(.accentPressed)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(.accentSubtle, in: Capsule())
    }
}
