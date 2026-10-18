import XCTest
@testable import HAWE

/// Android `MissionFormatTest` · `TemplateInputRulesTest` · `ResultsFormatTest` · `InsightFormatTest` · `DevUnlockTest` 를 옮겼어요.
final class MissionFormatTests: XCTestCase {
    private let now = ISO8601DateFormatter().date(from: "2026-10-12T00:00:00Z")!

    private func date(_ text: String) -> Date { ISO8601DateFormatter().date(from: text)! }

    func testMultiMissionSelectionAndSubmittedMessages() {
        let first = TodayMission(id: 10, group: .mock, type: .write, topic: "주제", number: 1, opensAt: now,
                                 closesAt: now.addingTimeInterval(3600), members: [])
        var second = first
        second = TodayMission(id: 11, group: .mock, type: .write, topic: "주제", number: 1, opensAt: now,
                              closesAt: now.addingTimeInterval(3600), members: [])
        XCTAssertEqual(MissionFormat.selectedMission([first, second], selectedID: nil), first)
        XCTAssertEqual(MissionFormat.selectedMission([first, second], selectedID: second.id), second)
        XCTAssertEqual(MissionFormat.selectedMission([first, second], selectedID: 999), first)
        XCTAssertEqual(MissionFormat.submittedToastText(index: 0, total: 1, late: false), "오늘의 미션을 올렸어요")
        XCTAssertEqual(MissionFormat.submittedToastText(index: 0, total: 1, late: true), "지각으로 올렸어요")
        XCTAssertEqual(MissionFormat.submittedToastText(index: 1, total: 2, late: false), "2번째 미션을 올렸어요")
        XCTAssertEqual(MissionFormat.submittedToastText(index: 1, total: 2, late: true), "2번째 미션을 지각으로 올렸어요")
    }

    func testCountdownClampsAndPadsHours() {
        XCTAssertEqual(MissionFormat.countdown(to: now, now: now), "00:00:00")
        XCTAssertEqual(MissionFormat.countdown(to: now.addingTimeInterval(-1), now: now), "00:00:00")
        XCTAssertEqual(MissionFormat.countdown(to: now.addingTimeInterval(59), now: now), "00:00:59")
        XCTAssertEqual(MissionFormat.countdown(to: now.addingTimeInterval(3661), now: now), "01:01:01")
        XCTAssertEqual(MissionFormat.countdown(to: now.addingTimeInterval(86400), now: now), "24:00:00")
        XCTAssertEqual(MissionFormat.countdown(to: now.addingTimeInterval(360000), now: now), "100:00:00")
        XCTAssertEqual(MissionFormat.countdown(to: now.addingTimeInterval(1.9), now: now), "00:00:01")
    }

    func testDateUsesSeoul() {
        XCTAssertEqual(MissionFormat.dateText(date("2026-10-11T14:59:59Z")), "10월 11일")
        XCTAssertEqual(MissionFormat.dateText(date("2026-10-11T15:00:00Z")), "10월 12일")
    }

