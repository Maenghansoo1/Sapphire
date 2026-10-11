import SwiftUI

@main
struct HAWEApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @State private var session = AppSession()
    @State private var store = DataStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(session)
                .environment(store)
                .preferredColorScheme(.light)
        }
    }
}
