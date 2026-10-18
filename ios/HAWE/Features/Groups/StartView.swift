import SwiftUI

/// 01 시작 (Figma 1:314) — 참여한 그룹이 하나도 없을 때, 또는 04 홈의 + 로 열었을 때
struct StartView: View {
    @Binding var path: [MainRoute]
    /// 04 홈에서 + 로 열면 왼쪽 위에 뒤로 가기를 둬요.
    var showsBack = false

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 0) {
            if showsBack {
                HNavBar(leading: .back { dismiss() })
                    .padding(.horizontal, HLayout.screenPadding)
            }
            scaffold
        }
        .overlay(alignment: .topTrailing) {
            Button { path.append(.myPage(.home)) } label: {
                HIconView(icon: .person, size: 22)
                    .frame(width: HLayout.minTouch, height: HLayout.minTouch)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("마이페이지")
            .padding(.top, 4)
            .padding(.trailing, 14)
        }
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
    }

    private var scaffold: some View {
        HCTAScaffold {
            VStack(alignment: .leading, spacing: 48) {
                intro
                contentTypes
            }
            // 헤더가 있으면 그 아래에 바로 붙여요.
            .padding(.top, showsBack ? 0 : HLayout.introTopInset)
        } cta: {
            VStack(spacing: 12) {
                HButton("새 그룹 만들기") { path.append(.createGroup) }
                HButton("초대 코드로 참여하기", kind: .secondary) { path.append(.joinGroup(code: nil)) }
            }
        }
    }

    private var intro: some View {
        VStack(alignment: .leading, spacing: 12) {
            HLogoLockup()
            Text("매일 하나의 미션,\n우리만의 기록")
                .font(.plex(30, .bold))
                .plexLineHeight(1.3, size: 30, weight: .bold)
                .foregroundStyle(.ink)
            Text("오늘은 그림일까, 사진일까?\n매일 랜덤으로 정해지는 미션을 최대 6명이 함께해요.")
                .font(.body15)
                .plexLineHeight(1.6, size: 15)
                .foregroundStyle(.textSecondary)
        }
    }

    /// 3칸 · 간격 12 · 카드 surface 모서리 20 · 안쪽 20 · 아이콘 26 → 글자 사이 28
    private var contentTypes: some View {
        HStack(spacing: 12) {
            ForEach([MissionType.draw, .photo, .write]) { type in
                VStack(alignment: .leading, spacing: 28) {
                    HIconView(icon: type.icon, size: 26)
                    Text(type.displayName)
                        .font(.plex(16, .semibold))
                        .foregroundStyle(.ink)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
                .background(.surface, in: RoundedRectangle(cornerRadius: HRadius.card, style: .continuous))
            }
        }
        .accessibilityElement(children: .combine)
    }
}

#Preview("그룹 없음") {
    NavigationStack {
        StartView(path: .constant([]))
    }
}

#Preview("홈에서 + 로 열림") {
    NavigationStack {
        StartView(path: .constant([]), showsBack: true)
    }
}
