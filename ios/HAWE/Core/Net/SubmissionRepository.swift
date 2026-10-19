import Foundation

/// 올리기에 실패했을 때 화면이 할 일
enum SubmitOutcome: Equatable {
    /// 입력을 그대로 두고 안내만 해요 — 다시 올릴 수 있어요.
    case stay(String)
    /// 앞선 요청이 이미 서버에 닿았어요 — 올린 것으로 봐요.
    case alreadySubmitted
    /// 다음 미션이 열려서 닫혔어요.
    case missionClosed
    /// 그룹에서 빠졌거나 미션이 없어요 — 홈으로 가요.
    case returnHome

    init(error: Error) {
        guard let error = error as? APIError else {
            self = .stay(error is NetworkError ? "네트워크 연결을 확인해 주세요" : "잠시 후 다시 시도해 주세요")
            return
        }
        switch error.code {
        case "ALREADY_SUBMITTED": self = .alreadySubmitted
        case "MISSION_CLOSED": self = .missionClosed
        case "INVALID_SUBMISSION": self = .stay(error.message.isEmpty ? "제출 내용을 확인해 주세요" : error.message)
        case "FILE_TOO_LARGE": self = .stay("파일이 너무 커요")
        case "UNSUPPORTED_FILE_TYPE": self = .stay("지원하지 않는 파일이에요")
        case "NOT_GROUP_MEMBER", "MISSION_NOT_FOUND": self = .returnHome
        default: self = .stay("잠시 후 다시 시도해 주세요")
        }
    }
}

/// 미션 제출·결과 피드·스티커·댓글 (`/api/missions/{id}/submissions`, `/api/submissions/{id}/...`)
final class SubmissionRepository: Sendable {
    private let api: APIClient

    init(api: APIClient) {
        self.api = api
    }

    func submit(missionID: Int, content: SubmissionContent, myUserID: Int) async throws -> Submission {
        let parts: [APIClient.Part]
        switch content {
        case .image(let data, let ext):
            let png = ext.lowercased() == "png"
            parts = [.file(name: "file", filename: png ? "drawing.png" : "photo.jpg", mime: png ? "image/png" : "image/jpeg", data: data)]
        case .text(let body):
            parts = [.text(name: "text", value: body)]
        case .scalar(let scalar):
            parts = Self.scalarParts(scalar)
        }
        let dto: SubmissionDTO = try await api.postMultipart("/api/missions/\(missionID)/submissions", parts: parts)
        guard let submission = model(dto, myUserID: myUserID) else {
            throw APIError(status: 201, code: "INVALID_RESPONSE", message: "서버 응답을 읽을 수 없습니다")
        }
        return submission
    }

    /// 그룹은 최대 6명이라 한 페이지로 충분해요.
    func feed(missionID: Int, myUserID: Int) async throws -> [Submission] {
        let page: SubmissionPageDTO = try await api.get("/api/missions/\(missionID)/submissions?page=0&size=100")
        return page.content.compactMap { model($0, myUserID: myUserID) }
    }

    /// 집계 틀의 그룹 결과. 내가 안 냈으면 423 FEED_LOCKED, 집계 틀이 아니면 400 RESULT_NOT_SUPPORTED.
    func result(missionID: Int) async throws -> MissionAggregate? {
        let dto: MissionResultDTO = try await api.get("/api/missions/\(missionID)/result")
        return dto.aggregate
    }

    /// 스티커 토글. 서버가 돌려주는 이 작품의 반응 전체로 화면을 다시 그려요.
    func toggleReaction(submissionID: Int, sticker: Sticker) async throws -> [Reaction] {
        let response: ReactionsDTO = try await api.post(
            "/api/submissions/\(submissionID)/reactions", body: ReactionRequestDTO(stickerType: sticker.rawValue)
        )
        return Self.reactions(response.reactions ?? [])
    }

    func comments(submissionID: Int) async throws -> [SubmissionComment] {
        let page: PageDTO<CommentDTO> = try await api.get("/api/submissions/\(submissionID)/comments?page=0&size=100")
        return page.content.map(comment)
    }

    func addComment(submissionID: Int, content: String, mentionedUserIDs: [Int], image: Data?, imageExtension: String = "jpg") async throws -> SubmissionComment {
        var parts: [APIClient.Part] = [.text(name: "content", value: content)]
        var seen = Set<Int>()
        for id in mentionedUserIDs where seen.insert(id).inserted {
            parts.append(.text(name: "mentionedUserIds", value: String(id)))
        }
        if let image {
            let png = imageExtension.lowercased() == "png"
            parts.append(.file(name: "file", filename: png ? "comment.png" : "comment.jpg", mime: png ? "image/png" : "image/jpeg", data: image))
        }
        let dto: CommentDTO = try await api.postMultipart("/api/submissions/\(submissionID)/comments", parts: parts)
        return comment(dto)
    }