    func testMemberAndNoticeCopy() {
        let mine = MissionMember(id: 1, profile: .mockMine, isMe: true)
        XCTAssertEqual(MissionFormat.memberDisplayName(mine), "하늘 (나)")
        XCTAssertEqual(MissionFormat.memberDisplayName(MissionMember(id: 1, profile: .mockMine)), "하늘")
        XCTAssertEqual(MissionFormat.lockNoticeText(.draw), "내 미션을 올리면 친구들의 그림이 열려요.")
        XCTAssertEqual(MissionFormat.lockNoticeText(.photo), "내 미션을 올리면 친구들의 사진이 열려요.")
        XCTAssertEqual(MissionFormat.lockNoticeText(.write), "내 미션을 올리면 친구들의 글이 열려요.")
        XCTAssertEqual(MissionFormat.lockNoticeText(.write, template: MissionTemplates.textOneWord), "내 미션을 올리면 친구들의 글이 열려요.")
        XCTAssertEqual(MissionFormat.lockNoticeText(.write, template: MissionTemplates.textBalance), "내 답을 올리면 친구들의 답과 그룹 결과가 열려요.")
        XCTAssertEqual(MissionFormat.lockNoticeText(.slider, template: MissionTemplates.sliderTaste), "내 답을 올리면 친구들의 답과 그룹 결과가 열려요.")
        XCTAssertEqual(MissionFormat.lockNoticeText(.slider), "내 미션을 올리면 친구들의 슬라이더가 열려요.")
        XCTAssertEqual(MissionFormat.pokeSentText(.poke, nickname: "민준"), "민준님을 콕 찔렀어요")
        XCTAssertEqual(MissionFormat.pokeSentText(.fling, nickname: "민준"), "민준님에게 똥을 뿌렸어요")
        XCTAssertEqual(PokeKind.allCases.map(MissionFormat.pokeButtonLabel), ["콕", "똥"])
        XCTAssertEqual(MissionFormat.pokeButtonDescription(.poke, nickname: "민준", sent: false), "민준님 콕 찌르기")
        XCTAssertEqual(MissionFormat.pokeButtonDescription(.fling, nickname: "민준", sent: false), "민준님에게 똥 뿌리기")
        XCTAssertEqual(MissionFormat.pokeButtonDescription(.poke, nickname: "민준", sent: true), "민준님을 콕 찔렀어요")
        XCTAssertEqual(MissionFormat.pokeButtonDescription(.fling, nickname: "민준", sent: true), "민준님에게 똥을 뿌렸어요")
    }

    func testDeadlineShowsNormalLateAndClosedStates() {
        let deadline = date("2026-10-12T14:00:00Z")
        let closes = date("2026-10-13T00:00:00Z")
        XCTAssertEqual(MissionFormat.deadlineText(deadlineAt: deadline, closesAt: closes, now: now), "마감 오후 11:00")
        XCTAssertEqual(MissionFormat.deadlineText(deadlineAt: deadline, closesAt: closes, now: deadline), "마감이 지났어요 · 지금 올리면 지각이에요")
        XCTAssertNil(MissionFormat.deadlineText(deadlineAt: deadline, closesAt: closes, now: closes))
        let nextDay = date("2026-10-13T14:00:00Z")
        XCTAssertEqual(MissionFormat.deadlineText(deadlineAt: nextDay, closesAt: nextDay.addingTimeInterval(3600), now: now), "마감 내일 오후 11:00")
    }

