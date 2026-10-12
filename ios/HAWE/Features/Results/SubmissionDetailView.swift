import SwiftUI

/// 14 작품 상세 (사진 1:1082 / 그림 1:1130 / 글 1:1179 / 음성 1:1227)
/// 서버 피드에서 받은 작품을 크게 보여 줘요. 좋아요·댓글은 서버에 생기면(7단계) 다시 붙여요.
struct SubmissionDetailView: View {
    let feed: ResultFeed
    let submissionID: Submission.ID

    @Environment(\.dismiss) private var dismiss

    private var submission: Submission? { feed.submission(submissionID) }

    var body: some View {
        Group {
            if let submission {
                content(submission)
            } else {
                Color.bg
            }
        }
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
    }

    private func content(_ submission: Submission) -> some View {
        VStack(spacing: 0) {
            header(submission)
                .padding(.horizontal, HLayout.screenPaddingCompact)

            ScrollView {
                artwork(submission)
                    .padding(.top, 12)
                    .padding(.horizontal, HLayout.screenPaddingCompact)
                    .padding(.bottom, 16)
            }
        }
    }

    // MARK: - Header

    /// 뒤로 + 가운데(아바타 30 · 이름 15 · 날짜·유형 13), 아래 주제 13 (Figma 의 ⋯ 메뉴는 쓰지 않아요)
    private func header(_ submission: Submission) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HNavBar(leading: .back { dismiss() })
            .overlay {
                HStack(spacing: 8) {
                    HAvatar(content: submission.author.avatar, size: 30)
                    Text(submission.author.nickname)
                        .font(.plex(15, .semibold))
                        .foregroundStyle(.ink)
                    Text("\(submission.createdAt.koreanMonthDay) · \(submission.type.displayName)" + (submission.isLate ? " · \(LateLabel.text)" : ""))
                        .font(.caption13)
                        .foregroundStyle(.textSecondary)
                }
                .lineLimit(1)
                .padding(.horizontal, HLayout.minTouch + 8)
            }

            Text(feed.result.topic)
                .font(.caption13)
                .foregroundStyle(.textSecondary)
                .lineLimit(1)
        }
    }

    // MARK: - Artwork

    @ViewBuilder
    private func artwork(_ submission: Submission) -> some View {
        switch submission.type {
        case .photo:
            PhotoArtwork(submission: submission, height: 300)
        case .draw:
            DrawingArtwork(submission: submission, height: 300, drawingSize: 240)
        case .write:
            // surface · 모서리 20 · 안쪽 24 · 19 Medium · 줄 높이 1.7
            Text(submission.text ?? "")
                .font(.plex(19, .medium))
                .plexLineHeight(1.7, size: 19, weight: .medium)
                .foregroundStyle(.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(24)
                .background(.surface, in: RoundedRectangle(cornerRadius: HRadius.card, style: .continuous))
                .textSelection(.enabled)
        case .voice:
            voicePlayer(submission)
        }
    }

    /// 테두리 line · 모서리 20 · 안쪽 24 · 파형(4 · 간격 4 · 높이 60) + 재생 60
    private func voicePlayer(_ submission: Submission) -> some View {
        let player = feed.player
        let isPlaying = player.playingID == submission.id
        let duration = submission.duration ?? 0
        return VStack(spacing: 18) {
            VoiceBars(
                levels: submission.waveform,
                progress: isPlaying ? player.progress : 0,
                barWidth: 4,
                spacing: 4,
                maxHeight: 60,
                minHeight: 8
            )
            HStack(spacing: 16) {
                Text((isPlaying ? player.elapsed : 0).shortDuration)
                    .font(.caption13)
                    .monospacedDigit()
                    .foregroundStyle(.textSecondary)
                Button { player.toggle(submission) } label: {
                    HIconView(icon: isPlaying ? .pause : .play, size: 22, color: .onAccent)
                        .frame(width: 60, height: 60)
                        .background(.accent, in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(isPlaying ? "일시정지" : "재생")
                Text(duration.shortDuration)
                    .font(.caption13)
                    .monospacedDigit()
                    .foregroundStyle(.textSecondary)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .overlay(
            RoundedRectangle(cornerRadius: HRadius.card, style: .continuous)
                .strokeBorder(.line, lineWidth: 1)
        )
        .onDisappear { player.stop() }
    }
}

#Preview {
    let store = DataStore.preview()
    let group = store.groups[0]
    let feed = ResultFeed(store: store, groupID: group.id, missionID: group.missions.last!.id)
    NavigationStack {
        SubmissionDetailView(feed: feed, submissionID: feed.result.submissions[0].id)
    }
}
