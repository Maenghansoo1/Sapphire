import PhotosUI
import SwiftUI

/// 14 작품 상세 (사진 1:1082 / 그림 1:1130 / 글 1:1179)
/// 작품을 크게 보여 주고, 아래에 스티커·댓글(멘션·사진 첨부)을 달 수 있어요 (7A).
struct SubmissionDetailView: View {
    let feed: ResultFeed
    let submissionID: Submission.ID

    @Environment(\.dismiss) private var dismiss
    @Environment(Services.self) private var services
    @State private var aiGuess = AiGuessRunner()

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
        .aiGuessCard(aiGuess)
    }

    private func content(_ submission: Submission) -> some View {
        let result = feed.result
        return VStack(spacing: 0) {
            header(submission)
                .padding(.horizontal, HLayout.screenPaddingCompact)

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    artwork(submission)
                    if result.type == .photo, let review = submission.aiReview {
                        AiReviewLine(review: review, commentLines: 2).padding(.top, 12)
                    }
                    if result.type == .draw, aiGuess.isAvailable {
                        AiShowButton(fillsWidth: true) {
                            let url = submission.mediaURL
                            aiGuess.guess(mine: submission.isMine, load: { await InkSource.ink(url: url) }) {
                                DrawSubjectMatcher.match(template: result.template, topic: result.topic, spec: result.spec, categories: $0)
                            }
                        }
                        .padding(.top, 12)
                    }
                    SubmissionSocial(submissionID: submission.id, initialReactions: submission.reactions,
                                     members: feed.members, repository: services.submissions)
                        .padding(.top, 24)
                }
                .padding(.top, 12)
                .padding(.horizontal, HLayout.screenPaddingCompact)
                .padding(.bottom, 24)
            }
            .scrollDismissesKeyboard(.interactively)
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
                    VStack(alignment: .leading, spacing: 0) {
                        Text(submission.author.nickname)
                            .font(.plex(15, .semibold))
                            .foregroundStyle(.ink)
                        Text("\(submission.createdAt.koreanMonthDay) · \(feed.result.type.displayName)" + (submission.isLate ? " · \(LateLabel.text)" : ""))
                            .font(.caption13)
                            .foregroundStyle(.textSecondary)
                    }
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
        let shape = RoundedRectangle(cornerRadius: HRadius.card, style: .continuous)
        switch feed.result.type {
        case .photo:
            PhotoArtwork(submission: submission, height: 300)
        case .draw:
            DrawingArtwork(submission: submission, height: 300, drawingSize: 240)
        case .write, .slider:
            if ResultsFormat.hasTemplateAnswer(submission.template) {
                TemplateAnswer(submission: submission, spec: feed.result.spec, large: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(24)
                    .background(.surface, in: shape)
                    .textSelection(.enabled)
            } else {
                // surface · 모서리 20 · 안쪽 24 · 19 Medium · 줄 높이 1.7
                Text(submission.text ?? "")
                    .font(.plex(19, .medium))
                    .plexLineHeight(1.7, size: 19, weight: .medium)
                    .foregroundStyle(.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(24)
                    .background(.surface, in: shape)
                    .textSelection(.enabled)
            }
        }
    }
}

/// 스티커·댓글 (7A, 임시 UI: 스티커는 이모지) — 반응은 서버가 돌려준 값으로만 다시 그려요 (낙관 반영 없음).
struct SubmissionSocial: View {
    let submissionID: Int
    let initialReactions: [Reaction]
    let members: [GroupMember]
    let repository: SubmissionRepository

    @State private var reactions: [Reaction] = []
    @State private var comments: [SubmissionComment]?
    @State private var loadFailed = false
    @State private var reload = 0
    @State private var message: String?
    @State private var started = false