    func testTemplateGuideAndButtonCopy() {
        let balance = MissionSpec(optionA: "짜장면", optionB: "짬뽕")
        let slider = MissionSpec(question: "오늘 컨디션", left: "방전", right: "풀충전")
        XCTAssertEqual(MissionFormat.templateGuideText(MissionTemplates.textOneWord, spec: MissionSpec()), "띄어쓰기 없이 한 단어로 남겨요")
        XCTAssertEqual(MissionFormat.templateGuideText(MissionTemplates.textReview, spec: MissionSpec()), "별점 1~5개와 한 줄 후기를 남겨요")
        XCTAssertEqual(MissionFormat.templateGuideText(MissionTemplates.textBalance, spec: balance), "‘짜장면’ vs ‘짬뽕’ 둘 중 하나를 골라요")
        XCTAssertEqual(MissionFormat.templateGuideText(MissionTemplates.textBalance, spec: MissionSpec(optionA: "짜장면")), "둘 중 하나를 골라요")
        XCTAssertEqual(MissionFormat.templateGuideText(MissionTemplates.sliderPosition, spec: slider), "‘방전’ ↔ ‘풀충전’ 사이에서 내 위치를 골라요")
        XCTAssertEqual(MissionFormat.templateGuideText(MissionTemplates.sliderTaste, spec: slider), "‘방전’ ↔ ‘풀충전’ 사이에서 내 위치를 골라요")
        XCTAssertEqual(MissionFormat.templateGuideText(MissionTemplates.sliderTaste, spec: MissionSpec(left: " ", right: "최고")), "내 위치를 골라요")
        XCTAssertEqual(MissionFormat.templateGuideText(MissionTemplates.sliderConsensus, spec: slider), "내 위치와 우리 그룹 평균을 함께 예측해요")
        XCTAssertNil(MissionFormat.templateGuideText(MissionTemplates.textAcrostic, spec: MissionSpec(word: "퇴근길")))
        XCTAssertNil(MissionFormat.templateGuideText(MissionTemplates.photoNow, spec: MissionSpec()))

        XCTAssertEqual(MissionFormat.actionTitle(.write, template: MissionTemplates.textBalance), "하나 고르기")
        XCTAssertEqual(MissionFormat.actionTitle(.write, template: MissionTemplates.textReview), "글 쓰기")
        XCTAssertEqual(MissionFormat.actionTitle(.slider, template: MissionTemplates.sliderPosition), "슬라이더 정하기")
        XCTAssertEqual(MissionFormat.actionTitle(.photo, template: MissionTemplates.photoNow), "사진 찍기")
        XCTAssertEqual(MissionFormat.resultsActionTitle(template: MissionTemplates.textBalance), "결과 보기")
        XCTAssertEqual(MissionFormat.resultsActionTitle(template: MissionTemplates.sliderConsensus), "결과 보기")
        XCTAssertEqual(MissionFormat.resultsActionTitle(template: MissionTemplates.textOneWord), "친구들 작품 보기")
        XCTAssertEqual(MissionFormat.resultsActionTitle(template: MissionTemplates.drawSketch), "친구들 작품 보기")
        XCTAssertEqual(MissionFormat.unsupportedText, "앱을 업데이트하면 이 미션을 할 수 있어요")
    }
}

final class TemplateInputRulesTests: XCTestCase {
    func testOneWordRemovesSpacesAndLimitsGraphemes() {
        XCTAssertEqual(TemplateInput.oneWord(" 오늘 하루\n"), "오늘하루")
        XCTAssertEqual(TemplateInput.oneWord("탭과\t줄바꿈\r\n"), "탭과줄바꿈")
        XCTAssertEqual(TemplateInput.oneWord("전각　공백"), "전각공백")
        XCTAssertEqual(TemplateInput.oneWord(String(repeating: "가", count: 25)), String(repeating: "가", count: 20))
        XCTAssertEqual(TemplateInput.oneWord(String(repeating: "😀", count: 21)), String(repeating: "😀", count: 20))
    }

    func testOneWordStateAndContent() {
        XCTAssertFalse(OneWordState().canSubmit)
        XCTAssertFalse(OneWordState().hasDraft)
        XCTAssertFalse(OneWordState(reason: "  ").hasDraft)
        let state = OneWordState(word: "몰입", reason: "  하루 종일 코딩  ")
        XCTAssertTrue(state.canSubmit)
        XCTAssertTrue(state.hasDraft)
        XCTAssertEqual(state.wordCounter, "2 / 20")
        XCTAssertEqual(state.reasonCounter, "12 / 100")
        XCTAssertEqual(state.content, ScalarAnswer(text: "몰입", caption: "하루 종일 코딩"))
        XCTAssertEqual(OneWordState(word: "몰입", reason: " \n ").content, ScalarAnswer(text: "몰입"))
        let reasonOnly = OneWordState(reason: "이유만")
        XCTAssertTrue(reasonOnly.hasDraft && !reasonOnly.canSubmit)
    }