    func deleteComment(commentID: Int) async throws {
        try await api.delete("/api/comments/\(commentID)")
    }

    /// 새 틀의 제출 필드. 값이 없는 필드는 파트를 만들지 않아요(서버는 요구하지 않는 필드를 400 으로 거부).
    static func scalarParts(_ content: ScalarAnswer) -> [APIClient.Part] {
        var parts: [APIClient.Part] = []
        if let text = content.text { parts.append(.text(name: "text", value: text)) }
        if let caption = content.caption { parts.append(.text(name: "caption", value: caption)) }
        if let number = content.number { parts.append(.text(name: "number", value: String(number))) }
        if let guess = content.guess { parts.append(.text(name: "guess", value: String(guess))) }
        if let choice = content.choice { parts.append(.text(name: "choice", value: choice)) }
        return parts
    }

    static func reactions(_ dtos: [ReactionDTO]) -> [Reaction] {
        dtos.compactMap { dto in
            Sticker(rawValue: dto.stickerType).map {
                Reaction(sticker: $0, count: dto.count ?? 0, reactedByMe: dto.reactedByMe ?? false, userIDs: dto.userIds ?? [])
            }
        }
    }

    private func comment(_ dto: CommentDTO) -> SubmissionComment {
        SubmissionComment(
            id: dto.id,
            author: Profile(avatar: api.avatar(characterKey: dto.author.characterKey, profileImageURL: dto.author.profileImageUrl),
                            nickname: dto.author.nickname),
            authorID: dto.author.userId,
            content: dto.content ?? "",
            imageURL: dto.imageUrl.map(api.absoluteURL),
            mentionedUserIDs: dto.mentionedUserIds ?? [],
            createdAt: ServerDate.instant(dto.createdAt) ?? .now,
            isMine: dto.mine ?? false
        )
    }

    private func model(_ dto: SubmissionDTO, myUserID: Int) -> Submission? {
        guard let type = MissionType(template: dto.template) else { return nil }
        return Submission(
            id: dto.id,
            author: Profile(avatar: api.avatar(characterKey: dto.author.characterKey, profileImageURL: dto.author.profileImageUrl),
                            nickname: dto.author.nickname),
            isMine: dto.author.userId == myUserID,
            type: type,
            createdAt: ServerDate.instant(dto.submittedAt) ?? .now,
            mediaURL: dto.mediaUrl.map(api.absoluteURL),
            text: dto.text,
            isLate: dto.isLate ?? false,
            authorID: dto.author.userId,
            reactions: Self.reactions(dto.reactions ?? []),
            commentCount: dto.commentCount ?? 0,
            template: dto.template,
            number: dto.number,
            choice: dto.choice,
            guess: dto.guess,
            caption: dto.caption,
            aiReview: dto.aiReview?.model
        )
    }
}

extension AiReviewDTO {
    /// 배지도 코멘트도 비어 있으면 보여 줄 게 없으니 nil
    var model: AiReview? {
        let badge = badge.flatMap { $0.trimmingCharacters(in: .whitespaces).isEmpty ? nil : $0 }
        let comment = comment?.trimmingCharacters(in: .whitespacesAndNewlines)
        let shown = comment?.isEmpty == false ? comment : nil
        return badge == nil && shown == nil ? nil : AiReview(badge: badge, comment: shown)
    }
}

extension MissionResultDTO {
    /// 서버가 계산한 집계 결과를 앱 모델로. 앱이 모르는 틀이면 nil.
    var aggregate: MissionAggregate? {
        let total = totalCount ?? 0
        switch template {
        case MissionTemplates.textBalance:
            let choices = choices ?? []
            return .balance(.init(
                totalCount: total,
                countA: choices.filter { $0.choice == "A" }.reduce(0) { $0 + ($1.count ?? 0) },
                countB: choices.filter { $0.choice == "B" }.reduce(0) { $0 + ($1.count ?? 0) },
                myChoice: myChoice
            ))
        case MissionTemplates.sliderPosition, MissionTemplates.sliderTaste:
            return .slider(.init(
                totalCount: total, average: average,
                distribution: (distribution ?? []).map { SliderPoint(userID: $0.userId, nickname: $0.nickname ?? "", number: $0.number) },
                myNumber: myNumber, diffFromAverage: diffFromAverage
            ))
        case MissionTemplates.sliderConsensus:
            return .consensus(.init(
                totalCount: total, average: average,
                ranking: (ranking ?? []).map { ConsensusRank(userID: $0.userId, nickname: $0.nickname ?? "", guess: $0.guess, diff: $0.diff) },
                winnerUserIDs: Set(winnerUserIds ?? [])
            ))
        default:
            return nil
        }
    }
}
