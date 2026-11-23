import XCTest
@testable import HAWE

/// Android `LiveGuessTest` · `LiveGuessRunnerTest`(문구)를 옮겼어요. 화면 연결은 실기기에서 확인해요.
final class LiveGuessTests: XCTestCase {
    private var categories: [DrawCategory] = []
    /// 평활 없이 마지막 결과 그대로(히스테리시스·연속 판정만 보려고)
    private let noSmoothing = LiveGuessConfig(alpha: 1)

    override func setUpWithError() throws {
        let url = try XCTUnwrap(Bundle.main.url(forResource: "quickdraw-categories", withExtension: "json"))
        categories = try DrawCategory.parse(Data(contentsOf: url))
    }

    private func category(_ ko: String) -> DrawCategory { categories.first { $0.ko == ko }! }

    private func probs(_ pairs: (String, Float)...) -> [Float] {
        var values = [Float](repeating: 0, count: categories.count)
        for (ko, p) in pairs { values[category(ko).id] = p }
        return values
    }

    private func guesses(_ pairs: (String, Float)...) -> [AiGuess] {
        pairs.map { AiGuess(category: category($0.0), probability: $0.1) }
    }

    // MARK: - 추적

    func testEmaMovesTowardRepeatedResult() {
        let tracker = LiveGuessTracker(subjects: [])
        let first = tracker.onInference(probs(("고양이", 0.2)), categories: categories, at: 0)
        XCTAssertEqual(first.status, .unsure)
        XCTAssertEqual(first.guesses[0].probability, 0.2, accuracy: 0.001)
        for (index, expected) in [Float(0.6), 0.8, 0.9].enumerated() {
            let state = tracker.onInference(probs(("고양이", 1)), categories: categories, at: Int64(index + 1))
            XCTAssertEqual(state.guesses[0].category, category("고양이"))
            XCTAssertEqual(state.guesses[0].probability, expected, accuracy: 0.002)
            XCTAssertEqual(state.status, .guessed)
        }
    }

    func testLeaderChangesOnlyWhenNewTopIsAheadByMargin() {
        let tracker = LiveGuessTracker(config: noSmoothing, subjects: [])
        XCTAssertEqual(tracker.onInference(probs(("고양이", 0.5), ("강아지", 0.4)), categories: categories, at: 0).guesses[0].category,
                       category("고양이"))
        let held = tracker.onInference(probs(("고양이", 0.42), ("강아지", 0.48)), categories: categories, at: 1)
        XCTAssertEqual(held.guesses.prefix(2).map(\.category.ko), ["고양이", "강아지"])
        XCTAssertEqual(tracker.onInference(probs(("고양이", 0.3), ("강아지", 0.6)), categories: categories, at: 2).guesses[0].category,
                       category("강아지"))
    }

    func testCorrectNeedsConsecutiveInferencesAndKeepsFirstTime() {
        let tracker = LiveGuessTracker(config: noSmoothing, subjects: [category("고양이")])
        tracker.onStrokeStart(at: 0)
        let once = tracker.onInference(probs(("고양이", 0.7)), categories: categories, at: 1000)
        XCTAssertEqual(once.status, .guessed)
        XCTAssertFalse(once.guessed)
        let twice = tracker.onInference(probs(("고양이", 0.7)), categories: categories, at: 2000)
        XCTAssertEqual(twice.status, .correct)
        XCTAssertEqual(twice.elapsedMillis, 2000)
        // 더 그려도 처음 맞힌 시각은 그대로, 지워도 맞힌 기록은 남아요
        tracker.onInference(probs(("강아지", 0.9)), categories: categories, at: 5000)
        XCTAssertEqual(tracker.state.guessedAtMillis, 2000)
        XCTAssertTrue(tracker.onEmpty().guessed)
        XCTAssertFalse(tracker.reset().guessed)
    }

    func testStreakResetsWhenProbabilityDrops() {
        let tracker = LiveGuessTracker(config: noSmoothing, subjects: [category("고양이")])
        tracker.onInference(probs(("고양이", 0.7)), categories: categories, at: 0)
        tracker.onInference(probs(("고양이", 0.5)), categories: categories, at: 1)
        XCTAssertNotEqual(tracker.onInference(probs(("고양이", 0.7)), categories: categories, at: 2).status, .correct)
    }

    func testWithoutSubjectsOnlyGuesses() {
        let tracker = LiveGuessTracker(config: noSmoothing, subjects: [])
        tracker.onInference(probs(("고양이", 0.9)), categories: categories, at: 0)
        XCTAssertEqual(tracker.onInference(probs(("고양이", 0.9)), categories: categories, at: 1).status, .guessed)
    }

