import SwiftUI

/// 사진 작품 — surfaceStrong · 모서리 20. 불러오기 전에는 "[민준의 사진]" 자리 표시.
struct PhotoArtwork: View {
    let submission: Submission
    var height: CGFloat

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: HRadius.card, style: .continuous)
        ZStack {
            shape.fill(.surfaceStrong)
            if let url = submission.mediaURL, !url.isFileURL {
                AsyncImage(url: url) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFill()
                    } else {
                        placeholder
                    }
                }
            } else {
                placeholder
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .clipShape(shape)
    }

    private var placeholder: some View {
        Text("[\(submission.author.nickname)의 사진]")
            .font(.plex(14))
            .foregroundStyle(.textSecondary)
    }
}

/// 그림 작품 — 흰 바탕 + 테두리 line. 그림은 칸 안에 맞춰요.
struct DrawingArtwork: View {
    let submission: Submission
    var height: CGFloat
    var cornerRadius: CGFloat = HRadius.card
    /// 칸 안 그림 크기 (Figma: 165 칸에 130, 300 칸에 240)
    var drawingSize: CGFloat

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        ZStack {
            shape.fill(.bg)
            if let url = submission.mediaURL, !url.isFileURL {
                AsyncImage(url: url) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFit().frame(width: drawingSize, height: drawingSize)
                    } else {
                        placeholder
                    }
                }
            } else {
                placeholder
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .clipShape(shape)
        .overlay(shape.strokeBorder(.line, lineWidth: 1))
    }

    private var placeholder: some View {
        Text("[\(submission.author.nickname)의 그림]")
            .font(.plex(14))
            .foregroundStyle(.textSecondary)
    }
}

/// 아직 올리지 않은 멤버 — Figma 에 없어서 목록 끝에 '아직' 으로 보여 줘요 (CLAUDE.md §5-2)
struct PendingMembers: View {
    let members: [Profile]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("아직 안 올렸어요")
                .font(.plex(14, .bold))
                .foregroundStyle(.ink)
                .padding(.bottom, 6)
            ForEach(members, id: \.self) { member in
                HStack(spacing: 10) {
                    HAvatar(content: member.avatar, size: 34)
                    Text(member.nickname)
                        .font(.plex(15, .semibold))
                        .foregroundStyle(.ink)
                    Spacer()
                    Text("아직")
                        .font(.caption13)
                        .foregroundStyle(.textTertiary)
                }
                .frame(height: 48)
            }
        }
    }
}

/// 지각 제출 표시
enum LateLabel {
    static let text = "지각"
}

/// AI 심사 배지·한 줄 코멘트(B10, 임시 UI: Figma 확정 시 교체). 점수는 숨기고, 배지 없이 격려 코멘트만 보여 줄 수도 있어요.
/// 피드에서는 코멘트 1줄, 상세에서는 2줄까지.
struct AiReviewLine: View {
    let review: AiReview
    var commentLines = 1

    var body: some View {
        let label = ResultsFormat.aiBadgeLabel(review.badge)
        if label != nil || review.comment != nil {
            VStack(alignment: .leading, spacing: 6) {
                if let label {
                    HStack(spacing: 5) {
                        HIconView(icon: .badgeFitTemp, size: 16, color: .accentPressed)
                        Text(label)
                            .font(.plex(13, .semibold))
                            .foregroundStyle(.accentPressed)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(.accentSubtle, in: Capsule())
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(label)
                }
                if let comment = review.comment {
                    Text(comment)
                        .font(.caption13)
                        .foregroundStyle(.textSecondary)
                        .lineLimit(commentLines)
                }
            }
        }
    }
}

/// 제출물 본문. 새 틀은 틀별 모양(한 단어+이유, 별점+후기, 선택+이유, 슬라이더 위치)이고,
/// 그 밖의 글 틀은 지금까지처럼 글만 보여요. `large` 는 상세 화면용 큰 글씨.
struct TemplateAnswer: View {
    let submission: Submission
    let spec: MissionSpec
    var large = false

