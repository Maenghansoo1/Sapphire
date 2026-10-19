import SwiftUI

/// 미션 수행 화면 (06 그림 · 07 사진 · 08 글 · 새 틀 6종) — 05 에서 전체 화면으로 띄워요.
/// 올리는 동안 버튼을 잠그고, 실패하면 입력을 그대로 두고 안내해서 다시 올릴 수 있어요.
/// 제출 후에는 수정·삭제할 수 없어요 (CLAUDE.md §5-2).
struct MissionPerformView: View {
    /// 올리기 결과
    enum Reply {
        /// 끝났어요 — 화면을 닫아요.
        case done
        /// 입력을 그대로 두고 안내만 해요.
        case stay(String)
    }

    let type: MissionType
    let topic: String
    var template = ""
    var spec = MissionSpec()
    var onSubmit: (SubmissionContent) async -> Reply = { _ in .done }

    @Environment(\.dismiss) private var dismiss
    @State private var toast: String?

    var body: some View {
        Group {
            switch type {
            case .draw: DrawMissionView(topic: topic, template: template, spec: spec, onSubmit: submit)
            case .photo: PhotoMissionView(topic: topic, onSubmit: submit)
            case .write:
                switch template {
                case MissionTemplates.textOneWord: OneWordMissionView(topic: topic, onSubmit: submit)
                case MissionTemplates.textReview: ReviewMissionView(topic: topic, onSubmit: submit)
                case MissionTemplates.textBalance: BalanceMissionView(topic: topic, spec: spec, onSubmit: submit)
                default: WriteMissionView(topic: topic, template: template, spec: spec, onSubmit: submit)
                }
            }
        }
        .background(.bg)
        .hToast($toast)
    }

    /// true 면 올리기 성공(화면이 닫혀요), false 면 다시 올릴 수 있게 버튼을 풀어요.
    private func submit(_ content: SubmissionContent) async -> Bool {
        switch await onSubmit(content) {
        case .done:
            dismiss()
            return true
        case .stay(let message):
            toast = message
            return false
        }
    }
}

/// 수행 화면 공통 위쪽 — "오늘의 ○○" 13 SemiBold + 주제 20 Bold (위 4)
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

/// 수행 화면 공통 헤더 — 닫기 + 오른쪽 도구. 쓰던 내용이 있으면 닫기 전에 물어봐요.
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
