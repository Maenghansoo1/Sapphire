import AVFoundation
import Foundation
import Observation

/// 결과 공개(10~13)와 작품 상세(14)가 함께 쓰는 상태 — 피드는 서버에서 받아 DataStore 에 넣어요.
@MainActor
@Observable
final class ResultFeed {
    let store: DataStore
    let groupID: Int
    let missionID: Int
    let player = VoicePlayer()

    init(store: DataStore, groupID: Int, missionID: Int) {
        self.store = store
        self.groupID = groupID
        self.missionID = missionID
    }

    /// 피드를 아직 못 받았으면 nil
    var loadedResult: MissionResult? {
        store.result(groupID: groupID, missionID: missionID)
    }

    var result: MissionResult {
        loadedResult ?? MissionResult(
            id: missionID, groupName: "", type: .draw, topic: "", date: .now, memberCount: 0, submissions: [], pending: []
        )
    }

    func submission(_ id: Submission.ID) -> Submission? {
        result.submissions.first { $0.id == id }
    }
}

/// 음성 작품 재생 — 한 번에 하나씩, '전체 이어 듣기'면 끝나면 다음 것으로 넘어가요.
/// 서버 파일(http) 은 내려받으면서 재생해요. 재생할 수 없으면 다음 것으로 넘어가요.
@MainActor
@Observable
final class VoicePlayer: NSObject {
    private(set) var playingID: Submission.ID?
    /// 0...1
    private(set) var progress: Double = 0
    private(set) var elapsed: TimeInterval = 0

    private var queue: [Submission] = []
    private var current: Submission?
    private var player: AVPlayer?
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
        player?.pause()
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
        guard let url = submission.mediaURL else {
            advance()
            return
        }
        current = submission
        playingID = submission.id
        try? AVAudioSession.sharedInstance().setCategory(.playback)
        try? AVAudioSession.sharedInstance().setActive(true)
        let player = AVPlayer(url: url)
        player.play()
        self.player = player
        timer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
            MainActor.assumeIsolated { self?.tick() }
        }
    }

    private func tick() {
        guard let current, let player, let item = player.currentItem else { return }
        if item.status == .failed {
            advance()
            return
        }
        let itemDuration = item.duration.seconds
        let duration = itemDuration.isFinite && itemDuration > 0 ? itemDuration : (current.duration ?? 1)
        elapsed = max(0, player.currentTime().seconds.isFinite ? player.currentTime().seconds : 0)
        progress = min(1, elapsed / max(duration, 0.1))
        let reachedEnd = item.status == .readyToPlay && elapsed >= duration - 0.05
        if reachedEnd || progress >= 1 { advance() }
    }

    private func advance() {
        let next = queue.first
        queue = Array(queue.dropFirst())
        if let next { play(next) } else { stop() }
    }
}