    func testReasonLimitsCountCodePointsLikeServer() {
        XCTAssertEqual(TemplateInput.codePointCount("가😀a"), 3)
        XCTAssertEqual(TemplateInput.reason(String(repeating: "가", count: 150), limit: TemplateLimits.oneWordReason), String(repeating: "가", count: 100))
        XCTAssertEqual(TemplateInput.reason(String(repeating: "😀", count: 101), limit: TemplateLimits.oneWordReason), String(repeating: "😀", count: 100))
        XCTAssertEqual(TemplateInput.reason("짧은 이유", limit: TemplateLimits.balanceReason), "짧은 이유")
        XCTAssertEqual(TemplateInput.codePointCount(TemplateInput.reason(String(repeating: "나", count: 300), limit: TemplateLimits.balanceReason)), 200)
        XCTAssertEqual(TemplateInput.reason("줄\n바꿈은 그대로", limit: TemplateLimits.balanceReason), "줄\n바꿈은 그대로")
    }

    func testReviewIsOneLineWithStars() {
        XCTAssertEqual(TemplateInput.review("한 줄\n후기"), "한 줄 후기")
        XCTAssertEqual(TemplateInput.review("윈도\r\n줄바꿈"), "윈도 줄바꿈")
        XCTAssertEqual(TemplateInput.review(String(repeating: "가", count: 120)), String(repeating: "가", count: 100))
        XCTAssertFalse(ReviewState().canSubmit)
        XCTAssertFalse(ReviewState(stars: 4).canSubmit)
        XCTAssertFalse(ReviewState(line: "맛있다").canSubmit)
        XCTAssertFalse(ReviewState(stars: 4, line: "   ").canSubmit)
        XCTAssertFalse(ReviewState(stars: 6, line: "맛있다").canSubmit)
        XCTAssertTrue(ReviewState(stars: 1, line: "맛있다").canSubmit)
        XCTAssertTrue(ReviewState(stars: 1).hasDraft)
        XCTAssertEqual(ReviewState(stars: 5, line: "맛있다").counter, "3 / 100")
        XCTAssertEqual(ReviewState(stars: 5, line: " 또 먹고 싶다 ").content, ScalarAnswer(number: 5, text: "또 먹고 싶다"))
    }

    func testBalanceNeedsChoice() {
        XCTAssertFalse(BalanceState().canSubmit)
        XCTAssertFalse(BalanceState(reason: "이유").canSubmit)
        XCTAssertTrue(BalanceState(reason: "이유").hasDraft)
        XCTAssertFalse(BalanceState(choice: "C").canSubmit)
        XCTAssertTrue(BalanceState(choice: "A").canSubmit)
        XCTAssertEqual(BalanceState(choice: "B", reason: "  ").content, ScalarAnswer(choice: "B"))
        XCTAssertEqual(BalanceState(choice: "A", reason: "면이 좋아\n").content, ScalarAnswer(choice: "A", caption: "면이 좋아"))
        XCTAssertEqual(BalanceState().counter, "0 / 200")
    }

    func testSliderAlwaysHasIntegerValues() {
        XCTAssertEqual(SliderState().number, 50)
        XCTAssertFalse(SliderState().touched)
        XCTAssertEqual(SliderState().content, ScalarAnswer(number: 50))
        XCTAssertEqual(SliderState(number: 80, guess: 60, consensus: true).content, ScalarAnswer(number: 80, guess: 60))
        XCTAssertEqual(SliderState(number: 80, guess: 60, consensus: false).content, ScalarAnswer(number: 80))
        XCTAssertEqual(TemplateInput.sliderValue(-3), 0)
        XCTAssertEqual(TemplateInput.sliderValue(100.4), 100)
        XCTAssertEqual(TemplateInput.sliderValue(62.5), 63)
        XCTAssertEqual(TemplateInput.sliderValue(62.49), 62)
    }

