import SwiftUI

/// 14 작품 상세 · 댓글 (사진 1:1082 / 그림 1:1130 / 글 1:1179 / 음성 1:1227)
/// 작품 + 좋아요(토글) + 댓글 목록 + 하단 댓글 입력. 내 댓글은 지울 수 있어요.
struct SubmissionDetailView: View {
    let feed: ResultFeed
    let submissionID: Submission.ID

    @Environment(\.dismiss) private var dismiss
    @State private var draft = ""
    @State private var deletingComment: SubmissionComment?
    @FocusState private var isInputFocused: Bool

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
                VStack(alignment: .leading, spacing: 0) {
                    artwork(submission)
                        .padding(.top, 12)
                        .padding(.horizontal, HLayout.screenPaddingCompact)
                    likes(submission)
                        .padding(.top, 6)
                        .padding(.horizontal, HLayout.screenPaddingCompact)
                    comments(submission)
                }
                .padding(.bottom, 16)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            commentBar(submission)
        }
        .alert("댓글을 지울까요?", isPresented: Binding(
            get: { deletingComment != nil },
            set: { if !$0 { deletingComment = nil } }
        )) {
            Button("취소", role: .cancel) {}
            Button("지우기", role: .destructive) {
                if let comment = deletingComment {
                    feed.deleteComment(comment.id, from: submission.id)
                }
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
                    Text("\(submission.createdAt.koreanMonthDay) · \(submission.type.displayName)")
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

    // MARK: - Likes

    private func likes(_ submission: Submission) -> some View {
        HStack(spacing: 10) {
            LikeButton(count: submission.likeCount, isLiked: submission.likedByMe) {
                feed.toggleLike(submission.id)
            }
            if let text = likersText(submission) {
                Text(text)
                    .font(.caption13)
                    .foregroundStyle(.textSecondary)
                    .lineLimit(1)
            }
        }
    }

    /// "지우, 서연님 외 2명이 좋아해요"
    private func likersText(_ submission: Submission) -> String? {
        let names = submission.likerNames
        guard !names.isEmpty else { return nil }
        let shown = names.prefix(2).joined(separator: ", ")
        let others = submission.likeCount - min(names.count, 2)
        return others > 0 ? "\(shown)님 외 \(others)명이 좋아해요" : "\(shown)님이 좋아해요"
    }

    // MARK: - Comments

    /// 위 구분선 · 위 14 · 간격 16, 아바타 32 + 이름 13 SemiBold · 시간 12 + 본문 14 (줄 높이 1.55)
    private func comments(_ submission: Submission) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("댓글 \(submission.comments.count)")
                .font(.plex(14, .bold))
                .foregroundStyle(.ink)

            ForEach(submission.comments) { comment in
                HStack(alignment: .top, spacing: 10) {
                    HAvatar(content: comment.author.avatar, size: 32)
                    VStack(alignment: .leading, spacing: 3) {
                        HStack(alignment: .firstTextBaseline, spacing: 6) {
                            Text(comment.author.nickname)
                                .font(.plex(13, .semibold))
                                .foregroundStyle(.ink)
                            Text(comment.createdAt.koreanRelative)
                                .font(.caption12)
                                .foregroundStyle(.textSecondary)
                            if comment.isMine {
                                // Figma 에 삭제 모양이 없어서 시간 옆 작은 글자로 뒀어요.
                                Button("삭제") { deletingComment = comment }
                                    .font(.caption12)
                                    .foregroundStyle(.textTertiary)
                                    .buttonStyle(.plain)
                                    .padding(.leading, 2)
                            }
                        }
                        Text(comment.body)
                            .font(.plex(14))
                            .plexLineHeight(1.55, size: 14)
                            .foregroundStyle(.ink)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 14)
        .padding(.horizontal, HLayout.screenPaddingCompact)
        .overlay(alignment: .top) {
            Rectangle().fill(.line).frame(height: 1)
        }
    }

    // MARK: - Comment bar

    /// 위 테두리 border · 위 10 · 좌우 16 · 입력 알약 44 + 등록 44
    private func commentBar(_ submission: Submission) -> some View {
        let trimmed = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        return HStack(spacing: 8) {
            TextField("", text: $draft, prompt: Text("\(submission.author.nickname)님에게 댓글 남기기").foregroundStyle(.textTertiary))
                .font(.plex(15))
                .foregroundStyle(.ink)
                .tint(.ink)
                .focused($isInputFocused)
                .submitLabel(.send)
                .onSubmit { send(to: submission.id) }
                .padding(.horizontal, 16)
                .frame(height: 44)
                .overlay(Capsule().strokeBorder(.border, lineWidth: 1))

            Button { send(to: submission.id) } label: {
                Text("등록")
                    .font(.plex(15, .semibold))
                    .foregroundStyle(trimmed.isEmpty ? .textSecondary : .onAccent)
                    .padding(.horizontal, 16)
                    .frame(height: 44)
                    .background(trimmed.isEmpty ? Color.disabled : Color.accent, in: Capsule())
            }
            .buttonStyle(.plain)
            .disabled(trimmed.isEmpty)
        }
        .padding(.horizontal, 16)
        .padding(.top, 10)
        .padding(.bottom, 6)
        .background(.bg)
        .overlay(alignment: .top) {
            Rectangle().fill(.border).frame(height: 1)
        }
    }

    private func send(to id: Submission.ID) {
        let body = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else { return }
        feed.addComment(body, to: id)
        draft = ""
    }
}

#Preview("사진") {
    let feed = ResultFeed(result: .mock(type: .photo, topic: "지금 창밖으로 보이는 풍경", date: .now))
    NavigationStack {
        SubmissionDetailView(feed: feed, submissionID: feed.result.submissions[0].id)
    }
}

#Preview("음성") {
    let feed = ResultFeed(result: .mock(type: .voice, topic: "오늘 하루를 10초로 말해줘", date: .now))
    NavigationStack {
        SubmissionDetailView(feed: feed, submissionID: feed.result.submissions[0].id)
    }
}
