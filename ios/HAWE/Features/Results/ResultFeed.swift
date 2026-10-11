import AVFoundation
import Foundation
import Observation

/// 결과 공개(10~13)와 작품 상세(14)가 함께 쓰는 상태 — 좋아요·댓글이 두 화면에 같이 반영돼요.
@MainActor
@Observable
final class ResultFeed {
    var result: MissionResult
    let player = VoicePlayer()

    init(result: MissionResult) {
        self.result = result
    }

    func submission(_ id: Submission.ID) -> Submission? {
        result.submissions.first { $0.id == id }
    }

    /// TODO: 서버 연결 시 likes 저장/삭제
    func toggleLike(_ id: Submission.ID) {
        guard let index = result.submissions.firstIndex(where: { $0.id == id }) else { return }
        result.submissions[index].likedByMe.toggle()
        result.submissions[index].likeCount += result.submissions[index].likedByMe ? 1 : -1
    }

    /// TODO: 서버 연결 시 comments 저장
    func addComment(_ body: String, to id: Submission.ID) {
        guard let index = result.submissions.firstIndex(where: { $0.id == id }) else { return }
        let me = GroupDetail.mock.me?.displayed ?? .mockMine
        result.submissions[index].comments.append(
            SubmissionComment(id: UUID(), author: me, isMine: true, body: body, createdAt: .now)
        )
    }

    /// 내 댓글만 지울 수 있어요. TODO: 서버 연결 시 comments 삭제 (RLS 로 본인 것만)
    func deleteComment(_ commentID: SubmissionComment.ID, from id: Submission.ID) {
        guard let index = result.submissions.firstIndex(where: { $0.id == id }) else { return }
        result.submissions[index].comments.removeAll { $0.id == commentID && $0.isMine }
    }
}

/// 음성 작품 재생 — 한 번에 하나씩, '전체 이어 듣기'면 끝나면 다음 것으로 넘어가요.
/// 서버 연결 전에는 파일이 없어서 길이만큼 재생하는 척해요.
@MainActor
@Observable
final class VoicePlayer: NSObject {
    private(set) var playingID: Submission.ID?
    /// 0...1
    private(set) var progress: Double = 0
    private(set) var elapsed: TimeInterval = 0

    private var queue: [Submission] = []
    private var current: Submission?
    private var player: AVAudioPlayer?
    private var timer: Timer?

    func toggle(_ submission: Submission) {
        if playingID == submission.id {
            stop()
        } else {
            queue = []
            play(submission)
        }
    }

    func playAll(_ submissions: [Submission]) {
        let voices = submissions.filter { $0.type == .voice }
        guard let first = voices.first else { return }
        queue = Array(voices.dropFirst())
        play(first)
    }

    func stop() {
        player?.stop()
        player = nil
        timer?.invalidate()
        timer = nil
        playingID = nil
        current = nil
        progress = 0
        elapsed = 0
    }

    private func play(_ submission: Submission) {
        stop()
        current = submission
        playingID = submission.id
        if let url = submission.mediaURL, let player = try? AVAudioPlayer(contentsOf: url) {
            try? AVAudioSession.sharedInstance().setCategory(.playback)
            player.play()
            self.player = player
        }
        let started = Date.now
        timer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
            MainActor.assumeIsolated { self?.tick(started: started) }
        }
    }

    private func tick(started: Date) {
        guard let current else { return }
        let duration = player?.duration ?? current.duration ?? 1
        elapsed = player?.currentTime ?? Date.now.timeIntervalSince(started)
        progress = min(1, elapsed / max(duration, 0.1))
        let finished = player.map { !$0.isPlaying } ?? (elapsed >= duration)
        if finished || progress >= 1 {
            let next = queue.first
            queue = Array(queue.dropFirst())
            if let next { play(next) } else { stop() }
        }
    }
}