    func testScalarPartsSkipEmptyFields() {
        let parts = SubmissionRepository.scalarParts(ScalarAnswer(number: 80, guess: 60, text: "몰입", caption: "이유"))
        let names = parts.map { part -> String in
            if case .text(let name, let value) = part { return "\(name)=\(value)" }
            return ""
        }
        XCTAssertEqual(names, ["text=몰입", "caption=이유", "number=80", "guess=60"])
        XCTAssertTrue(SubmissionRepository.scalarParts(ScalarAnswer()).isEmpty)
    }
}

final class ResultsFormatTests: XCTestCase {
    private let now = ISO8601DateFormatter().date(from: "2026-10-12T00:00:00Z")!

    private func date(_ text: String) -> Date { ISO8601DateFormatter().date(from: text)! }

    func testSeoulTime() {
        XCTAssertEqual(date("2026-10-12T00:12:00Z").koreanTime, "오전 9:12")
        XCTAssertEqual(date("2026-10-11T15:05:00Z").koreanTime, "오전 12:05")
        XCTAssertEqual(date("2026-10-12T03:00:00Z").koreanTime, "오후 12:00")
        XCTAssertEqual(date("2026-10-12T14:59:00Z").koreanTime, "오후 11:59")
        XCTAssertEqual(date("2026-10-11T15:00:00Z").koreanMonthDay, "10월 12일")
    }

    func testFeedAndCommentCopy() {
        XCTAssertEqual(ResultsFormat.progressText(done: 3, memberCount: 5), "3 / 5명 완료")
        XCTAssertEqual(ResultsFormat.progressText(done: 5, memberCount: 5), "5명 모두 완료")
        XCTAssertEqual(ResultsFormat.progressText(done: 0, memberCount: 4), "0 / 4명 완료")
        XCTAssertEqual(ResultsFormat.hintText(.photo), "사진을 눌러서 자세히 보기")
        XCTAssertEqual(ResultsFormat.hintText(.draw), "그림을 눌러서 자세히 보기")
        XCTAssertEqual(ResultsFormat.hintText(.write), "글을 눌러서 자세히 보기")
        XCTAssertEqual(ResultsFormat.hintText(.slider), "슬라이더를 눌러서 자세히 보기")
        XCTAssertFalse(ResultsFormat.commentSendEnabled(""))
        XCTAssertFalse(ResultsFormat.commentSendEnabled("  "))
        XCTAssertFalse(ResultsFormat.commentSendEnabled("\n"))
        XCTAssertTrue(ResultsFormat.commentSendEnabled(" 안녕 "))
        XCTAssertEqual(ResultsFormat.mentionedIDs(in: "@지우 고마워 @서연", picked: [2: "지우", 3: "서연", 4: "현우"]), [2, 3])
    }

    func testNewTemplateAnswerCopy() {
        XCTAssertEqual(ResultsFormat.starsText(4), "★★★★☆")
        XCTAssertEqual(ResultsFormat.starsText(0), "☆☆☆☆☆")
        XCTAssertEqual(ResultsFormat.starsText(9), "★★★★★")
        XCTAssertEqual(ResultsFormat.starsText(-1), "☆☆☆☆☆")
        let spec = MissionSpec(optionA: "짜장면", optionB: "짬뽕")
        XCTAssertEqual(ResultsFormat.balanceChoiceText("A", spec: spec), "A · 짜장면")
        XCTAssertEqual(ResultsFormat.balanceChoiceText("B", spec: spec), "B · 짬뽕")
        XCTAssertEqual(ResultsFormat.balanceChoiceText("B", spec: MissionSpec()), "B")
        XCTAssertNil(ResultsFormat.balanceChoiceText(nil, spec: spec))
        XCTAssertNil(ResultsFormat.balanceChoiceText("C", spec: spec))
        XCTAssertEqual(Set(MissionTemplates.supported.filter(ResultsFormat.hasTemplateAnswer)),
                       ["TEXT_ONE_WORD", "TEXT_REVIEW", "TEXT_BALANCE", "SLIDER_POSITION", "SLIDER_TASTE", "SLIDER_CONSENSUS"])
    }

