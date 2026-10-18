import XCTest
@testable import HAWE

/// Android `InkPreprocessorTest` · `AiGuessTest` · `QuickDrawClassifierTest`(기기 테스트)를 옮겼어요.
final class InkPreprocessorTests: XCTestCase {
    private let goldenNames = ["01_empty", "02_too_few", "03_minimum_ink", "04_horizontal_line", "05_vertical_line",
                               "06_full", "07_diagonal", "08_circle_off_center", "09_rect_outline", "10_cat_drawing"]

    private func json(_ name: String) throws -> [String: Any] {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: name, withExtension: "json"), "\(name) 없음")
        return try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
    }

    private func golden(_ name: String) throws -> (mask: [Bool], width: Int, height: Int, expected: [Float]?) {
        let object = try json(name)
        let width = try XCTUnwrap(object["width"] as? Int)
        let height = try XCTUnwrap(object["height"] as? Int)
        let rows = try XCTUnwrap(object["mask"] as? [String]).map(Array.init)
        let mask = (0..<(width * height)).map { rows[$0 / width][$0 % width] == "1" }
        let expected = (object["expected"] as? [[NSNumber]])?.flatMap { $0.map(\.floatValue) }
        return (mask, width, height, expected)
    }

    /// M1 골든 예시: 마스크를 전처리하면 기대값과 모든 칸이 1e-3 안 (명세 "검증")
    func testMatchesGoldenExamplesFromTraining() throws {
        for name in goldenNames {
            let golden = try golden(name)
            let actual = InkPreprocessor.preprocess(mask: golden.mask, width: golden.width, height: golden.height)
            guard let expected = golden.expected else {
                XCTAssertNil(actual, "\(name) 은 너무 비어서 멈춰야 해요")
                continue
            }
            let values = try XCTUnwrap(actual, name)
            XCTAssertEqual(expected.count, 28 * 28)
            let worst = zip(expected, values).map { abs($0 - $1) }.max() ?? 0
            XCTAssertLessThanOrEqual(worst, 1e-3, "\(name) 최대 오차 \(worst)")
            XCTAssertTrue(values.allSatisfy { (0...1).contains($0) }, name)
            XCTAssertEqual(values.max() ?? 0, 1, accuracy: 1e-6, "\(name) 가장 진한 값은 1")
        }
    }

    func testInkRuleFollowsSpec() {
        let pixels: [[UInt8]] = [
            [255, 255, 255, 255],     // 흰 배경: 아님
            [0, 0, 0, 0],             // 투명 검정: 아님
            [17, 17, 17, 255],        // 검정 잉크
            [0xF2, 0xD7, 0x4E, 255],  // 팔레트 연노랑(min 78): 잉크
            [240, 240, 240, 255],     // 거의 흰색: 아님
            [0, 0, 0, 127],           // 반투명(127): 아님
            [0, 0, 0, 128],           // 알파 128: 잉크
            [234, 250, 250, 255],     // min 234 < 235: 잉크
            [235, 250, 250, 255],     // min 235: 아님
        ]
        XCTAssertEqual(InkPreprocessor.inkMask(rgba: pixels.flatMap { $0 }, width: pixels.count, height: 1),
                       [false, false, true, true, false, false, true, true, false])
        // 앱 팔레트 7색은 모두 잉크 (흰 배경 PNG 에서 연한 색이 사라지지 않음)
        let palette: [UInt32] = [0x111111, 0x8E8E93, 0xE5484D, 0xF5A524, 0xF2D74E, 0x30A46C, 0x2F5BFF]
        let rgba: [UInt8] = palette.flatMap { value -> [UInt8] in
            [UInt8((value >> 16) & 0xFF), UInt8((value >> 8) & 0xFF), UInt8(value & 0xFF), 255]
        }
        XCTAssertTrue(InkPreprocessor.inkMask(rgba: rgba, width: palette.count, height: 1).allSatisfy { $0 })
    }

    func testMinInkAndAreaWeights() {
        XCTAssertEqual(InkPreprocessor.minInk(width: 32, height: 32), 3)
        XCTAssertEqual(InkPreprocessor.minInk(width: 77, height: 77), 3)  // 5929/2000 → 올림 3
        XCTAssertEqual(InkPreprocessor.minInk(width: 320, height: 380), 61)  // 60.8 → 61
        for side in [1, 7, 20, 37, 64, 700] {
            for row in InkPreprocessor.areaWeights(source: side, target: 20) {
                XCTAssertEqual(row.reduce(0) { $0 + $1.weight }, 1, accuracy: 1e-9, "S=\(side) 한 칸의 가중치 합")
            }
        }
    }

    func testPositionAndSizeDoNotMatterButShapeDoes() throws {
        func line(_ width: Int, _ height: Int, x0: Int, y: Int, length: Int) -> [Bool] {
            (0..<(width * height)).map { $0 / width == y && ($0 % width) >= x0 && ($0 % width) < x0 + length }
        }
        let a = try XCTUnwrap(InkPreprocessor.preprocess(mask: line(60, 40, x0: 5, y: 10, length: 30), width: 60, height: 40))
        let b = try XCTUnwrap(InkPreprocessor.preprocess(mask: line(100, 100, x0: 50, y: 90, length: 30), width: 100, height: 100))
        XCTAssertEqual(zip(a, b).map { abs($0 - $1) }.max() ?? 0, 0, accuracy: 1e-6)
        // 가로줄은 세로 가운데에 놓여요 — 종횡비 유지
        let rows = (0..<28).filter { y in (0..<28).contains { a[y * 28 + $0] > 0 } }
        XCTAssertTrue(rows.allSatisfy { (13...14).contains($0) })
    }

    /// iOS 쪽 복사본이 M1 원본과 같아야 해요 (명세·모델을 다시 만들면 같이 바꾸도록).
    func testCopiesMatchMlOriginals() throws {
        let ml = URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("../../ml/quickdraw").standardized
        guard FileManager.default.fileExists(atPath: ml.path) else { throw XCTSkip("저장소 밖에서 돌리고 있어요") }
        for name in goldenNames {
            let original = try Data(contentsOf: ml.appendingPathComponent("golden/\(name).json"))
            let copy = try Data(contentsOf: XCTUnwrap(Bundle(for: Self.self).url(forResource: name, withExtension: "json")))
            XCTAssertEqual(original, copy, "golden/\(name).json")
        }
        let outputs = try Data(contentsOf: XCTUnwrap(Bundle(for: Self.self).url(forResource: "golden_outputs", withExtension: "json")))
        XCTAssertEqual(try Data(contentsOf: ml.appendingPathComponent("export/golden_outputs.json")), outputs)
        let categories = try Data(contentsOf: XCTUnwrap(Bundle.main.url(forResource: "quickdraw-categories", withExtension: "json")))
        XCTAssertEqual(try Data(contentsOf: ml.appendingPathComponent("categories.json")), categories)
        // 범주 순서 = 모델 출력 순서 = labels_ko.txt 줄 순서
        let labels = try String(contentsOf: ml.appendingPathComponent("export/labels_ko.txt"), encoding: .utf8)
            .split(whereSeparator: \.isNewline).map(String.init).filter { !$0.isEmpty }
        XCTAssertEqual(try DrawCategory.parse(categories).map(\.ko), labels)
        // "잘 모르겠어요" 기준도 모델 설명서와 같아야 해요
        let export = try json("export")
        XCTAssertEqual(try XCTUnwrap(export["unsure_threshold"] as? Double), Double(aiGuessThreshold), accuracy: 1e-6)
    }

    /// Core ML 모델(int8 TFLite 를 옮긴 것)이 골든 확률과 0.02 안, 1등과 2등 차이가 0.05 이상이면 1등도 같아요.
    func testCoreMLModelMatchesGoldenOutputs() throws {
        let classifier = try QuickDrawClassifier.load(bundle: .main)
        XCTAssertEqual(classifier.categories.count, 50)
        let outputs = try json("golden_outputs")
        let items = try XCTUnwrap(outputs["items"] as? [[String: Any]])
        XCTAssertFalse(items.isEmpty)
        for item in items {
            let name = try XCTUnwrap(item["name"] as? String)
            let expected = try XCTUnwrap(golden(name).expected)
            let probabilities = try classifier.classify(expected)
            let want = try XCTUnwrap(item["probabilities"] as? [NSNumber]).map(\.floatValue)
            let worst = zip(probabilities, want).map { abs($0 - $1) }.max() ?? 1
            XCTAssertLessThanOrEqual(worst, 0.02, "\(name) 최대 차이 \(worst)")
            let margin = try XCTUnwrap(item["margin"] as? Double)
            let top = try XCTUnwrap((item["top3"] as? [[String: Any]])?.first?["index"] as? Int)
            if margin >= 0.05 {
                XCTAssertEqual(probabilities.indices.max { probabilities[$0] < probabilities[$1] }, top, name)
            }
        }
    }
}

