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

/// 미션 제출·결과 피드 (`/api/missions/{id}/submissions`)
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
        case .voice(let fileURL, let duration, let waveform):
            let data = try Data(contentsOf: fileURL)
            parts = [
                .file(name: "file", filename: "voice.m4a", mime: "audio/mp4", data: data),
                .text(name: "duration", value: Self.durationParam(duration)),
                .text(name: "waveform", value: Self.waveformParam(waveform)),
            ]
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

    /// 서버는 0 < 길이 ≤ 30초만 받아요.
    static func durationParam(_ value: TimeInterval) -> String {
        let safe = value.isFinite ? value : 0.1
        return String(format: "%.2f", locale: Locale(identifier: "en_US_POSIX"), min(max(safe, 0.1), 30))
    }

    /// 0...1 값, 최대 64개 JSON 배열
    static func waveformParam(_ values: [CGFloat]) -> String {
        let sampled = values.count <= 64 ? values : (0..<64).map { values[$0 * (values.count - 1) / 63] }
        let numbers = sampled.map { value -> String in
            let clamped = value.isFinite ? min(max(Double(value), 0), 1) : 0
            return String(format: "%.3f", locale: Locale(identifier: "en_US_POSIX"), clamped)
        }
        return "[" + numbers.joined(separator: ",") + "]"
    }

    private func model(_ dto: SubmissionDTO, myUserID: Int) -> Submission? {
        guard let type = MissionType(template: dto.template) else { return nil }
        return Submission(
            id: dto.id,
            author: Profile(avatar: .character(ProfileCharacter.fromKey(dto.author.characterKey)), nickname: dto.author.nickname),
            isMine: dto.author.userId == myUserID,
            type: type,
            createdAt: ServerDate.instant(dto.submittedAt) ?? .now,
            mediaURL: dto.mediaUrl.map(api.absoluteURL),
            text: dto.text,
            duration: dto.duration,
            waveform: (dto.waveform ?? []).map { CGFloat($0) },
            isLate: dto.isLate ?? false,
            authorID: dto.author.userId
        )
    }
}
