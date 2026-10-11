import Foundation

/// 앱 밖 웹 문서 링크 (Safari View 로 열어요, CLAUDE.md §4)
enum AppLinks {
    static let termsOfService = URL(string: "https://shorthaired-rhodium-e1a.notion.site/HA-WE-3f68d13df9ff80ff84a3c2e307d52f65")!
    static let privacyPolicy = URL(string: "https://shorthaired-rhodium-e1a.notion.site/HA-WE-3f68d13df9ff80f38ca3c3fd1c92a9be")!

    /// 초대 링크 (Universal Link)
    /// TODO: 실제 도메인을 정하면 바꿔요. 지금 주소는 임시라 눌러도 열리지 않아요.
    static func invite(code: String) -> URL {
        URL(string: "https://hawe.app/invite/\(code)")!
    }
}

/// `.sheet(item:)` 으로 띄우기 위한 감싸개
struct WebLink: Identifiable {
    let url: URL
    var id: URL { url }
}
