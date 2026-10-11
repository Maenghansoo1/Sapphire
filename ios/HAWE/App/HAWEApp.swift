import SwiftUI

@main
struct HAWEApp: App {
    var body: some Scene {
        WindowGroup {
            // 1단계: 컴포넌트 미리보기. 인증·홈 흐름은 2단계부터 연결해요.
            ComponentGalleryView()
                .preferredColorScheme(.light)
        }
    }
}
