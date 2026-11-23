import PencilKit
import UIKit

// MARK: - 잉크 픽셀

enum InkSource {
    /// 내가 지금 그린 획 → 투명 배경 + 검정 잉크 (색은 무시, 지우개는 지운 채로) — 명세 (1) "내가 방금 그린 그림".
    /// 해상도는 그림판 크기 그대로(배율 1)라 충분히 작고, 비율 기반인 전처리 결과에는 영향이 없어요.
    static func ink(drawing: PKDrawing, bounds: CGRect) -> InkImage? {
        let black = PKDrawing(strokes: drawing.strokes.map { stroke in
            PKStroke(ink: PKInk(stroke.ink.inkType, color: .black), path: stroke.path, transform: stroke.transform, mask: stroke.mask)
        })
        var image: UIImage?
        UITraitCollection(userInterfaceStyle: .light).performAsCurrent {
            image = black.image(from: bounds, scale: 1)
        }
        return image.flatMap { rgba($0, width: Int(bounds.width.rounded()), height: Int(bounds.height.rounded())) }
    }

    /// 미리 곱하지 않은 RGBA 8비트 픽셀로 그려요 (행 우선).
    static func rgba(_ image: UIImage, width: Int, height: Int) -> InkImage? {
        guard width > 0, height > 0, let cg = image.cgImage else { return nil }
        var premultiplied = [UInt8](repeating: 0, count: width * height * 4)
        let drawn = premultiplied.withUnsafeMutableBytes { buffer -> Bool in
            guard let context = CGContext(
                data: buffer.baseAddress, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width * 4,
                space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            ) else { return false }
            context.draw(cg, in: CGRect(x: 0, y: 0, width: width, height: height))
            return true
        }
        guard drawn else { return nil }
        // 반투명 칸은 색을 원래 값으로 되돌려요 (Android getPixels 와 같은 값).
        for offset in stride(from: 0, to: premultiplied.count, by: 4) {
            let alpha = Int(premultiplied[offset + 3])
            guard alpha > 0, alpha < 255 else { continue }
            for channel in 0..<3 {
                premultiplied[offset + channel] = UInt8(min(255, Int(premultiplied[offset + channel]) * 255 / alpha))
            }
        }
        return InkImage(rgba: premultiplied, width: width, height: height)
    }
}
