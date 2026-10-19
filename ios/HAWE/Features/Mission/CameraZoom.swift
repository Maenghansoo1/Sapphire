import CoreGraphics
import Foundation

/// 사진 미션 카메라의 확대 배율. 기기가 허용하는 범위 안에서 최대 `max` 배까지(그 이상은 화질이 너무 떨어져요).
/// Android `CameraZoom` 과 같아요.
enum CameraZoom {
    static let max: CGFloat = 10

    /// 기기 범위(`deviceMin`~`deviceMax`)와 `max` 안으로 맞춰요.
    static func clamp(_ value: CGFloat, deviceMin: CGFloat, deviceMax: CGFloat) -> CGFloat {
        let upper = Swift.max(Swift.min(deviceMax, max), deviceMin)
        return Swift.min(Swift.max(value, deviceMin), upper)
    }

    /// "1.0×", "2.5×" — 소수 첫째 자리까지
    static func label(_ ratio: CGFloat) -> String {
        String(format: "%.1f×", locale: Locale(identifier: "en_US_POSIX"), Double(ratio))
    }
}
