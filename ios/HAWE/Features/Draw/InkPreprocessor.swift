import Foundation

/// 그림 → 모델 입력(28×28) 전처리. `ml/quickdraw/PREPROCESS_SPEC.md`(버전 1)와 학습 코드(`rasterize.py`)를
/// 그대로 옮겼어요 (Android `InkPreprocessor` 와 같은 계산). 하나라도 다르면 정확도만 조용히 떨어지니,
/// 고칠 때는 골든 테스트(`InkPreprocessorTests`)로 확인해요.
enum InkPreprocessor {
    static let outputSize = 28
    static let boxSize = 20
    static let margin = (outputSize - boxSize) / 2
    static let inkMinAlpha = 128
    static let inkMaxChannel = 235

    /// 잉크 칸 수가 이보다 적으면 "너무 비어 있음" (명세 (2))
    static func minInk(width: Int, height: Int) -> Int {
        max(3, (width * height + 1999) / 2000)
    }

    /// RGBA 픽셀(행 우선, 한 칸 4바이트, 미리 곱하지 않은 값) → 잉크 마스크 (명세 (1))
    static func inkMask(rgba: [UInt8], width: Int, height: Int) -> [Bool] {
        precondition(rgba.count == width * height * 4, "픽셀 수가 크기와 달라요")
        return (0..<(width * height)).map { index in
            let offset = index * 4
            let alpha = Int(rgba[offset + 3])
            let channel = Int(min(rgba[offset], rgba[offset + 1], rgba[offset + 2]))
            return alpha >= inkMinAlpha && channel < inkMaxChannel
        }
    }

    /// 잉크 마스크(행 우선 w×h) → 28×28 실수(행 우선, 0~1). 너무 비었으면 nil (추론하지 않음).
    static func preprocess(mask: [Bool], width: Int, height: Int) -> [Float]? {
        precondition(mask.count == width * height, "마스크 크기가 달라요")
        guard mask.lazy.filter({ $0 }).count >= minInk(width: width, height: height) else { return nil }
        // (3) 잉크 경계 상자
        var x0 = width, x1 = -1, y0 = height, y1 = -1
        for y in 0..<height {
            for x in 0..<width where mask[y * width + x] {
                x0 = min(x0, x); x1 = max(x1, x)
                y0 = min(y0, y); y1 = max(y1, y)
            }
        }
        let bw = x1 - x0 + 1
        let bh = y1 - y0 + 1
        // (4) 정사각형 가운데: square[y][x] = mask[y0 + y − oy][x0 + x − ox]
        let side = max(bw, bh)
        let ox = (side - bw) / 2
        let oy = (side - bh) / 2
        func square(_ y: Int, _ x: Int) -> Double {
            let sy = y - oy, sx = x - ox
            return sy >= 0 && sy < bh && sx >= 0 && sx < bw && mask[(y0 + sy) * width + x0 + sx] ? 1 : 0
        }
        // (5) 20×20 면적 평균: small = W · square · Wᵀ
        let weights = areaWeights(source: side, target: boxSize)
        let columns: [[Double]] = (0..<side).map { y in
            (0..<boxSize).map { j in weights[j].reduce(0) { $0 + $1.weight * square(y, $1.index) } }
        }
        let small: [[Double]] = (0..<boxSize).map { i in
            (0..<boxSize).map { j in weights[i].reduce(0) { $0 + $1.weight * columns[$1.index][j] } }
        }
        // (6) 가장 진한 값을 1로
        let peak = small.map { $0.max() ?? 0 }.max() ?? 0
        // (7) 28×28 가운데 (여백 4)
        var out = [Float](repeating: 0, count: outputSize * outputSize)
        for i in 0..<boxSize {
            for j in 0..<boxSize {
                let value = peak > 0 ? small[i][j] / peak : small[i][j]
                out[(margin + i) * outputSize + margin + j] = Float(value)
            }
        }
        return out
    }

    /// 결과 칸 i 가 원본 구간 [i·src/dst, (i+1)·src/dst) 와 겹치는 원본 칸과 비율 (한 칸의 합 = 1)
    static func areaWeights(source: Int, target: Int) -> [[(index: Int, weight: Double)]] {
        let scale = Double(source) / Double(target)
        return (0..<target).map { i in
            let start = Double(i) * Double(source) / Double(target)
            let end = Double(i + 1) * Double(source) / Double(target)
            var row: [(index: Int, weight: Double)] = []
            var k = Int(start.rounded(.down))
            while k < source && Double(k) < end {
                let overlap = min(end, Double(k + 1)) - max(start, Double(k))
                if overlap > 0 { row.append((k, overlap / scale)) }
                k += 1
            }
            return row
        }
    }
}

/// 잉크 판정에 쓸 RGBA 픽셀. 메모리에서만 쓰고 파일·로그에 남기지 않아요.
struct InkImage {
    let rgba: [UInt8]
    let width: Int
    let height: Int

    /// 명세 (1)~(7). 너무 비었으면 nil.
    var modelInput: [Float]? {
        InkPreprocessor.preprocess(mask: InkPreprocessor.inkMask(rgba: rgba, width: width, height: height), width: width, height: height)
    }
}
