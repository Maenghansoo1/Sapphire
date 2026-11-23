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

    /// 예시 미션 카드 3장을 겹쳐 둔 그림이에요. 버튼으로 오해하지 않도록 기울여 겹치고, 누를 수 없어요.
    /// Figma 01 시작 · Mission preview — 카드 220×150 · 모서리 18 · 안쪽 18, 아래 안내 13
    private var contentTypes: some View {
        VStack(spacing: 12) {
            ZStack {
                MissionPreviewCard(type: .write, isFront: false)
                    .rotationEffect(.degrees(7))
                    .offset(x: -55, y: 10)
                MissionPreviewCard(type: .photo, isFront: false)
                    .rotationEffect(.degrees(-7))
                    .offset(x: 53, y: 4)
                MissionPreviewCard(type: .draw, isFront: true, prompt: "30초 안에 서로의\n얼굴을 그려보세요")
                    .rotationEffect(.degrees(1.5))
                    .offset(y: -18)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 210)

            Text("매일 이런 미션 중 하나가 랜덤으로 도착해요")
                .font(.caption13)
                .foregroundStyle(.textSecondary)
                .frame(maxWidth: .infinity)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("그림, 사진, 글 중 하나의 미션이 매일 랜덤으로 도착해요")
    }
}

/// 시작 화면의 예시 미션 카드. 앞 카드는 미션 문장까지, 뒤 카드는 아이콘만 보여요.
private struct MissionPreviewCard: View {
    let type: MissionType
    let isFront: Bool
    var prompt: String? = nil

    var body: some View {
        VStack(alignment: type == .photo ? .trailing : .leading, spacing: 14) {
            if isFront {
                HStack(spacing: 6) {
                    HIconView(icon: type.icon, size: 14, color: .accent)
                    Text("오늘의 미션 · \(type.displayName)")
                        .font(.plex(12, .semibold))
                        .foregroundStyle(.accent)
                }
                .padding(.vertical, 4)
                .padding(.leading, 8)
                .padding(.trailing, 10)
                .background(.accentSubtle, in: Capsule())
                if let prompt {
                    Text(prompt)
                        .font(.plex(17, .bold))
                        .plexLineHeight(1.4, size: 17, weight: .bold)
                        .foregroundStyle(.ink)
                }
            } else {
                HIconView(icon: type.icon, size: 18, color: .accent)
                    .padding(7)
                    .background(.bg, in: Circle())
            }
        }
        .padding(18)
        .frame(width: 220, height: 150, alignment: type == .photo ? .topTrailing : .topLeading)
        .background(isFront ? Color.bg : Color.surface, in: shape)
        .overlay {
            if isFront { shape.strokeBorder(Color.black.opacity(0.06), lineWidth: 1) }
        }
        .shadow(color: isFront ? Color(hex: 0x121A26, opacity: 0.10) : .clear, radius: 14, y: 10)
    }

    private var shape: RoundedRectangle {
        RoundedRectangle(cornerRadius: 18, style: .continuous)
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
