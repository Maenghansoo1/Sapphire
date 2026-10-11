import SafariServices
import SwiftUI

/// 앱 안에서 웹 페이지를 여는 Safari View
struct SafariView: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        let controller = SFSafariViewController(url: url)
        controller.preferredControlTintColor = UIColor(Color.ink)
        return controller
    }

    func updateUIViewController(_ controller: SFSafariViewController, context: Context) {}
}

extension View {
    /// `link` 에 값이 들어오면 Safari View 를 띄워요.
    func safariSheet(_ link: Binding<WebLink?>) -> some View {
        sheet(item: link) { link in
            SafariView(url: link.url).ignoresSafeArea()
        }
    }
}

#Preview {
    SafariView(url: AppLinks.termsOfService)
}
