import SwiftUI

// 본문: IBM Plex Sans KR, 워드마크: Fredoka SemiBold (CLAUDE.md §3-3)
enum PlexWeight {
    case regular, medium, semibold, bold

    var postScriptName: String {
        switch self {
        case .regular: "IBMPlexSansKR-Regular"
        case .medium: "IBMPlexSansKR-Medium"
        case .semibold: "IBMPlexSansKR-SemiBold"
        case .bold: "IBMPlexSansKR-Bold"
        }
    }
}

extension Font {
    /// Figma 텍스트 크기를 그대로 넣어 쓰세요. 예: `.plex(17, .semibold)`
    static func plex(_ size: CGFloat, _ weight: PlexWeight = .regular) -> Font {
        .custom(weight.postScriptName, size: size)
    }

    /// 워드마크 "HA'WE" 전용
    static func wordmark(_ size: CGFloat) -> Font {
        // fonts/Fredoka-SemiBold.ttf 의 PostScript 이름
        .custom("FredokaLight-SemiBold", size: size)
    }

    // 자주 쓰는 크기 (Figma 화면 기준)
    /// 화면 제목 26 Bold
    static let screenTitle = plex(26, .bold)
    /// 내비게이션 바 가운데 제목 17 Bold
    static let navTitle = plex(17, .bold)
    /// 섹션 제목 16 Bold
    static let sectionTitle = plex(16, .bold)
    /// 입력 라벨 14 SemiBold
    static let fieldLabel = plex(14, .semibold)
    /// 본문 15 Regular
    static let body15 = plex(15)
    /// 보조 13 Regular
    static let caption13 = plex(13)
    /// 캡션 12 Regular
    static let caption12 = plex(12)
}
