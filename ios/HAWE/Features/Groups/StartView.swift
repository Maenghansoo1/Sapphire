import SwiftUI

/// 01 시작 (Figma 1:314) — 참여한 그룹이 하나도 없을 때
struct StartView: View {
    var body: some View {
        HCTAScaffold {
            VStack(alignment: .leading, spacing: 48) {
                intro
                contentTypes
            }
            .padding(.top, HLayout.introTopInset)
        } cta: {
            VStack(spacing: 12) {
                // TODO: 02 그룹 만들기 / 03 초대 코드로 참여는 다음 묶음에서 연결해요.
                HButton("새 그룹 만들기") {}
                HButton("초대 코드로 참여하기", kind: .secondary) {}
            }
        }
        .overlay(alignment: .topTrailing) {
            // TODO: 17 마이페이지 연결
            Button {} label: {
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

    /// 2열 · 간격 12 · 카드 surface 모서리 20 · 안쪽 20 · 아이콘 26 → 글자 사이 28
    private var contentTypes: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 12), count: 2), spacing: 12) {
            ForEach(MissionType.allCases) { type in
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

#Preview {
    NavigationStack {
        StartView()
    }
}
