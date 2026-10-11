import SwiftUI

@main
struct HAWEApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @State private var session = AppSession()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(session)
                .preferredColorScheme(.light)
        }
    }
}
