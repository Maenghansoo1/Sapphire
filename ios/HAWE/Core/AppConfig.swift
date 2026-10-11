import Foundation

/// Config.xcconfig → Info.plist 로 넘어온 값을 읽어요. (실제 값은 git 에 올리지 않아요)
enum AppConfig {
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
