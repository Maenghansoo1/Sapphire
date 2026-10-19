import Foundation
import Observation

/// 개발자 모드의 유저 전환 — 서버(`/api/dev/login-as`)가 준 토큰으로 바꿔 끼우고 내 정보를 다시 받아요.
/// 실패하면 전 토큰으로 되돌려요. (Android `AppViewModel.switchUser`)
@MainActor
@Observable
final class DevAccountSwitcher {
    private(set) var isBusy = false
    /// 토스트로 띄울 결과
    var message: String?

    @ObservationIgnored private let services: Services
    @ObservationIgnored private let session: AppSession
    @ObservationIgnored private let store: DataStore

    /// 전환하는 동안에는 세션 만료로 로그아웃하지 않아요 (토큰을 바꿔 끼우는 중이라서요).
    nonisolated static let switching = LockedFlag()

    init(services: Services, session: AppSession, store: DataStore) {
        self.services = services
        self.session = session
        self.store = store
    }

    func switchUser(to userID: Int) {
        guard !isBusy, userID != session.account.userID else { return }
        isBusy = true
        Self.switching.set(true)
        let tokens = services.tokens
        let oldAccess = tokens.accessToken
        let oldRefresh = tokens.refreshToken
        let oldAccount = session.account
        Task {
            defer {
                Self.switching.set(false)
                isBusy = false
            }
            do {
                let issued = try await services.dev.loginAs(userID: userID)
                tokens.save(access: issued.accessToken, refresh: issued.refreshToken)
                session.beginRestoring()
                store.reset()
                let account = try await services.auth.restore()
                store.setMyProfile(account.profile)
                session.signIn(account)
                message = "\(account.profile.nickname) 계정으로 전환했어요"
            } catch {
                if let oldAccess, let oldRefresh {
                    tokens.save(access: oldAccess, refresh: oldRefresh)
                } else {
                    tokens.clear()
                }
                if session.state == .restoring {
                    let restored = (try? await services.auth.restore()) ?? oldAccount
                    store.setMyProfile(restored.profile)
                    session.signIn(restored)
                }
                message = "유저 전환 실패: \(error.devDescription)"
            }
        }
    }
}

/// 여러 스레드에서 읽는 참/거짓 하나
final class LockedFlag: @unchecked Sendable {
    private let lock = NSLock()
    private var value = false

    var isOn: Bool { lock.withLock { value } }
    func set(_ on: Bool) { lock.withLock { value = on } }
}

extension Error {
    /// 개발자 도구에 보여 줄 오류 — 서버 코드와 내용
    var devDescription: String {
        if let error = self as? APIError { return "\(error.code) \(error.message)" }
        if self is NetworkError { return "네트워크 오류" }
        return String(describing: self)
    }
}