    func testBalancePercentsAlwaysAddUpTo100() {
        XCTAssertTrue(ResultsFormat.balancePercents(countA: 3, countB: 1) == (75, 25))
        XCTAssertTrue(ResultsFormat.balancePercents(countA: 1, countB: 2) == (33, 67))
        XCTAssertTrue(ResultsFormat.balancePercents(countA: 2, countB: 1) == (67, 33))
        XCTAssertTrue(ResultsFormat.balancePercents(countA: 1, countB: 0) == (100, 0))
        XCTAssertTrue(ResultsFormat.balancePercents(countA: 0, countB: 6) == (0, 100))
        XCTAssertTrue(ResultsFormat.balancePercents(countA: 0, countB: 0) == (0, 0))
        for a in 0...6 {
            for b in 0...6 where a + b > 0 {
                let result = ResultsFormat.balancePercents(countA: a, countB: b)
                XCTAssertEqual(result.a + result.b, 100)
            }
        }
    }

    func testAverageAndDiffCopyKeepServerRounding() {
        XCTAssertEqual(ResultsFormat.decimalText(62.5), "62.5")
        XCTAssertEqual(ResultsFormat.decimalText(60), "60")
        XCTAssertEqual(ResultsFormat.decimalText(0), "0")
        XCTAssertEqual(ResultsFormat.decimalText(32.8), "32.8")
        let spec = MissionSpec(left: "방전", right: "풀충전")
        XCTAssertEqual(ResultsFormat.diffFromAverageText(7.5, spec: spec), "내 위치는 평균보다 ‘풀충전’ 쪽으로 7.5")
        XCTAssertEqual(ResultsFormat.diffFromAverageText(-32.8, spec: spec), "내 위치는 평균보다 ‘방전’ 쪽으로 32.8")
        XCTAssertEqual(ResultsFormat.diffFromAverageText(0, spec: spec), "내 위치가 딱 평균이에요")
        XCTAssertEqual(ResultsFormat.diffFromAverageText(10, spec: MissionSpec()), "내 위치는 평균보다 오른쪽으로 10")
        XCTAssertEqual(ResultsFormat.diffFromAverageText(-2.5, spec: MissionSpec(left: "")), "내 위치는 평균보다 왼쪽으로 2.5")
        XCTAssertNil(ResultsFormat.diffFromAverageText(nil, spec: spec))
    }

    func testConsensusRanksAndWinners() {
        let ranking = [ConsensusRank(userID: 2, nickname: "잎사귀", guess: 60, diff: 2.5),
                       ConsensusRank(userID: 3, nickname: "서연", guess: 65, diff: 2.5),
                       ConsensusRank(userID: 1, nickname: "몽실이", guess: 80, diff: 17.5),
                       ConsensusRank(userID: 4, nickname: "지우", guess: 40, diff: 22.5)]
        XCTAssertEqual(ResultsFormat.competitionRanks(ranking), [1, 1, 3, 4])
        XCTAssertEqual(ResultsFormat.competitionRanks([]), [])
        XCTAssertEqual(ResultsFormat.consensusWinnerText(ranking, winnerUserIDs: [2, 3], myUserID: 1), "잎사귀님, 서연님이 공동으로 가장 가깝게 맞혔어요")
        XCTAssertEqual(ResultsFormat.consensusWinnerText(ranking, winnerUserIDs: [2, 3], myUserID: 2), "나와 서연님이 공동으로 가장 가깝게 맞혔어요")
        XCTAssertEqual(ResultsFormat.consensusWinnerText(Array(ranking.prefix(1)), winnerUserIDs: [2], myUserID: 1), "잎사귀님이 가장 가깝게 맞혔어요")
        XCTAssertEqual(ResultsFormat.consensusWinnerText(Array(ranking.prefix(1)), winnerUserIDs: [2], myUserID: 2), "내가 가장 가깝게 맞혔어요")
        XCTAssertNil(ResultsFormat.consensusWinnerText(ranking, winnerUserIDs: [], myUserID: 1))
    }

