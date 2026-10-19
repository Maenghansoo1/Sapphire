import CoreML
import Foundation

/// 그림 인식 모델(M1 을 옮긴 `QuickDrawCNN.mlpackage`, 범주 50개)을 기기 안에서 돌려요. 제출 시 추측 후보를 공유해요.
/// 처음 쓸 때 한 번만 불러오고(앱 시작 비용 없음), 불러오지 못하면 기능을 숨겨요(크래시 없음).
/// 모델은 `ios/Tools/quickdraw_coreml.py` 로 int8 TFLite 를 옮긴 것이라 Android 와 같은 확률이 나와요.
final class QuickDrawClassifier: @unchecked Sendable {
    let categories: [DrawCategory]
    private let model: MLModel
    /// MLModel 은 동시에 부르지 않아요.
    private let lock = NSLock()

    private init(model: MLModel, categories: [DrawCategory]) {
        self.model = model
        self.categories = categories
    }

    /// 전처리한 28×28 입력(`InkPreprocessor.preprocess`) → 범주별 확률 (합 1)
    func classify(_ pixels: [Float]) throws -> [Float] {
        precondition(pixels.count == InkPreprocessor.outputSize * InkPreprocessor.outputSize, "입력은 28×28 이어야 해요")
        let size = NSNumber(value: InkPreprocessor.outputSize)
        let input = try MLMultiArray(shape: [1, size, size, 1], dataType: .float32)
        let pointer = input.dataPointer.bindMemory(to: Float.self, capacity: pixels.count)
        for (index, value) in pixels.enumerated() { pointer[index] = value }
        let features = try MLDictionaryFeatureProvider(dictionary: ["image": MLFeatureValue(multiArray: input)])
        let output = try lock.withLock { try model.prediction(from: features) }
        guard let probabilities = output.featureValue(for: "probabilities")?.multiArrayValue,
              probabilities.count == categories.count else {
            throw ModelError()
        }
        return (0..<probabilities.count).map { probabilities[$0].floatValue }
    }

    struct ModelError: Error {}

    // MARK: - 불러오기

    private static let loader = Loader()

    /// 모델을 쓸 수 있으면 그 인스턴스, 파일 누락·손상·범주 수 불일치면 nil (다시 시도하지 않아요).
    static func shared() async -> QuickDrawClassifier? {
        await loader.get()
    }

    private actor Loader {
        private var loaded: QuickDrawClassifier?
        private var unavailable = false

        func get() -> QuickDrawClassifier? {
            if let loaded { return loaded }
            if unavailable { return nil }
            do {
                let classifier = try QuickDrawClassifier.load(bundle: .main)
                loaded = classifier
                return classifier
            } catch {
                unavailable = true
                return nil
            }
        }
    }

    static func load(bundle: Bundle) throws -> QuickDrawClassifier {
        guard let categoriesURL = bundle.url(forResource: "quickdraw-categories", withExtension: "json"),
              let modelURL = bundle.url(forResource: "QuickDrawCNN", withExtension: "mlmodelc") else {
            throw ModelError()
        }
        let categories = try DrawCategory.parse(Data(contentsOf: categoriesURL))
        let configuration = MLModelConfiguration()
        // 층마다 int8 양자화를 흉내 내서 float32 CPU 로 돌려야 골든과 같은 값이 나와요.
        configuration.computeUnits = .cpuOnly
        let model = try MLModel(contentsOf: modelURL, configuration: configuration)
        let output = model.modelDescription.outputDescriptionsByName["probabilities"]?.multiArrayConstraint
        guard model.modelDescription.inputDescriptionsByName["image"] != nil,
              output?.shape.last?.intValue == categories.count else {
            throw ModelError()
        }
        return QuickDrawClassifier(model: model, categories: categories)
    }
}
