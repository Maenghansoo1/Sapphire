import Foundation

/// Config.xcconfig → Info.plist 로 넘어온 값을 읽어요. (실제 값은 git 에 올리지 않아요)
enum AppConfig {
    /// Spring 서버 주소. Config.xcconfig 의 API_BASE_URL 이 없으면
    /// 디버그는 시뮬레이터에서 Mac 의 서버(localhost:8080), 릴리스는 임시 운영 주소를 써요.
    static var apiBaseURL: URL {
        if let url = string("API_BASE_URL").flatMap(URL.init(string:)) { return url }
        #if DEBUG
        return URL(string: "http://localhost:8080")!
        #else
        // TODO: 운영 API 도메인이 정해지면 바꿔요.
        return URL(string: "https://api.hawe.app")!
        #endif
    }

    static var supabaseURL: URL? {
        string("SUPABASE_URL").flatMap(URL.init(string:))
    }

    static var supabaseAnonKey: String? {
        string("SUPABASE_ANON_KEY")
    }

    private static func string(_ key: String) -> String? {
        guard let value = Bundle.main.object(forInfoDictionaryKey: key) as? String,
              !value.isEmpty, !value.hasPrefix("$(") else { return nil }
        return value
    }
}
