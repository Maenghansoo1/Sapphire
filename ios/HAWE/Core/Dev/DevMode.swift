import Foundation
import Observation

/// 개발자 모드 — 앱 정보의 버전을 10번 누르면 켜져요. 켜져 있을 때만 서버 시계 오프셋을 앱 시계에 더해요.
/// 값은 이 기기(UserDefaults)에만 저장해요.
@Observable
final class DevMode: @unchecked Sendable {
    static let shared = DevMode()

    private(set) var enabled: Bool
    /// 서버 시계가 실제 시각보다 앞선 초 (개발자 도구의 시계 조작)
    private(set) var offsetSeconds: Int

    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private let lock = NSLock()
    /// 다른 스레드(APIClient·AppClock)가 읽는 복사본
    @ObservationIgnored private var enabledSnapshot: Bool
    @ObservationIgnored private var offsetSnapshot: Int

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        let enabled = defaults.bool(forKey: "hawe.dev.enabled")
        let offset = enabled ? defaults.integer(forKey: "hawe.dev.clockOffsetSeconds") : 0
        self.enabled = enabled
        self.offsetSeconds = offset
        enabledSnapshot = enabled
        offsetSnapshot = offset
    }

    var isEnabled: Bool { lock.withLock { enabledSnapshot } }
    var currentOffset: Int { lock.withLock { offsetSnapshot } }

    func changeEnabled(_ value: Bool) {
        enabled = value
        if !value { offsetSeconds = 0 }
        lock.withLock {
            enabledSnapshot = value
            offsetSnapshot = offsetSeconds
        }
        defaults.set(value, forKey: "hawe.dev.enabled")
        defaults.set(offsetSeconds, forKey: "hawe.dev.clockOffsetSeconds")
    }

    func setOffset(_ seconds: Int) {
        guard isEnabled else { return }
        offsetSeconds = seconds
        lock.withLock { offsetSnapshot = seconds }
        defaults.set(seconds, forKey: "hawe.dev.clockOffsetSeconds")
    }
}

/// 앱이 쓰는 지금 시각 — 개발자 모드에서 서버 시계를 옮기면 같이 옮겨져요.
enum AppClock {
    static func now() -> Date {
        Date.now.addingTimeInterval(TimeInterval(DevMode.shared.currentOffset))
    }
}

/// 개발자 버전 10번 누르기 (3초 안에 이어서 눌러야 해요)
struct DevUnlock {
    struct TapResult: Equatable {
        var count: Int
        var unlocked: Bool
        var message: String?
    }

    private var lastTap: TimeInterval?
    private var count = 0

    mutating func registerTap(now: TimeInterval, alreadyEnabled: Bool) -> TapResult {
        if alreadyEnabled { return TapResult(count: 0, unlocked: false, message: "이미 개발자 모드예요") }
        if let lastTap, now >= lastTap, now - lastTap <= 3 {
            count += 1
        } else {
            count = 1
        }
        lastTap = now
        if count >= 10 {
            count = 0
            return TapResult(count: 10, unlocked: true, message: "개발자 모드가 켜졌어요")
        }
        return TapResult(count: count, unlocked: false, message: count >= 5 ? "개발자 모드까지 \(10 - count)번" : nil)
    }
}

/// 개발자 도구의 네트워크 오류 주입 — 남은 횟수만큼 서버 대신 가짜 응답을 돌려줘요.
enum DevFault: String, CaseIterable {
    case insightGenerating = "INSIGHT_GENERATING"
    case feedLocked = "FEED_LOCKED"
    case serverError = "SERVER_ERROR"
    case networkDown = "NETWORK_DOWN"
}

enum DevFaults {
    private static let lock = NSLock()
    nonisolated(unsafe) private static var remaining: [DevFault: Int] = [:]

    static func set(_ fault: DevFault, count: Int) {
        lock.withLock { remaining[fault] = max(0, count) }
    }

    static func count(_ fault: DevFault) -> Int {
        lock.withLock { remaining[fault] ?? 0 }
    }

    static func clear() {
        lock.withLock { remaining.removeAll() }
    }

    /// 이 요청에 주입할 오류 (하나 쓰면 횟수가 줄어요). 개발자 API(`/api/dev/`)에는 주입하지 않아요.
    static func take(path: String, method: String, enabled: Bool = DevMode.shared.isEnabled) -> DevFault? {
        guard enabled, !path.hasPrefix("/api/dev/") else { return nil }
        return lock.withLock {
            let fault = DevFault.allCases.first { fault in
                guard (remaining[fault] ?? 0) > 0 else { return false }
                switch fault {
                case .insightGenerating: return method == "GET" && path.range(of: #"^/api/groups/\d+/insights"#, options: .regularExpression) != nil
                case .feedLocked: return method == "GET" && path.range(of: #"^/api/missions/\d+/submissions"#, options: .regularExpression) != nil
                case .serverError, .networkDown: return true
                }
            }
            if let fault { remaining[fault] = (remaining[fault] ?? 1) - 1 }
            return fault
        }
    }

    /// 주입할 가짜 응답 (상태 코드, 본문). 네트워크 끊김이면 nil.
    static func response(for fault: DevFault, path: String, query: String?) -> (Int, Data)? {
        switch fault {
        case .networkDown:
            return nil
        case .insightGenerating:
            let group = path.split(separator: "/").dropFirst(2).first.flatMap { Int($0) } ?? 0
            let month = query?.split(separator: "&").first { $0.hasPrefix("month=") }.map { String($0.dropFirst(6)) } ?? ""
            return (202, Data(#"{"groupId":\#(group),"month":"\#(month)","status":"GENERATING","retryAfterSeconds":2}"#.utf8))
        case .feedLocked:
            return (423, Data(#"{"code":"FEED_LOCKED","message":"개발자 오류 주입"}"#.utf8))
        case .serverError:
            return (500, Data(#"{"code":"INTERNAL_ERROR","message":"개발자 오류 주입"}"#.utf8))
        }
    }
}
