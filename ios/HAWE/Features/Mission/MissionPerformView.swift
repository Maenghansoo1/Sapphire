import SwiftUI

/// 미션 수행 화면 (06 그림 · 07 사진 · 08 글 · 09 음성) — 05 에서 전체 화면으로 띄워요.
/// 제출 후에는 수정·삭제할 수 없어요 (CLAUDE.md §5-2).
struct MissionPerformView: View {
    let type: MissionType
    let topic: String
    var onSubmit: (SubmissionContent) -> Void = { _ in }

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Group {
            switch type {
            case .draw: DrawMissionView(topic: topic, onSubmit: submit)
            case .photo: PhotoMissionView(topic: topic, onSubmit: submit)
            case .write: WriteMissionView(topic: topic, onSubmit: submit)
            case .voice: VoiceMissionView(topic: topic, onSubmit: submit)
            }
        }
        .background(.bg)
    }

    /// TODO: 서버 연결 시 Storage 업로드(submissions/{group}/{mission}/{user}.png|jpg|m4a) 후 제출 저장
    private func submit(_ content: SubmissionContent) {
        onSubmit(content)
        dismiss()
    }
}

/// 06~09 공통 위쪽 — "오늘의 ○○" 13 SemiBold + 주제 20 Bold (위 4)
struct MissionTitle: View {
    let type: MissionType
    let topic: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("오늘의 \(type.displayName)")
                .font(.plex(13, .semibold))
                .foregroundStyle(.textSecondary)
            Text(topic)
                .font(.plex(20, .bold))
                .foregroundStyle(.ink)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 4)
    }
}

/// 06~09 공통 헤더 — 닫기 + 오른쪽 도구. 쓰던 내용이 있으면 닫기 전에 물어봐요.
struct MissionHeader<Trailing: View>: View {
    var hasDraft: Bool
    @ViewBuilder var trailing: Trailing

    @Environment(\.dismiss) private var dismiss
    @State private var confirmsClose = false

    var body: some View {
        HNavBar(leading: .close {
            if hasDraft { confirmsClose = true } else { dismiss() }
        }) { trailing }
            .alert("그만둘까요?", isPresented: $confirmsClose) {
                Button("계속하기", role: .cancel) {}
                Button("그만두기", role: .destructive) { dismiss() }
            } message: {
                Text("지금까지 한 내용은 저장되지 않아요.")
            }
    }
}

extension MissionHeader where Trailing == EmptyView {
    init(hasDraft: Bool) {
        self.init(hasDraft: hasDraft) { EmptyView() }
    }
}

#Preview {
    MissionPerformView(type: .write, topic: "너에게 해주고 싶은 말 한 줄")
}
