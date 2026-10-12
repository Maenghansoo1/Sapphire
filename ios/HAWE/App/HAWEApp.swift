import SwiftUI

@main
struct HAWEApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @State private var session: AppSession
    @State private var store: DataStore
    @State private var services: Services

    init() {
        let tokens = KeychainTokenStore()
        let session = AppSession(state: tokens.refreshToken != nil ? .restoring : .signedOut)
        let store = DataStore()
        let services = Services(tokens: tokens, onSessionExpired: {
            // 재발급까지 실패하면 다시 로그인하게 해요.
            Task { @MainActor in session.signOut() }
        })
        session.onCleared = {
            services.auth.clearSession()
            MainActor.assumeIsolated { store.reset() }
        }
        _session = State(initialValue: session)
        _store = State(initialValue: store)
        _services = State(initialValue: services)
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(session)
                .environment(store)
                .environment(services)
                .preferredColorScheme(.light)
                .task { await restoreSession() }
        }
    }

    /// 저장된 세션이 있으면 내 정보를 받아 로그인 상태로 들어가요.
    @MainActor
    private func restoreSession() async {
        guard session.state == .restoring else { return }
        do {
            let account = try await services.auth.restore()
            store.setMyProfile(account.profile)
            session.signIn(account)
        } catch is CancellationError {
        } catch {
            session.failRestoring()
        }
    }
}
