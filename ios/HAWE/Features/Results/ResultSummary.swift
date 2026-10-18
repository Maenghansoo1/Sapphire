import SwiftUI

/// 결과 카드 상태. 잠금·권한 오류는 피드 쪽이 화면을 닫으므로 여기서는 숨기기만 해요.
enum AggregateLoad: Equatable {
    case loading, failed, hidden
    case loaded(MissionAggregate)
}

/// 집계 틀의 "그룹 결과" 카드 (임시 UI: Figma 확정 시 교체). 값은 모두 서버가 계산한 그대로 보여요.
/// 밸런스는 A/B 비율 막대, 슬라이더는 평균·분포 점·내 위치 차이, 다수결 예측은 실제 평균과 순위표.
struct AggregateCard: View {
    let load: AggregateLoad
    let spec: MissionSpec
    let members: [GroupMember]
    let myUserID: Int
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
                case .loaded(.slider(let value)):
                    SliderSummary(value: value, spec: spec, myUserID: myUserID)
                case .loaded(.consensus(let value)):
                    ConsensusSummary(value: value, spec: spec, members: members, myUserID: myUserID)
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

private struct SliderSummary: View {
    let value: MissionAggregate.Slider
    let spec: MissionSpec
    let myUserID: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            AverageLine(label: "평균", average: value.average)
            HSliderTrack(marks: value.distribution.map {
                HSliderMark(value: $0.number, color: $0.userID == myUserID ? .accent : .textTertiary)
            }, average: value.average)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(value.distribution.map { "\($0.nickname) \($0.number)" }.joined(separator: ", "))
            HSliderEndLabels(left: spec.left, right: spec.right)
            if let text = ResultsFormat.diffFromAverageText(value.diffFromAverage, spec: spec) {
                Text(text)
                    .font(.plex(14, .semibold))
                    .foregroundStyle(.ink)
            }
        }
    }
}

private struct ConsensusSummary: View {
    let value: MissionAggregate.Consensus
    let spec: MissionSpec
    let members: [GroupMember]
    let myUserID: Int

    var body: some View {
        let ranks = ResultsFormat.competitionRanks(value.ranking)
        VStack(alignment: .leading, spacing: 10) {
            AverageLine(label: "실제 평균", average: value.average)
            HSliderTrack(marks: value.ranking.map {
                HSliderMark(value: $0.guess, color: value.winnerUserIDs.contains($0.userID) ? .accent : .textTertiary)
            }, average: value.average)
            HSliderEndLabels(left: spec.left, right: spec.right)
            if let text = ResultsFormat.consensusWinnerText(value.ranking, winnerUserIDs: value.winnerUserIDs, myUserID: myUserID) {
                Text(text)
                    .font(.plex(14, .semibold))
                    .foregroundStyle(.ink)
            }
            VStack(spacing: 0) {
                ForEach(Array(value.ranking.enumerated()), id: \.offset) { index, entry in
                    row(entry, rank: ranks[index])
                }
            }
        }
    }

    private func row(_ entry: ConsensusRank, rank: Int) -> some View {
        let avatar = members.first { $0.id == entry.userID }?.displayed.avatar ?? .character(.default)
        let name = entry.userID == myUserID ? "\(entry.nickname) (나)" : entry.nickname
        let winner = value.winnerUserIDs.contains(entry.userID)
        return HStack(spacing: 10) {
            Text("\(rank)")
                .font(.plex(15, .bold))
                .monospacedDigit()
                .foregroundStyle(winner ? Color.accentPressed : Color.ink)
                .frame(width: 18, alignment: .leading)
            HAvatar(content: avatar, size: 28)
            Text(name)
                .font(.plex(14, .semibold))
                .foregroundStyle(.ink)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            if winner { MineTag(text: "적중") }
            Text("예측 \(entry.guess) · 차이 \(ResultsFormat.decimalText(entry.diff))")
                .font(.caption12)
                .monospacedDigit()
                .foregroundStyle(.textSecondary)
        }
        .frame(minHeight: 48)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(rank)등 \(name), 예측 \(entry.guess), 차이 \(ResultsFormat.decimalText(entry.diff))" + (winner ? ", 가장 가깝게 맞힘" : ""))
    }
}

private struct AverageLine: View {
    let label: String
    let average: Double?

    var body: some View {
        HStack(alignment: .lastTextBaseline, spacing: 8) {
            Text(label)
                .font(.caption13)
                .foregroundStyle(.textSecondary)
            Text(average.map(ResultsFormat.decimalText) ?? "-")
                .font(.plex(28, .bold))
                .monospacedDigit()
                .foregroundStyle(.ink)
        }
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
