import SwiftUI

@main
struct HAWEApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @State private var session: AppSession
    @State private var store: DataStore
    @State private var services: Services
    @State private var switcher: DevAccountSwitcher

    init() {
        let tokens = KeychainTokenStore()
        let session = AppSession(state: tokens.refreshToken != nil ? .restoring : .signedOut)
        let store = DataStore()
        let services = Services(tokens: tokens, onSessionExpired: {
            // 재발급까지 실패하면 다시 로그인하게 해요. 개발자 모드 유저 전환 중에는 전환이 알아서 되돌려요.
            guard !DevAccountSwitcher.switching.isOn else { return }
            Task { @MainActor in session.signOut() }
        })
        session.onCleared = {
            services.auth.clearSession()
            MainActor.assumeIsolated { store.reset() }
        }
        _session = State(initialValue: session)
        _store = State(initialValue: store)
        _services = State(initialValue: services)
        _switcher = State(initialValue: DevAccountSwitcher(services: services, session: session, store: store))
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(session)
                .environment(store)
                .environment(services)
                .environment(switcher)
                .environment(DevMode.shared)
                .preferredColorScheme(.light)
                .task { await restoreSession() }
                // 개발자 모드를 켜면 서버 시계 오프셋을 받아 앱 시계를 맞춰요.
                .task(id: DevMode.shared.enabled) { await syncDevClock() }
                .hToast($switcher.message)
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

    @MainActor
    private func syncDevClock() async {
        guard DevMode.shared.enabled else { return }
        if let status = try? await services.dev.status() {
            DevMode.shared.setOffset(status.offsetSeconds ?? 0)
        }
    }
}
