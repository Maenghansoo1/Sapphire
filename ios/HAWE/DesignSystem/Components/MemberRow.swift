import SwiftUI

/// 아바타 사이사이에 관계 장식이 들어가는 줄 (CLAUDE.md §3-7, Figma `Members`)
/// Figma 홈 카드 기준: 아바타 34(흰 테두리 2) · 장식 20 · 서로 5pt 겹침
struct MemberRow: View {
    let members: [AvatarContent]
    let relationship: Relationship
    var avatarSize: CGFloat = 34

    private var decorSize: CGFloat { (avatarSize * 0.59).rounded() }

    var body: some View {
        HStack(spacing: -5) {
            ForEach(Array(members.enumerated()), id: \.offset) { index, member in
                if index > 0 {
                    RelationshipDecoration(relationship: relationship, diameter: decorSize)
                        .zIndex(1)
                }
                HAvatar(content: member, size: avatarSize, ringed: true)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(relationship.displayName) \(members.count)명")
    }
}

#Preview {
    let five: [AvatarContent] = [.character(.gamja), .character(.kkotnim), .character(.mulbangul), .character(.pilleum), .character(.gomdol)]
    VStack(alignment: .leading, spacing: 16) {
        MemberRow(members: five, relationship: .friend)
        MemberRow(members: [.character(.mongsil), .character(.ipsagwi)], relationship: .lover)
        MemberRow(members: Array(five.prefix(4)), relationship: .rival)
        MemberRow(members: Array(five.prefix(3)), relationship: .family)
        MemberRow(members: Array(five.prefix(3)), relationship: .soso, avatarSize: 38)
    }
    .padding(20)
}
