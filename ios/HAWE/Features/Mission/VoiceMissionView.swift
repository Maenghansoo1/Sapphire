import AVFoundation
import SwiftUI

/// 09 미션 수행 · 음성 (Figma 1:672)
/// 녹음(최대 30초)·정지·다시·미리 듣기, 파형 표시. m4a(AAC)
struct VoiceMissionView: View {
    let topic: String
    var onSubmit: (SubmissionContent) -> Void = { _ in }

    @Environment(\.openURL) private var openURL
    @State private var recorder = VoiceRecorder()

    var body: some View {
        VStack(spacing: 0) {
            MissionHeader(hasDraft: recorder.hasRecording || recorder.isRecording)

            MissionTitle(type: .voice, topic: topic)

            // 위 72 · 간격 28
            VStack(spacing: 28) {
                Text(Self.timeText(recorder.elapsed))
                    .font(.plex(44, .semibold))
                    .monospacedDigit()
                    .foregroundStyle(.ink)
                Waveform(levels: recorder.levels, progress: recorder.waveformProgress)
                Text("최대 \(Int(VoiceRecorder.maxDuration))초")
                    .font(.caption13)
                    .foregroundStyle(.textSecondary)
            }
            .padding(.top, 72)

            Spacer(minLength: 24)

            controls
                .padding(.bottom, 32)

            HButton("올리기") {
                recorder.stopPlayback()
                onSubmit(.voice(
                    fileURL: recorder.fileURL,
                    duration: recorder.elapsed,
                    // 녹음 안 된 막대(-1)는 빼고, 결과 화면 파형으로 써요.
                    waveform: recorder.levels.filter { $0 >= 0 }
                ))
            }
            .disabled(!recorder.hasRecording || recorder.isRecording)
        }
        .padding(.horizontal, HLayout.screenPaddingCompact)
        .padding(.bottom, 32)
        .ignoresSafeArea(.container, edges: .bottom)
        .background(.bg)
        .onDisappear { recorder.reset() }
        .alert("마이크 권한이 필요해요", isPresented: $recorder.showsPermissionAlert) {
            Button("취소", role: .cancel) {}
            Button("설정 열기") {
                if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
            }
        } message: {
            Text("설정 > HA'WE 에서 마이크를 켜 주세요.")
        }
    }

    /// 다시 56 · 녹음 84 · 미리 듣기 56, 간격 36
    private var controls: some View {
        HStack(spacing: 36) {
            Button { recorder.reset() } label: {
                Text("다시")
                    .font(.plex(13, .semibold))
                    .foregroundStyle(.ink)
                    .frame(width: 56, height: 56)
                    .overlay(Circle().strokeBorder(.border, lineWidth: 1))
            }
            .buttonStyle(.plain)
            .disabled(!recorder.hasRecording || recorder.isRecording)
            .opacity(recorder.hasRecording && !recorder.isRecording ? 1 : 0.4)
            .accessibilityLabel("다시 녹음")

            Button {
                Task { await recorder.toggleRecording() }
            } label: {
                ZStack {
                    Circle().fill(.accent)
                    if recorder.isRecording {
                        // 녹음 중: 흰 네모(정지)
                        RoundedRectangle(cornerRadius: 6, style: .continuous)
                            .fill(.onAccent)
                            .frame(width: 26, height: 26)
                    } else {
                        // Figma 에는 녹음 중 모양만 있어서, 대기 모양은 흰 원으로 정했어요.
                        Circle().fill(.onAccent).frame(width: 28, height: 28)
                    }
                }
                .frame(width: 84, height: 84)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(recorder.isRecording ? "녹음 멈추기" : "녹음 시작")

            Button { recorder.togglePlayback() } label: {
                HIconView(icon: recorder.isPlaying ? .pause : .play, size: 20)
                    .frame(width: 56, height: 56)
                    .overlay(Circle().strokeBorder(.border, lineWidth: 1))
            }
            .buttonStyle(.plain)
            .disabled(!recorder.hasRecording || recorder.isRecording)
            .opacity(recorder.hasRecording && !recorder.isRecording ? 1 : 0.4)
            .accessibilityLabel(recorder.isPlaying ? "일시정지" : "미리 듣기")
        }
    }

    static func timeText(_ seconds: TimeInterval) -> String {
        let total = Int(seconds.rounded(.down))
        return String(format: "%02d:%02d", total / 60, total % 60)
    }
}

/// 막대 4 · 간격 4 · 높이 80. 녹음된 구간은 ink, 남은 구간은 waveformIdle 8pt.
struct Waveform: View {
    /// 0...1 소리 크기, 막대 수만큼
    let levels: [CGFloat]
    /// 미리 듣기 중이면 0...1 진행률 (그 뒤 막대는 흐리게)
    var progress: CGFloat?