    private var names: [Int: String] {
        Dictionary(members.map { ($0.id, $0.displayed.nickname) }, uniquingKeysWith: { a, _ in a })
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            stickerBar
            VStack(alignment: .leading, spacing: 12) {
                Text("댓글 \(comments?.count ?? 0)")
                    .font(.plex(15, .bold))
                    .foregroundStyle(.ink)
                if loadFailed {
                    Button { reload += 1 } label: {
                        Text("댓글을 불러오지 못했어요. 눌러서 다시 시도")
                            .font(.caption13)
                            .foregroundStyle(.textSecondary)
                    }
                    .buttonStyle(.plain)
                } else if let comments {
                    if comments.isEmpty {
                        Text("첫 댓글을 남겨 보세요")
                            .font(.caption13)
                            .foregroundStyle(.textSecondary)
                    } else {
                        ForEach(comments) { comment in
                            CommentRow(comment: comment) { delete(comment) }
                        }
                    }
                }
            }
            CommentComposer(mentionable: members.filter { !$0.isMe }, onSend: send)
            if let message {
                Text(message)
                    .font(.caption13)
                    .foregroundStyle(.danger)
            }
        }
        .onAppear {
            if !started {
                reactions = initialReactions
                started = true
            }
        }
        .task(id: reload) { await loadComments() }
    }

    private var stickerBar: some View {
        let byType = Dictionary(reactions.map { ($0.sticker, $0) }, uniquingKeysWith: { a, _ in a })
        return VStack(alignment: .leading, spacing: 8) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(Sticker.allCases, id: \.self) { sticker in
                        let reaction = byType[sticker]
                        let mine = reaction?.reactedByMe == true
                        let shape = RoundedRectangle(cornerRadius: 20, style: .continuous)
                        Button { toggle(sticker) } label: {
                            HStack(spacing: 6) {
                                Text(sticker.emoji).font(.plex(17))
                                Text("\(reaction?.count ?? 0)")
                                    .font(.plex(14))
                                    .monospacedDigit()
                                    .foregroundStyle(.ink)
                            }
                            .padding(.horizontal, 14)
                            .frame(height: 40)
                            .background(mine ? Color.accentSubtle : Color.bg, in: shape)
                            .overlay(shape.strokeBorder(mine ? Color.accent : Color.border, lineWidth: 1))
                            .contentShape(shape)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("\(sticker.label) \(mine ? "취소" : "남기기"), \(reaction?.count ?? 0)명")
                    }
                }
            }
            ForEach(reactions, id: \.sticker) { reaction in
                Text("\(reaction.sticker.emoji) " + reaction.userIDs.map { names[$0] ?? "나간 멤버" }.joined(separator: ", "))
                    .font(.caption13)
                    .foregroundStyle(.textSecondary)
            }
        }
    }

    private func loadComments() async {
        loadFailed = false
        do {
            comments = try await repository.comments(submissionID: submissionID)
        } catch is CancellationError {
        } catch {
            loadFailed = true
        }
    }

    private func toggle(_ sticker: Sticker) {
        Task {
            do {
                reactions = try await repository.toggleReaction(submissionID: submissionID, sticker: sticker)
                message = nil
            } catch is CancellationError {
            } catch {
                message = ResultsFormat.socialErrorMessage(error)
            }
        }
    }

    private func delete(_ comment: SubmissionComment) {
        Task {
            do {
                try await repository.deleteComment(commentID: comment.id)
                comments?.removeAll { $0.id == comment.id }
                message = nil
            } catch is CancellationError {
            } catch {
                message = ResultsFormat.socialErrorMessage(error)
            }
        }
    }

    private func send(_ content: String, _ mentions: [Int], _ image: (data: Data, ext: String)?) async -> Bool {
        do {
            let created = try await repository.addComment(
                submissionID: submissionID, content: content, mentionedUserIDs: mentions,
                image: image?.data, imageExtension: image?.ext ?? "jpg"
            )
            comments = (comments ?? []) + [created]
            message = nil
            return true
        } catch {
            message = ResultsFormat.socialErrorMessage(error)
            return false
        }
    }
}

private struct CommentRow: View {
    let comment: SubmissionComment
    let onDelete: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            HAvatar(content: comment.author.avatar, size: 30)
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    Text(comment.author.nickname)
                        .font(.plex(14, .semibold))
                        .foregroundStyle(.ink)
                    Text(comment.createdAt.koreanTime)
                        .font(.caption12)
                        .foregroundStyle(.textSecondary)
                    Spacer(minLength: 0)
                    if comment.isMine {
                        Button("삭제", action: onDelete)
                            .font(.caption12)
                            .foregroundStyle(.textSecondary)
                            .buttonStyle(.plain)
                            .frame(minHeight: 32)
                    }
                }
                if !comment.content.isEmpty {
                    Text(comment.content)
                        .font(.plex(15))
                        .plexLineHeight(1.5, size: 15)
                        .foregroundStyle(.ink)
                        .fixedSize(horizontal: false, vertical: true)
                }
                if let url = comment.imageURL {
                    AsyncImage(url: url) { image in
                        image.resizable().scaledToFit()
                    } placeholder: {
                        Color.surfaceStrong.frame(height: 120)
                    }
                    .frame(maxHeight: 240, alignment: .leading)
                    .clipShape(RoundedRectangle(cornerRadius: HRadius.card, style: .continuous))
                    .accessibilityLabel("\(comment.author.nickname)의 댓글 사진")
                }
            }
        }
    }
}

