import SwiftUI

/// 기본 프로필 캐릭터 7종 (CLAUDE.md §3-6, DB enum §6)
/// Swift 의 `Character` 타입과 겹치지 않게 ProfileCharacter 로 이름 붙였어요.
enum ProfileCharacter: String, CaseIterable, Identifiable, Codable {
    case mongsil, gomdol, kkotnim, ipsagwi, mulbangul, pilleum, gamja

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .mongsil: "몽실이"
        case .gomdol: "곰돌이"
        case .kkotnim: "꽃님"
        case .ipsagwi: "잎사귀"
        case .mulbangul: "물방울"
        case .pilleum: "필름이"
        case .gamja: "감자"
        }
    }

    /// Figma `character/*` 에서 내보낸 SVG (Assets.xcassets/Characters)
    var image: Image { Image("character-\(rawValue)") }

    static let `default`: ProfileCharacter = .mongsil
}