    func testBadInputIsIgnoredWithoutCrashing() {
        let tracker = LiveGuessTracker(subjects: [])
        let before = tracker.state
        XCTAssertEqual(tracker.onInference([0.5, 0.5], categories: categories, at: 0), before)
        var nan = probs(("고양이", 0.5))
        nan[0] = .nan
        XCTAssertEqual(tracker.onInference(nan, categories: categories, at: 0), before)
    }

    func testPendingShowsThinkingOnlyBeforeFirstGuess() {
        let tracker = LiveGuessTracker(subjects: [])
        XCTAssertEqual(tracker.onInkPending().status, .thinking)
        tracker.onInference(probs(("고양이", 0.9)), categories: categories, at: 0)
        XCTAssertEqual(tracker.onInkPending().status, .guessed)
    }

    // MARK: - 추론 간격

    func testGateLimitsCallsWhileDrawing() {
        var gate = InferenceGate(minIntervalMillis: 500)
        XCTAssertTrue(gate.request(at: 0, strokeEnded: false))
        XCTAssertFalse(gate.finish(at: 10))
        XCTAssertFalse(gate.request(at: 400, strokeEnded: false))
        XCTAssertTrue(gate.request(at: 500, strokeEnded: false))
        XCTAssertFalse(gate.finish(at: 510))
        XCTAssertTrue(gate.request(at: 520, strokeEnded: true))
    }

    func testGateRunsOnceMoreAfterRequestsDuringInference() {
        var gate = InferenceGate(minIntervalMillis: 500)
        XCTAssertTrue(gate.request(at: 0, strokeEnded: true))
        for index in 0..<5 { XCTAssertFalse(gate.request(at: Int64(10 + index), strokeEnded: index == 2)) }
        XCTAssertTrue(gate.pending)
        XCTAssertTrue(gate.finish(at: 50))
        XCTAssertFalse(gate.finish(at: 60))
        XCTAssertFalse(gate.running)
    }

    // MARK: - 말풍선 문구

    func testCopyUsesByeolWithoutEmoji() {
        let empty = liveGuessCopy(LiveGuessState())
        XCTAssertEqual(empty.title, "그리면 별이가 맞혀 볼게요")
        XCTAssertEqual(empty.detail, "그리는 동안 계속 추측해요")
        XCTAssertEqual(liveGuessCopy(LiveGuessState(status: .thinking)).title, "별이가 지켜보는 중…")

        let guessed = LiveGuessState(status: .guessed, guesses: guesses(("강아지", 0.7), ("고양이", 0.2), ("토끼", 0.1)))
        XCTAssertEqual(liveGuessCopy(guessed).title, "별이는 ‘강아지’라고 생각해요")
        XCTAssertEqual(liveGuessCopy(guessed).candidates.map(\.category.ko), ["강아지", "고양이"])
        XCTAssertEqual(liveGuessCopy(LiveGuessState(status: .guessed, guesses: guesses(("달", 0.8)))).title, "별이는 ‘달’이라고 생각해요")

        let correct = LiveGuessState(status: .correct, guesses: guesses(("고양이", 0.8)), guessed: true,
                                     guessedAtMillis: 5000, elapsedMillis: 3400)
        let copy = liveGuessCopy(correct)
        XCTAssertEqual(copy.title, "별이가 맞혔어요!")
        XCTAssertEqual(copy.detail, "‘고양이’ · 3초 만에")
        XCTAssertTrue(copy.celebrate)
        XCTAssertNil(copy.badge)
    }

    func testBadgeKeepsEarlierHitAfterDrawingChanges() {
        let changed = LiveGuessState(status: .guessed, guesses: guesses(("강아지", 0.7)), guessed: true,
                                     guessedAtMillis: 5000, elapsedMillis: 2600)
        XCTAssertEqual(liveGuessCopy(changed).badge, "3초 만에 맞혔어요")
        XCTAssertFalse(liveGuessCopy(changed).celebrate)
        var noTime = changed
        noTime.elapsedMillis = nil
        XCTAssertEqual(liveGuessCopy(noTime).badge, "맞혔어요")
    }

    func testElapsedRoundsToWholeSecondsAtLeastOne() {
        XCTAssertEqual(elapsedSeconds(0), 1)
        XCTAssertEqual(elapsedSeconds(1499), 1)
        XCTAssertEqual(elapsedSeconds(1500), 2)
        XCTAssertEqual(elapsedSeconds(12_345), 12)
        XCTAssertEqual(elapsedSeconds(-50), 1)
    }
}