    var body: some View {
        HStack(spacing: 4) {
            ForEach(Array(levels.enumerated()), id: \.offset) { index, level in
                let isRecorded = level >= 0
                let isPlayed = progress.map { CGFloat(index) / CGFloat(levels.count) < $0 } ?? true
                RoundedRectangle(cornerRadius: 2)
                    .fill(isRecorded && isPlayed ? Color.ink : isRecorded ? Color.textTertiary : Color.waveformIdle)
                    .frame(width: 4, height: isRecorded ? 8 + 72 * level : 8)
            }
        }
        .frame(height: 80)
        .animation(.linear(duration: 0.1), value: levels)
        .accessibilityHidden(true)
    }
}

/// 녹음·미리 듣기
@MainActor
@Observable
final class VoiceRecorder: NSObject {
    static let maxDuration: TimeInterval = 30
    /// Figma 파형 막대 수
    static let barCount = 32

    private(set) var isRecording = false
    private(set) var isPlaying = false
    private(set) var hasRecording = false
    private(set) var elapsed: TimeInterval = 0
    /// 아직 녹음 안 된 막대는 -1
    private(set) var levels = Array(repeating: CGFloat(-1), count: barCount)
    private(set) var playbackProgress: CGFloat = 0
    var showsPermissionAlert = false

    var waveformProgress: CGFloat? { isPlaying ? playbackProgress : nil }

    let fileURL = FileManager.default.temporaryDirectory.appendingPathComponent("mission-voice.m4a")

    private var recorder: AVAudioRecorder?
    private var player: AVAudioPlayer?
    private var timer: Timer?

    func toggleRecording() async {
        if isRecording {
            stopRecording()
        } else {
            await startRecording()
        }
    }

    private func startRecording() async {
        guard await AVAudioApplication.requestRecordPermission() else {
            showsPermissionAlert = true
            return
        }
        reset()
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker])
            try session.setActive(true)
            let settings: [String: Any] = [
                AVFormatIDKey: kAudioFormatMPEG4AAC,
                AVSampleRateKey: 44_100,
                AVNumberOfChannelsKey: 1,
                AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue,
            ]
            let recorder = try AVAudioRecorder(url: fileURL, settings: settings)
            recorder.isMeteringEnabled = true
            recorder.record(forDuration: Self.maxDuration)
            self.recorder = recorder
            isRecording = true
            startTimer { [weak self] in self?.tickRecording() }
        } catch {
            isRecording = false
        }
    }

    private func stopRecording() {
        recorder?.stop()
        timer?.invalidate()
        isRecording = false
        hasRecording = elapsed > 0
    }

    private func tickRecording() {
        guard let recorder else { return }
        guard recorder.isRecording else {
            // 30초가 되면 저절로 멈춰요.
            stopRecording()
            return
        }
        recorder.updateMeters()
        elapsed = min(recorder.currentTime, Self.maxDuration)
        // -50dB ~ 0dB → 0 ~ 1
        let power = recorder.averagePower(forChannel: 0)
        let level = CGFloat(max(0, min(1, (power + 50) / 50)))
        let index = min(Self.barCount - 1, Int(elapsed / Self.maxDuration * Double(Self.barCount)))
        levels[index] = max(levels[index], level)
    }

    func togglePlayback() {
        if isPlaying {
            stopPlayback()
            return
        }
        guard hasRecording, let player = try? AVAudioPlayer(contentsOf: fileURL) else { return }
        player.delegate = self
        player.play()
        self.player = player
        isPlaying = true
        startTimer { [weak self] in
            guard let self, let player = self.player else { return }
            self.playbackProgress = CGFloat(player.currentTime / max(player.duration, 0.1))
        }
    }

    func stopPlayback() {
        player?.stop()
        player = nil
        timer?.invalidate()
        isPlaying = false
        playbackProgress = 0
    }

    func reset() {
        recorder?.stop()
        recorder = nil
        stopPlayback()
        isRecording = false
        hasRecording = false
        elapsed = 0
        levels = Array(repeating: -1, count: Self.barCount)
        try? FileManager.default.removeItem(at: fileURL)
    }

    private func startTimer(_ tick: @escaping @MainActor () -> Void) {
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { _ in
            MainActor.assumeIsolated { tick() }
        }
    }
}

extension VoiceRecorder: AVAudioPlayerDelegate {
    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor in self.stopPlayback() }
    }
}

#Preview {
    VoiceMissionView(topic: "오늘 하루를 10초로 말해줘")
}
