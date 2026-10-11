import SwiftUI

/// 사진 작품 — surfaceStrong · 모서리 20. 서버 연결 전에는 "[민준의 사진]" 자리 표시.
struct PhotoArtwork: View {
    let submission: Submission
    var height: CGFloat

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: HRadius.card, style: .continuous)
        ZStack {
            shape.fill(.surfaceStrong)
            if let url = submission.mediaURL {
                AsyncImage(url: url) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    Color.surfaceStrong
                }
            } else {
                Text("[\(submission.author.nickname)의 사진]")
                    .font(.plex(14))
                    .foregroundStyle(.textSecondary)
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .clipShape(shape)
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
            if let url = submission.mediaURL {
                AsyncImage(url: url) { image in
                    image.resizable().scaledToFit()
                } placeholder: {
                    Color.clear
                }
                .frame(width: drawingSize, height: drawingSize)
            } else {
                Text("[\(submission.author.nickname)의 그림]")
                    .font(.plex(14))
                    .foregroundStyle(.textSecondary)
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .clipShape(shape)
        .overlay(shape.strokeBorder(.line, lineWidth: 1))
    }
}

/// 음성 파형 — 재생한 만큼 accent, 나머지는 controlBorder
struct VoiceBars: View {
    let levels: [CGFloat]
    var progress: Double = 0
    var barWidth: CGFloat = 3
    var spacing: CGFloat = 3
    var maxHeight: CGFloat = 24
    var minHeight: CGFloat = 6

    var body: some View {
        HStack(spacing: spacing) {
            ForEach(Array(levels.enumerated()), id: \.offset) { index, level in
                let played = Double(index) / Double(max(levels.count, 1)) < progress
                RoundedRectangle(cornerRadius: barWidth / 2)
                    .fill(played ? Color.accent : Color.controlBorder)
                    .frame(width: barWidth, height: max(minHeight, maxHeight * level))
            }
        }
        .frame(height: maxHeight, alignment: .center)
        .accessibilityHidden(true)
    }
}

/// 좋아요 (하트 20 + 수 14) — 누른 상태는 채운 하트(ink)
struct LikeButton: View {
    let count: Int
    let isLiked: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                HIconView(icon: isLiked ? .heartFill : .heart, size: 20)
                Text("\(count)")
                    .font(.plex(14))
                    .foregroundStyle(.ink)
                    .monospacedDigit()
            }
            .frame(height: 40)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(isLiked ? "좋아요 취소, \(count)개" : "좋아요, \(count)개")
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

extension Date {
    /// "오전 9:12"
    var koreanTime: String {
        formatted(.dateTime.hour().minute().locale(Locale(identifier: "ko_KR")))
    }

    /// "10월 11일"
    var koreanMonthDay: String {
        formatted(.dateTime.month(.defaultDigits).day().locale(Locale(identifier: "ko_KR")))
    }

    /// "2시간 전" — 1분 안이면 "방금"
    var koreanRelative: String {
        if Date.now.timeIntervalSince(self) < 60 { return "방금" }
        let formatter = RelativeDateTimeFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.unitsStyle = .full
        return formatter.localizedString(for: self, relativeTo: .now)
    }
}

extension TimeInterval {
    /// "0:09"
    var shortDuration: String {
        let total = Int(self.rounded(.down))
        return "\(total / 60):" + String(format: "%02d", total % 60)
    }
}