    func testAiReviewCopyAndPendingCheck() {
        XCTAssertEqual(ResultsFormat.aiBadgeLabel("PERFECT_FIT"), "오늘의 주제 찰떡상")
        XCTAssertNil(ResultsFormat.aiBadgeLabel(nil))
        XCTAssertNil(ResultsFormat.aiBadgeLabel("GOLDEN_FIT"))
        let mine = Submission(id: 1, author: .mockMine, isMine: true, type: .photo, createdAt: now.addingTimeInterval(-30))
        XCTAssertTrue(ResultsFormat.awaitsAiReview([mine], now: now))
        var reviewed = mine
        reviewed.aiReview = AiReview(badge: "PERFECT_FIT", comment: nil)
        XCTAssertFalse(ResultsFormat.awaitsAiReview([reviewed], now: now))
        var friend = mine
        friend.isMine = false
        XCTAssertFalse(ResultsFormat.awaitsAiReview([friend], now: now))
        var drawing = mine
        drawing.type = .draw
        XCTAssertFalse(ResultsFormat.awaitsAiReview([drawing], now: now))
        var old = mine
        old.createdAt = now.addingTimeInterval(-180)
        XCTAssertFalse(ResultsFormat.awaitsAiReview([old], now: now))
        var future = mine
        future.createdAt = now.addingTimeInterval(60)
        XCTAssertFalse(ResultsFormat.awaitsAiReview([future], now: now))
        XCTAssertFalse(ResultsFormat.awaitsAiReview([], now: now))
    }

    func testAiReviewDTOHidesEmptyValues() {
        XCTAssertNil(AiReviewDTO(badge: nil, comment: "  ").model)
        XCTAssertNil(AiReviewDTO(badge: " ", comment: nil).model)
        XCTAssertEqual(AiReviewDTO(badge: "PERFECT_FIT", comment: " 멋져요 ").model, AiReview(badge: "PERFECT_FIT", comment: "멋져요"))
    }
}

final class InsightAndDevTests: XCTestCase {
    private func month(_ year: Int, _ month: Int) -> Date {
        DataStore.calendar.date(from: DateComponents(year: year, month: month, day: 1))!
    }

    func testInsightBoundsTwelveMonths() {
        let current = month(2026, 10)
        XCTAssertFalse(InsightFormat.canShowPrevious(month(2025, 10), current: current))
        XCTAssertTrue(InsightFormat.canShowPrevious(month(2025, 11), current: current))
        XCTAssertFalse(InsightFormat.canShowNext(current, current: current))
        XCTAssertTrue(InsightFormat.canShowNext(month(2026, 9), current: current))
        XCTAssertEqual(InsightFormat.monthTitle(current), "2026년 10월")
        XCTAssertEqual(CalendarRepository.monthString(current), "2026-10")
    }

    func testInsightRatesDeltaAndWords() {
        XCTAssertEqual(InsightFormat.percent(0.745), 75)
        XCTAssertEqual(InsightFormat.percent(-1), 0)
        XCTAssertEqual(InsightFormat.percent(2), 100)
        XCTAssertEqual(InsightFormat.delta(5), "+5")
        XCTAssertEqual(InsightFormat.delta(-3), "-3")
        XCTAssertEqual(InsightFormat.delta(0), "변화 없음")
        XCTAssertNil(InsightFormat.delta(nil))
        XCTAssertEqual(InsightFormat.responseTime(30), "보통 1분 안에")
        XCTAssertEqual(InsightFormat.responseTime(61), "보통 2분 안에")
        XCTAssertEqual(InsightFormat.responseTime(3601), "보통 1시간 1분 안에")
        XCTAssertEqual(InsightFormat.wordSize(count: 9, maximum: 9), 18)
        XCTAssertEqual(InsightFormat.wordSize(count: 4, maximum: 9), 15)
        XCTAssertEqual(InsightFormat.wordSize(count: 2, maximum: 9), 13)
    }

