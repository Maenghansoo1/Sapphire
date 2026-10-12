import Foundation
import Security

/// 로그인 토큰 보관함
protocol TokenStore: AnyObject, Sendable {
    var accessToken: String? { get }
    var refreshToken: String? { get }
    func save(access: String, refresh: String)
    func clear()
}

/// 키체인에 저장해요 — 앱을 껐다 켜도 로그인이 유지돼요.
final class KeychainTokenStore: TokenStore, @unchecked Sendable {
    private let service = "com.sapphire.hawe.session"
    private let lock = NSLock()

    var accessToken: String? { lock.withLock { read("access") } }
    var refreshToken: String? { lock.withLock { read("refresh") } }

    func save(access: String, refresh: String) {
        lock.withLock {
            write("access", access)
            write("refresh", refresh)
        }
    }

    func clear() {
        lock.withLock {
            delete("access")
            delete("refresh")
        }
    }

    private func query(_ account: String) -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: service,
         kSecAttrAccount as String: account]
    }

    private func read(_ account: String) -> String? {
        var query = query(account)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func write(_ account: String, _ value: String) {
        delete(account)
        var query = query(account)
        query[kSecValueData as String] = Data(value.utf8)
        query[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(query as CFDictionary, nil)
    }

    private func delete(_ account: String) {
        SecItemDelete(query(account) as CFDictionary)
    }
}

/// 미리보기·테스트용
final class InMemoryTokenStore: TokenStore, @unchecked Sendable {
    private let lock = NSLock()
    private var access: String?
    private var refresh: String?

    var accessToken: String? { lock.withLock { access } }
    var refreshToken: String? { lock.withLock { refresh } }

    func save(access: String, refresh: String) {
        lock.withLock {
            self.access = access
            self.refresh = refresh
        }
    }

    func clear() {
        lock.withLock {
            access = nil
            refresh = nil
        }
    }
}
