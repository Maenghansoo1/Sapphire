import UIKit

// 화면마다 기본 내비게이션 바를 숨기고 HNavBar 를 쓰면 iOS 가 '밀어서 뒤로 가기'를 꺼 버려요.
// 다시 켜 줘요. 첫 화면에서는 동작하지 않게 막아요.
extension UINavigationController: @retroactive UIGestureRecognizerDelegate {
    override open func viewDidLoad() {
        super.viewDidLoad()
        interactivePopGestureRecognizer?.delegate = self
    }

    public func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        viewControllers.count > 1
    }
}