private struct CommentComposer: View {
    let mentionable: [GroupMember]
    let onSend: (String, [Int], (data: Data, ext: String)?) async -> Bool

    private static let maxLength = 300
    private static let maxImageBytes = 10 * 1024 * 1024

    @State private var text = ""
    @State private var picked: [Int: String] = [:]
    @State private var image: (data: Data, ext: String)?
    @State private var preview: UIImage?
    @State private var photoItem: PhotosPickerItem?
    @State private var sending = false
    @State private var imageError: String?

    private var canSend: Bool {
        !sending && (ResultsFormat.commentSendEnabled(text) || image != nil)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if !mentionable.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        Text("멘션")
                            .font(.caption12)
                            .foregroundStyle(.textSecondary)
                        ForEach(mentionable) { member in
                            let nickname = member.displayed.nickname
                            Button {
                                if !text.contains("@\(nickname)") {
                                    text = (text.isEmpty || text.hasSuffix(" ") ? text : text + " ") + "@\(nickname) "
                                }
                                picked[member.id] = nickname
                            } label: {
                                Text("@\(nickname)")
                                    .font(.caption13)
                                    .foregroundStyle(.ink)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 6)
                                    .background(.surface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            if let preview {
                Image(uiImage: preview)
                    .resizable()
                    .scaledToFill()
                    .frame(width: 96, height: 96)
                    .clipShape(RoundedRectangle(cornerRadius: HRadius.card, style: .continuous))
                    .overlay(alignment: .topTrailing) {
                        Button {
                            image = nil
                            self.preview = nil
                        } label: {
                            HIconView(icon: .close, size: 12, color: .onAccent)
                                .frame(width: 24, height: 24)
                                .background(.ink, in: Circle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("사진 빼기")
                    }
                    .accessibilityLabel("첨부한 사진")
            }
            if let imageError {
                Text(imageError)
                    .font(.caption13)
                    .foregroundStyle(.danger)
            }
            HStack(alignment: .top, spacing: 8) {
                PhotosPicker(selection: $photoItem, matching: .images) {
                    HIconView(icon: .image, size: 20)
                        .frame(width: 44, height: 44)
                        .background(.surface, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("사진 첨부")
                .padding(.top, 5)
                HTextField(placeholder: "댓글을 입력해 주세요", text: $text, maxLength: Self.maxLength)
                Button(action: send) {
                    Text("등록")
                        .font(.plex(14, .semibold))
                        .foregroundStyle(canSend ? Color.onAccent : Color.textDisabled)
                        .padding(.horizontal, 14)
                        .frame(height: 44)
                        .background(canSend ? Color.accent : Color.disabled, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .buttonStyle(.plain)
                .disabled(!canSend)
                .padding(.top, 5)
            }
        }
        .onChange(of: photoItem) { _, item in
            Task { await loadImage(item) }
        }
    }

    private func loadImage(_ item: PhotosPickerItem?) async {
        guard let item else { return }
        photoItem = nil
        guard let data = try? await item.loadTransferable(type: Data.self), let decoded = UIImage(data: data) else {
            imageError = "사진을 읽을 수 없어요"
            return
        }
        // PNG 는 그대로, 그 밖(HEIC 등)은 서버가 받는 JPEG 로 바꿔요.
        let isPNG = item.supportedContentTypes.contains(.png)
        guard let upload = isPNG ? data : decoded.jpegData(compressionQuality: 0.9) else {
            imageError = "사진을 읽을 수 없어요"
            return
        }
        if upload.count > Self.maxImageBytes {
            imageError = "사진이 너무 커요 (10MB 이하)"
            return
        }
        image = (upload, isPNG ? "png" : "jpg")
        preview = decoded
        imageError = nil
    }

    private func send() {
        guard canSend else { return }
        sending = true
        let content = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let mentions = ResultsFormat.mentionedIDs(in: text, picked: picked)
        let attached = image
        Task {
            let ok = await onSend(content, mentions, attached)
            sending = false
            if ok {
                text = ""
                picked = [:]
                image = nil
                preview = nil
                imageError = nil
            }
        }
    }
}

#Preview {
    let store = DataStore.preview()
    let group = store.groups[0]
    let feed = ResultFeed(store: store, groupID: group.id, missionID: group.missions.last!.id)
    NavigationStack {
        SubmissionDetailView(feed: feed, submissionID: feed.result.submissions[0].id)
    }
    .environment(Services.preview)
}
