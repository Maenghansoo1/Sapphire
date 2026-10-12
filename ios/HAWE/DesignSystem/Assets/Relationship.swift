import SwiftUI

/// 그룹 관계 5종 (CLAUDE.md §3-7, §5-3)
/// rawValue 는 임시예요 — 2단계에서 DB enum 을 정할 때 맞춰요.
enum Relationship: String, CaseIterable, Identifiable, Codable {
    case lover, friend, family, rival, soso

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .lover: "연인"
        case .friend: "친구"
        case .family: "가족"
        case .rival: "웬수"
        case .soso: "그저그런사이"
        }
    }

    /// 02 그룹 만들기 장식 미리보기 문구 — "캐릭터 사이에 ○○ 떠요"
    var decorPhrase: String {
        switch self {
        case .lover: "하트가"
        case .friend: "반짝이가"
        case .family: "작은 집이"
        case .rival: "번개가"
        case .soso: "말줄임표가"
        }
    }

    /// 연인은 2명, 나머지는 최대 6명
    var maxMembers: Int { self == .lover ? 2 : 6 }

    /// 서버 `relationType` (COUPLE · FRIEND · FAMILY · RIVAL · CASUAL)
    var serverValue: String {
        switch self {
        case .lover: "COUPLE"
        case .friend: "FRIEND"
        case .family: "FAMILY"
        case .rival: "RIVAL"
        case .soso: "CASUAL"
        }
    }

    init(server value: String?) {
        switch value {
        case "COUPLE": self = .lover
        case "FAMILY": self = .family
        case "FRIEND": self = .friend
        case "RIVAL": self = .rival
        default: self = .soso
        }
    }

    /// 장식 색 (§3-7)
    var decorColor: Color {
        switch self {
        case .lover: Color(hex: 0xE5484D)
        case .friend: Color(hex: 0xF0B429)
        case .family: Color(hex: 0xD98B63)
        case .rival: Color(hex: 0xE8590C)
        case .soso: Color(hex: 0x8E8E93)
        }
    }
}

/// 멤버 캐릭터 사이에 들어가는 작은 원형(흰 바탕) 장식.
/// Figma `Decor/*` 기준: 원 지름 = 아바타 × 0.59, 안쪽 그림 = 원 × 0.7
struct RelationshipDecoration: View {
    let relationship: Relationship
    var diameter: CGFloat = 20

    var body: some View {
        glyph
            .frame(width: diameter * 0.7, height: diameter * 0.7)
            .frame(width: diameter, height: diameter)
            .background(.bg, in: Circle())
            .accessibilityHidden(true)
    }

    @ViewBuilder
    private var glyph: some View {
        switch relationship {
        case .lover: Image("decor-lover").resizable().scaledToFit()
        case .friend: Image("decor-friend").resizable().scaledToFit()
        case .rival: Image("decor-enemy").resizable().scaledToFit()
        // Figma 에 아직 Decor/가족, Decor/그저그런사이 가 없어서 SF Symbol 로 임시 표시해요.
        case .family:
            Image(systemName: "house.fill")
                .resizable().scaledToFit()
                .foregroundStyle(relationship.decorColor)
        case .soso:
            Image(systemName: "ellipsis")
                .resizable().scaledToFit()
                .fontWeight(.bold)
                .foregroundStyle(relationship.decorColor)
        }
    }
}

#Preview {
    HStack(spacing: 12) {
        ForEach(Relationship.allCases) { relation in
            VStack(spacing: 6) {
                RelationshipDecoration(relationship: relation, diameter: 32)
                Text(relation.displayName).font(.caption12)
            }
        }
    }
    .padding()
    .background(.surface)
}