    var body: some View {
        let bodySize: CGFloat = large ? 17 : 15
        switch submission.template {
        case MissionTemplates.textOneWord:
            VStack(alignment: .leading, spacing: 8) {
                Text(submission.text ?? "")
                    .font(.plex(large ? 30 : 22, .bold))
                    .foregroundStyle(.ink)
                if let caption = submission.caption {
                    bodyText(caption, size: bodySize)
                }
            }
        case MissionTemplates.textReview:
            let stars = submission.number ?? 0
            VStack(alignment: .leading, spacing: 8) {
                Text(ResultsFormat.starsText(stars))
                    .font(.plex(large ? 24 : 18))
                    .foregroundStyle(.accent)
                    .accessibilityLabel("별점 \(stars)점")
                Text(submission.text ?? "")
                    .font(.plex(large ? 19 : 16))
                    .plexLineHeight(1.65, size: large ? 19 : 16)
                    .foregroundStyle(.ink)
                    .fixedSize(horizontal: false, vertical: true)
            }
        case MissionTemplates.textBalance:
            VStack(alignment: .leading, spacing: 10) {
                BalancePick(choice: submission.choice, spec: spec, large: large)
                if let caption = submission.caption {
                    bodyText(caption, size: bodySize)
                }
            }
        case MissionTemplates.sliderPosition, MissionTemplates.sliderTaste:
            SliderAnswer(label: nil, value: submission.number, spec: spec, large: large)
        case MissionTemplates.sliderConsensus:
            VStack(alignment: .leading, spacing: 16) {
                // 두 막대는 같은 눈금이라 양 끝 라벨은 아래에 한 번만
                SliderAnswer(label: "내 위치", value: submission.number, spec: spec, large: large, showsEndLabels: false)
                SliderAnswer(label: "평균 예측", value: submission.guess, spec: spec, large: large)
            }
        default:
            Text(submission.text ?? "")
                .font(.plex(16))
                .plexLineHeight(1.65, size: 16)
                .foregroundStyle(.ink)
                .multilineTextAlignment(.leading)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func bodyText(_ text: String, size: CGFloat) -> some View {
        Text(text)
            .font(.plex(size))
            .plexLineHeight(1.6, size: size)
            .foregroundStyle(.textBody)
            .multilineTextAlignment(.leading)
            .fixedSize(horizontal: false, vertical: true)
    }
}

private struct BalancePick: View {
    let choice: String?
    let spec: MissionSpec
    let large: Bool

    var body: some View {
        let option = choice == "A" ? spec.optionA : choice == "B" ? spec.optionB : nil
        HStack(spacing: 10) {
            Text(choice ?? "")
                .font(.plex(14, .bold))
                .foregroundStyle(.onAccent)
                .frame(width: large ? 32 : 28, height: large ? 32 : 28)
                .background(.accent, in: Circle())
            Text(option.nonBlank ?? "\(choice ?? "") 선택")
                .font(.plex(large ? 20 : 17, .semibold))
                .foregroundStyle(.ink)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// 읽기 전용 슬라이더 한 줄 (임시 UI): 라벨·값, 막대 위 점, 양 끝 라벨
private struct SliderAnswer: View {
    let label: String?
    let value: Int?
    let spec: MissionSpec
    let large: Bool
    var showsEndLabels = true

    var body: some View {
        if let value {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(label ?? "")
                        .font(.caption13)
                        .foregroundStyle(.textSecondary)
                    Spacer()
                    Text("\(value)")
                        .font(.plex(large ? 24 : 18, .bold))
                        .monospacedDigit()
                        .foregroundStyle(.ink)
                }
                HSliderTrack(marks: [HSliderMark(value: value, color: .accent, size: large ? 18 : 14)])
                if showsEndLabels {
                    HSliderEndLabels(left: spec.left, right: spec.right)
                }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel([label, "\(value)", spec.left.map { "왼쪽 \($0)" }, spec.right.map { "오른쪽 \($0)" }]
                .compactMap { $0 }.joined(separator: ", "))
        }
    }
}