    func testTenQuickTapsUnlockAndProgressStartsAtFive() {
        var unlock = DevUnlock()
        for index in 0..<4 { XCTAssertNil(unlock.registerTap(now: Double(index) * 0.1, alreadyEnabled: false).message) }
        XCTAssertEqual(unlock.registerTap(now: 0.4, alreadyEnabled: false).message, "개발자 모드까지 5번")
        for index in 0..<4 { XCTAssertFalse(unlock.registerTap(now: 0.5 + Double(index) * 0.1, alreadyEnabled: false).unlocked) }
        XCTAssertTrue(unlock.registerTap(now: 0.9, alreadyEnabled: false).unlocked)
        XCTAssertEqual(unlock.registerTap(now: 1.0, alreadyEnabled: true).message, "이미 개발자 모드예요")
    }

    func testGapOverThreeSecondsRestartsCount() {
        var unlock = DevUnlock()
        for index in 0..<9 { _ = unlock.registerTap(now: Double(index) * 0.1, alreadyEnabled: false) }
        XCTAssertEqual(unlock.registerTap(now: 3.901, alreadyEnabled: false).count, 1)
    }

    func testFaultsConsumeOnlyMatchingRequestsAndSkipDevAPI() {
        DevFaults.clear()
        defer { DevFaults.clear() }
        DevFaults.set(.insightGenerating, count: 2)
        XCTAssertNil(DevFaults.take(path: "/api/groups/7/insights", method: "GET", enabled: false), "개발자 모드가 꺼지면 주입하지 않아요")
        XCTAssertEqual(DevFaults.take(path: "/api/groups/7/insights", method: "GET", enabled: true), .insightGenerating)
        XCTAssertEqual(DevFaults.count(.insightGenerating), 1)
        let body = DevFaults.response(for: .insightGenerating, path: "/api/groups/7/insights", query: "month=2026-10")
        XCTAssertEqual(body?.0, 202)
        XCTAssertEqual(String(data: body?.1 ?? Data(), encoding: .utf8),
                       #"{"groupId":7,"month":"2026-10","status":"GENERATING","retryAfterSeconds":2}"#)
        DevFaults.set(.insightGenerating, count: 0)
        DevFaults.set(.feedLocked, count: 1)
        XCTAssertNil(DevFaults.take(path: "/api/groups", method: "GET", enabled: true), "피드가 아닌 요청은 그대로")
        XCTAssertEqual(DevFaults.take(path: "/api/missions/8/submissions", method: "GET", enabled: true), .feedLocked)
        DevFaults.set(.serverError, count: 1)
        XCTAssertNil(DevFaults.take(path: "/api/dev/status", method: "GET", enabled: true), "개발자 API 는 건너뛰어요")
        XCTAssertEqual(DevFaults.count(.serverError), 1)
        XCTAssertEqual(DevFaults.take(path: "/api/groups", method: "GET", enabled: true), .serverError)
        XCTAssertNil(DevFaults.response(for: .networkDown, path: "/api/groups", query: nil))
    }

    func testKoreanParticles() {
        XCTAssertEqual(MissionType.draw.subjectForm, "그림이")
        XCTAssertEqual(MissionType.slider.subjectForm, "슬라이더가")
        XCTAssertEqual(MissionType.photo.objectForm, "사진을")
        XCTAssertEqual(MissionType.slider.objectForm, "슬라이더를")
        XCTAssertEqual(KoreanText.josa("달", "이라고", "라고"), "이라고")
        XCTAssertEqual(KoreanText.josa("해", "이라고", "라고"), "라고")
        XCTAssertEqual(KoreanText.josa("cat", "이", "가"), "가")
    }
}