final class AiGuessTests: XCTestCase {
    private var categories: [DrawCategory] = []

    override func setUpWithError() throws {
        let url = try XCTUnwrap(Bundle.main.url(forResource: "quickdraw-categories", withExtension: "json"))
        categories = try DrawCategory.parse(Data(contentsOf: url))
    }

    private func category(_ ko: String) -> DrawCategory { categories.first { $0.ko == ko }! }
    private func guesses(_ pairs: (String, Float)...) -> [AiGuess] { pairs.map { AiGuess(category: category($0.0), probability: $0.1) } }

    func testCategoriesLoadInModelOrder() {
        XCTAssertEqual(categories.count, 50)
        XCTAssertEqual(categories[0].ko, "해")
        XCTAssertEqual(categories[0].names, ["해", "태양"])
        XCTAssertThrowsError(try DrawCategory.parse(Data(#"[{"id":1,"quickdraw":"moon","ko":"달"}]"#.utf8)))  // 0번이 없음
    }

    func testTopGuessesSortByProbabilityThenID() {
        var probabilities = [Float](repeating: 0, count: 50)
        probabilities[12] = 0.6
        probabilities[13] = 0.2
        probabilities[14] = 0.2
        let top = topGuesses(probabilities, categories: categories)
        XCTAssertEqual(top.map(\.category.ko), ["고양이", "강아지", "토끼"])
        XCTAssertEqual(top[0].probability, 0.6)
    }

    func testVerdicts() {
        let cat = category("고양이")
        XCTAssertEqual(aiVerdict(nil, subject: cat), .tooEmpty)
        let unsure = guesses(("고양이", 0.30), ("강아지", 0.25), ("토끼", 0.1))
        XCTAssertEqual(aiVerdict(unsure, subject: cat), .unsure(unsure))
        let catFirst = guesses(("고양이", 0.8), ("강아지", 0.1), ("토끼", 0.05))
        XCTAssertEqual(aiVerdict(catFirst, subject: cat), .correct(catFirst))
        XCTAssertEqual(aiVerdict(catFirst, subject: nil), .guessed(catFirst))
        let dogFirst = guesses(("강아지", 0.6), ("고양이", 0.3), ("토끼", 0.1))
        XCTAssertEqual(aiVerdict(dogFirst, subject: cat), .close(dogFirst, subject: cat, rank: 2))
        XCTAssertEqual(aiVerdict(dogFirst, subject: category("해")), .missed(dogFirst, subject: category("해")))
        // 주제가 3등이어도 0%에 가까우면 "아깝게"가 아니라 못 맞힘
        let catDrawing = guesses(("고양이", 0.99), ("토끼", 0.004), ("해", 0.001))
        XCTAssertEqual(aiVerdict(catDrawing, subject: category("해")), .missed(catDrawing, subject: category("해")))
        if case .close = aiVerdict(guesses(("강아지", 0.6), ("고양이", 0.15)), subject: cat) {} else { XCTFail("0.15 는 아깝게") }
        // 기준 경계: 기준(0.55)과 같으면 답하고, 조금이라도 낮으면 "잘 모르겠어요"
        if case .correct = aiVerdict(guesses(("고양이", aiGuessThreshold)), subject: cat) {} else { XCTFail("기준과 같으면 답해요") }
        if case .unsure = aiVerdict(guesses(("고양이", 0.54)), subject: cat) {} else { XCTFail("기준보다 낮으면 잘 모르겠어요") }
    }

    func testCopyUsesParticlesAndNeverBlames() {
        let dog = guesses(("강아지", 0.5), ("고양이", 0.3), ("토끼", 0.1))
        let moon = guesses(("달", 0.9), ("해", 0.05), ("별", 0.01))
        XCTAssertEqual(aiVerdictCopy(.correct(moon), mine: true), AiVerdictCopy(title: "AI가 맞혔어요!", detail: "‘달’ 그림이 맞대요"))
        XCTAssertEqual(aiVerdictCopy(.guessed(moon), mine: true).title, "AI는 ‘달’이라고 생각해요")
        XCTAssertEqual(aiVerdictCopy(.guessed(dog), mine: false).title, "AI는 ‘강아지’라고 생각해요")
        XCTAssertEqual(aiVerdictCopy(.close(dog, subject: category("고양이"), rank: 2), mine: true),
                       AiVerdictCopy(title: "아깝게 놓쳤어요", detail: "‘고양이’를 2등으로 골랐어요. AI는 ‘강아지’라고 생각했어요"))
        XCTAssertEqual(aiVerdictCopy(.close(guesses(("해", 0.5), ("달", 0.4)), subject: category("달"), rank: 2), mine: true).detail,
                       "‘달’을 2등으로 골랐어요. AI는 ‘해’라고 생각했어요")
        XCTAssertEqual(aiVerdictCopy(.missed(dog, subject: category("해")), mine: true).detail, "AI는 ‘강아지’라고 생각했어요")
        XCTAssertEqual(aiVerdictCopy(.tooEmpty, mine: true).title, "조금 더 그려 볼까요?")
        XCTAssertEqual(aiVerdictCopy(.tooEmpty, mine: false).title, "AI가 알아보기 어려워요")
        XCTAssertEqual(aiVerdictCopy(.unsure(dog), mine: true).title, "잘 모르겠어요")
        XCTAssertEqual(aiVerdictCopy(.unsure(dog), mine: false).detail, "AI도 헷갈린대요. ‘강아지’일까요?")
        XCTAssertEqual(AiVerdict.missed(dog, subject: category("해")).guesses, dog)
        XCTAssertEqual(AiVerdict.tooEmpty.guesses, [])
    }

    func testPercent() {
        XCTAssertEqual(percentText(0.734), "73%")
        XCTAssertEqual(percentText(1.2), "100%")
        XCTAssertEqual(percentText(-0.1), "0%")
    }

    func testSubjectMatching() {
        func match(_ template: String, _ topic: String, _ spec: MissionSpec = MissionSpec()) -> String? {
            DrawSubjectMatcher.match(template: template, topic: topic, spec: spec, categories: categories)?.ko
        }
        // 빈칸 subject 가 먼저, 없으면 주제 문장
        XCTAssertEqual(match(MissionTemplates.drawSketch, "아무거나", MissionSpec(subject: "고양이")), "고양이")
        XCTAssertEqual(match(MissionTemplates.drawSketch, "30초 안에 고양이를 그려줘"), "고양이")
        // 별칭·띄어쓰기·더 긴 이름 우선
        XCTAssertEqual(match(MissionTemplates.drawSketch, "", MissionSpec(subject: "태양")), "해")
        XCTAssertEqual(match(MissionTemplates.drawSketch, "", MissionSpec(subject: "생일케이크")), "생일 케이크")
        XCTAssertEqual(match(MissionTemplates.drawSketch, "", MissionSpec(subject: "케이크")), "생일 케이크")
        // 한 글자 이름은 낱말이 통째로 같을 때만 (조사는 떼고 봐요)
        XCTAssertEqual(match(MissionTemplates.drawSketch, "오늘 밤 달을 그려줘"), "달")
        XCTAssertNil(match(MissionTemplates.drawSketch, "해적선을 그려줘"))
        XCTAssertNil(match(MissionTemplates.drawSketch, "개구리를 그려줘"))
        // A를 B로는 결과물 b 로 판정
        XCTAssertEqual(match(MissionTemplates.drawAToB, "컵을 화분으로 바꿔 그려줘", MissionSpec(drawFrom: "컵", drawTo: "화분")), "화분")
        // 사람을 그리는 미션은 판정하지 않아요
        XCTAssertNil(match(MissionTemplates.drawSketch, "잎사귀를 30초 안에 그려줘", MissionSpec(hasTarget: true)))
        XCTAssertEqual(match(MissionTemplates.drawAToB, "잎사귀를 고양이로", MissionSpec(drawTo: "고양이", hasTarget: true)), "고양이")
        XCTAssertNil(match(MissionTemplates.drawSketch, "오늘 기분을 날씨로 그려줘"))
        XCTAssertNil(match(MissionTemplates.drawSketch, ""))
    }

    /// 앱 그림 화면처럼 검정 잉크 + 투명 배경 이미지를 픽셀로 바꾸면 잉크가 남아요.
    func testInkSourceReadsRenderedImage() throws {
        let size = CGSize(width: 40, height: 30)
        let image = UIGraphicsImageRenderer(size: size, format: {
            let format = UIGraphicsImageRendererFormat()
            format.scale = 1
            return format
        }()).image { context in
            UIColor.black.setFill()
            context.fill(CGRect(x: 5, y: 10, width: 30, height: 4))
        }
        let ink = try XCTUnwrap(InkSource.rgba(image, width: 40, height: 30))
        let mask = InkPreprocessor.inkMask(rgba: ink.rgba, width: ink.width, height: ink.height)
        XCTAssertEqual(mask.filter { $0 }.count, 120)
        XCTAssertTrue(mask[11 * 40 + 6])
        XCTAssertFalse(mask[0])
    }
}
