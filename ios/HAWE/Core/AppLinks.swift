import Foundation

/// 앱 밖 웹 문서 링크 (Safari View 로 열어요, CLAUDE.md §4)
enum AppLinks {
    static let termsOfService = URL(string: "https://shorthaired-rhodium-e1a.notion.site/HA-WE-3f68d13df9ff80ff84a3c2e307d52f65")!
    static let privacyPolicy = URL(string: "https://shorthaired-rhodium-e1a.notion.site/HA-WE-3f68d13df9ff80f38ca3c3fd1c92a9be")!

}

/// `.sheet(item:)` 으로 띄우기 위한 감싸개
struct WebLink: Identifiable {
    let url: URL
    var id: URL { url }
}
