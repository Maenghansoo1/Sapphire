import AVFoundation
import Foundation
import Observation

/// 결과 공개(10~13)와 작품 상세(14)가 함께 쓰는 상태 — 좋아요·댓글은 DataStore 에 저장돼서 두 화면에 같이 반영돼요.
@MainActor
@Observable
final class ResultFeed {
    let store: DataStore
    let groupID: UUID
    let missionID: UUID
    let player = VoicePlayer()

    init(store: DataStore, groupID: UUID, missionID: UUID) {
        self.store = store
        self.groupID = groupID
        self.missionID = missionID
    }

    /// 내가 올리지 않은 미션이면 비어 있어요 (§5-2)
    var result: MissionResult {
        store.result(groupID: groupID, missionID: missionID) ?? MissionResult(
            id: missionID, groupName: "", type: .draw, topic: "", date: .now, memberCount: 0, submissions: [], pending: []
        )
    }

    func submission(_ id: Submission.ID) -> Submission? {
        result.submissions.first { $0.id == id }
    }

    func toggleLike(_ id: Submission.ID) {
        store.toggleLike(id, missionID: missionID, groupID: groupID)
    }

    func addComment(_ body: String, to id: Submission.ID) {
        store.addComment(body, to: id, missionID: missionID, groupID: groupID)
    }

    /// 내 댓글만 지울 수 있어요.
    func deleteComment(_ commentID: SubmissionComment.ID, from id: Submission.ID) {
        store.deleteComment(commentID, from: id, missionID: missionID, groupID: groupID)
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
