import XCTest
@testable import HAWE

/// Android `PokeRepositoryTest` · `SubmissionRepositoryTest` · `InsightLoaderTest` · `GroupRepositoryTest` 를 옮겼어요.
/// 서버 대신 `StubProtocol` 이 응답해요.
final class NetworkTests: XCTestCase {
    private var api: APIClient!
    private var tokens: InMemoryTokenStore!

    override func setUp() {
        StubProtocol.reset()
        tokens = InMemoryTokenStore()
        tokens.save(access: "access", refresh: "refresh")
        api = APIClient(baseURL: URL(string: "http://stub.local")!, tokens: tokens, protocolClasses: [StubProtocol.self])
    }

    func testPokeSendsKindAndMissionAndMapsResponse() async throws {
        StubProtocol.respond(201, #"{"id":1,"toUserId":7,"type":"poke","missionId":10,"sentAt":"2026-10-13T15:41:48+09:00"}"#)
        let sent = try await PokeRepository(api: api).send(groupID: 3, toUserID: 7, kind: .poke, missionID: 10)
        XCTAssertEqual(sent, SentPoke(toUserID: 7, kind: .poke, missionID: 10))
        let request = try XCTUnwrap(StubProtocol.requests.first)
        XCTAssertEqual(request.httpMethod, "POST")
        XCTAssertEqual(request.url?.path, "/api/groups/3/pokes")
        XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer access")
        let body = try XCTUnwrap(JSONSerialization.jsonObject(with: StubProtocol.bodies.first ?? Data()) as? [String: Any])
        XCTAssertEqual(body["toUserId"] as? Int, 7)
        XCTAssertEqual(body["type"] as? String, "poke")
        XCTAssertEqual(body["missionId"] as? Int, 10)
    }

    func testPokeServerErrorsKeepTheirCodes() async {
        StubProtocol.respond(409, #"{"code":"ALREADY_POKED","message":"이미 보낸 재촉입니다"}"#)
        do {
            _ = try await PokeRepository(api: api).send(groupID: 3, toUserID: 7, kind: .poke, missionID: nil)
            XCTFail("오류가 나야 해요")
        } catch {
            XCTAssertEqual(error.apiStatus, 409)
            XCTAssertEqual(error.apiCode, "ALREADY_POKED")
        }
    }

    func testPokeFailureClassification() {
        func api(_ status: Int, _ code: String) -> APIError { APIError(status: status, code: code, message: "") }
        // 이미 보냄·이미 제출함·미션 없음은 안내 없이 상태만 맞춰요 (CONTRACT §4)
        XCTAssertEqual(PokeFailure(error: api(409, "ALREADY_POKED")), PokeFailure(followUp: .markSent, message: nil))
        XCTAssertEqual(PokeFailure(error: api(409, "TARGET_ALREADY_SUBMITTED")), PokeFailure(followUp: .refresh, message: nil))
        XCTAssertEqual(PokeFailure(error: api(409, "NO_OPEN_MISSION")), PokeFailure(followUp: .refresh, message: nil))
        XCTAssertEqual(PokeFailure(error: api(404, "TARGET_NOT_GROUP_MEMBER")), PokeFailure(followUp: .refresh, message: "그룹을 나간 멤버예요"))
        XCTAssertEqual(PokeFailure(error: api(403, "NOT_GROUP_MEMBER")), PokeFailure(followUp: .leave, message: "이 그룹의 멤버가 아니에요"))
        XCTAssertEqual(PokeFailure(error: api(404, "GROUP_NOT_FOUND")), PokeFailure(followUp: .leave, message: "그룹을 찾을 수 없어요"))
        XCTAssertEqual(PokeFailure(error: api(400, "CANNOT_POKE_SELF")), PokeFailure(followUp: .none, message: "나에게는 보낼 수 없어요"))
        XCTAssertEqual(PokeFailure(error: api(500, "INTERNAL")), PokeFailure(followUp: .none, message: "잠시 후 다시 시도해 주세요"))
        XCTAssertEqual(PokeFailure(error: NetworkError(underlying: URLError(.notConnectedToInternet))),
                       PokeFailure(followUp: .none, message: "네트워크 연결을 확인해 주세요"))
    }

    func testFeedParsesNewTemplatesReactionsAndAiReview() async throws {
        StubProtocol.respond(200, """
        {"content":[{"id":5,"missionId":9,"author":{"userId":2,"nickname":"지우","characterKey":"kkotnim",
          "profileImageUrl":"/files/p.jpg"},"template":"TEXT_REVIEW","number":4,"text":"맛있어요",
          "submittedAt":"2026-10-12T21:00:00.123456+09:00","isLate":true,"commentCount":3,
          "reactions":[{"stickerType":"heart","count":2,"reactedByMe":true,"userIds":[1,2]},{"stickerType":"unknown","count":1}],
          "aiReview":{"badge":"PERFECT_FIT","comment":" 멋져요 "}},
         {"id":6,"missionId":9,"author":{"userId":1,"nickname":"나"},"template":"VOICE_CLIP","submittedAt":"2026-10-12T21:00:00+09:00"},
         {"id":7,"missionId":9,"author":{"userId":1,"nickname":"나"},"template":"SLIDER_CONSENSUS","number":70,"guess":55,"submittedAt":"2026-10-12T21:00:00+09:00"}],
         "page":0,"size":100,"totalPages":1,"totalElements":3}
        """)
        let feed = try await SubmissionRepository(api: api).feed(missionID: 9, myUserID: 1)
        XCTAssertEqual(feed.count, 1, "앱이 모르는 틀(없앤 음성·슬라이더)은 빼요")
        let submission = try XCTUnwrap(feed.first)
        XCTAssertEqual(submission.type, .write)
        XCTAssertEqual(submission.number, 4)
        XCTAssertEqual(submission.text, "맛있어요")
        XCTAssertTrue(submission.isLate)
        XCTAssertFalse(submission.isMine)
        XCTAssertEqual(submission.commentCount, 3)
        XCTAssertEqual(submission.reactions, [Reaction(sticker: .heart, count: 2, reactedByMe: true, userIDs: [1, 2])])
        XCTAssertEqual(submission.aiReview, AiReview(badge: "PERFECT_FIT", comment: "멋져요"))
        XCTAssertEqual(submission.author.avatar, .photo(URL(string: "http://stub.local/files/p.jpg")!))
        XCTAssertEqual(StubProtocol.requests.first?.url?.query, "page=0&size=100")
    }

    func testAggregateResultsMapToModels() async throws {
        StubProtocol.respond(200, #"{"template":"TEXT_BALANCE","totalCount":4,"choices":[{"choice":"A","count":3},{"choice":"B","count":1}],"myChoice":"A"}"#)
        let balance = try await SubmissionRepository(api: api).result(missionID: 9)
        XCTAssertEqual(balance, .balance(.init(totalCount: 4, countA: 3, countB: 1, myChoice: "A")))

        StubProtocol.respond(200, #"{"template":"SLIDER_CONSENSUS","totalCount":2,"average":62.5}"#)
        let removed = try await SubmissionRepository(api: api).result(missionID: 9)
        XCTAssertNil(removed, "없앤 슬라이더 틀의 결과는 보여 주지 않아요")

        StubProtocol.respond(200, #"{"template":"PHOTO_NOW","totalCount":1}"#)
        let unknown = try await SubmissionRepository(api: api).result(missionID: 9)
        XCTAssertNil(unknown)
    }

    func testScalarSubmitSendsOnlyFilledFields() async throws {
        StubProtocol.respond(201, #"{"id":7,"missionId":9,"author":{"userId":1,"nickname":"나"},"template":"TEXT_BALANCE","choice":"B","submittedAt":"2026-10-12T21:00:00+09:00"}"#)
        let submission = try await SubmissionRepository(api: api).submit(missionID: 9, content: .scalar(ScalarAnswer(choice: "B")), myUserID: 1)
        XCTAssertEqual(submission.choice, "B")
        XCTAssertTrue(submission.isMine)
        let body = String(data: StubProtocol.bodies.first ?? Data(), encoding: .utf8) ?? ""
        XCTAssertTrue(body.contains("name=\"choice\"\r\n\r\nB\r\n"))
        XCTAssertFalse(body.contains("name=\"caption\""))
        XCTAssertFalse(body.contains("name=\"text\""))
    }

    func testCommentsSendMentionsOnce() async throws {
        StubProtocol.respond(201, #"{"id":3,"author":{"userId":1,"nickname":"나"},"content":"@지우 안녕","mentionedUserIds":[2],"createdAt":"2026-10-12T21:00:00+09:00","mine":true}"#)
        let comment = try await SubmissionRepository(api: api).addComment(submissionID: 5, content: "@지우 안녕", mentionedUserIDs: [2, 2], image: nil)
        XCTAssertTrue(comment.isMine)
        XCTAssertEqual(comment.mentionedUserIDs, [2])
        let body = String(data: StubProtocol.bodies.first ?? Data(), encoding: .utf8) ?? ""
        XCTAssertEqual(body.components(separatedBy: "name=\"mentionedUserIds\"").count - 1, 1)
        XCTAssertEqual(StubProtocol.requests.first?.url?.path, "/api/submissions/5/comments")
    }

    func testTodayMissionsReadParamsPokesAndSlots() async throws {
        StubProtocol.respond(200, """
        {"date":"2026-10-12","dailyMissionCount":2,"nextPublishAt":null,"missions":[
         {"missionId":1,"groupId":3,"slotNo":1,"number":4,"content":"짜장 vs 짬뽕","template":"TEXT_BALANCE","media":"TEXT",
          "params":{"optionA":"짜장면","optionB":"짬뽕","target":null},"publishedAt":"2026-10-12T09:00:00+09:00",
          "deadlineAt":"2026-10-12T23:00:00+09:00","closesAt":"2026-10-13T09:00:00+09:00","submittedByMe":false,
          "members":[{"userId":7,"submitted":false,"pokedByMe":["poke","boom"]},{"userId":8,"submitted":true}]},
         {"missionId":2,"groupId":3,"slotNo":2,"number":5,"content":"고양이 그리기","template":"DRAW_SKETCH","media":"DRAW",
          "params":{"subject":"고양이","seconds":"30"},"publishedAt":"2026-10-12T15:00:00+09:00",
          "deadlineAt":"2026-10-12T23:00:00+09:00","closesAt":"2026-10-13T15:00:00+09:00","submittedByMe":true,"members":[]}]}
        """)
        let state = try await MissionRepository(api: api).current(groupID: 3)
        XCTAssertEqual(state.missions.map(\.id), [1, 2])
        let balance = state.missions[0]
        XCTAssertEqual(balance.spec.optionA, "짜장면")
        XCTAssertTrue(balance.spec.hasTarget, "target 이 null 이어도 키가 있으면 사람을 지목한 미션이에요")
        XCTAssertEqual(balance.pokedByMe, [7: [.poke]])
        XCTAssertEqual(balance.members, [7: false, 8: true])
        XCTAssertEqual(state.missions[1].type, .draw)
        XCTAssertEqual(state.missions[1].spec.subject, "고양이")
        XCTAssertEqual(state.missions[1].spec.seconds, 30)
    }

    func testInsightPollsWithBoundedDelayUntilReady() async throws {
        var calls = 0
        var sleeps: [Int] = []
        var states: [InsightOutcome] = []
        try await loadInsight({
            calls += 1
            return calls <= 2 ? .generating(retryAfterSeconds: calls == 1 ? 1 : 9) : .ready(Self.insight())
        }, sleep: { sleeps.append($0) }, onProgress: { states.append($0) })
        XCTAssertEqual(calls, 3)
        XCTAssertEqual(sleeps, [2, 6], "2~6초로 맞춰요")
        XCTAssertEqual(states, [.ready(Self.insight(), updating: false)])
    }

    func testInsightStopsAfterEightCalls() async throws {
        var calls = 0
        var states: [InsightOutcome] = []
        try await loadInsight({ calls += 1; return .generating(retryAfterSeconds: 2) }, sleep: { _ in }, onProgress: { states.append($0) })
        XCTAssertEqual(calls, 8)
        XCTAssertEqual(states, [.timedOut])
    }

    func testInsightShowsStaleImmediatelyThenReplacesOnce() async throws {
        var calls = 0
        var states: [InsightOutcome] = []
        try await loadInsight({ calls += 1; return .ready(Self.insight(stale: calls == 1)) }, sleep: { _ in }, onProgress: { states.append($0) })
        XCTAssertEqual(calls, 2)
        XCTAssertEqual(states, [.ready(Self.insight(stale: true), updating: true), .ready(Self.insight(), updating: false)])
    }

    func testInsightKeepsStaleIfRefreshFails() async throws {
        var calls = 0
        var states: [InsightOutcome] = []
        try await loadInsight({
            calls += 1
            if calls == 1 { return .ready(Self.insight(stale: true)) }
            throw NetworkError(underlying: URLError(.notConnectedToInternet))
        }, sleep: { _ in }, onProgress: { states.append($0) })
        XCTAssertEqual(states.last, .ready(Self.insight(stale: true), updating: false))
    }

    func testInsightRevokedMembershipDuringRefreshReachesScreen() async {
        var calls = 0
        do {
            try await loadInsight({
                calls += 1
                if calls == 1 { return .ready(Self.insight(stale: true)) }
                throw APIError(status: 403, code: "NOT_GROUP_MEMBER", message: "탈퇴한 그룹")
            }, sleep: { _ in }, onProgress: { _ in })
            XCTFail("오류가 화면까지 가야 해요")
        } catch {
            XCTAssertEqual(error.apiCode, "NOT_GROUP_MEMBER")
        }
    }

    func testInsightRejectsAnotherMonthsResponse() async {
        StubProtocol.respond(200, #"{"groupId":3,"month":"2026-09","status":"READY"}"#)
        do {
            _ = try await InsightRepository(api: api).load(groupID: 3, month: DataStore.calendar.date(from: DateComponents(year: 2026, month: 10, day: 1))!)
            XCTFail("다른 달 응답은 쓰지 않아요")
        } catch {
            XCTAssertEqual(error.apiCode, "INVALID_RESPONSE")
        }
    }

    private static func insight(stale: Bool = false) -> GroupInsight {
        GroupInsight(month: DataStore.calendar.date(from: DateComponents(year: 2026, month: 10, day: 1))!, stale: stale, empty: false,
                     activityIndex: nil, fastest: nil, topWords: [], mostReacted: nil, highlights: [])
    }
}

/// 서버 대신 미리 넣어 둔 응답을 차례로 돌려줘요.
final class StubProtocol: URLProtocol {
    nonisolated(unsafe) static var responses: [(Int, Data)] = []
    nonisolated(unsafe) static var requests: [URLRequest] = []
    nonisolated(unsafe) static var bodies: [Data] = []

    static func reset() {
        responses = []
        requests = []
        bodies = []
    }

    static func respond(_ status: Int, _ body: String) {
        responses.append((status, Data(body.utf8)))
    }

    override class func canInit(with request: URLRequest) -> Bool { request.url?.host == "stub.local" }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        Self.requests.append(request)
        Self.bodies.append(request.httpBody ?? request.httpBodyStream.map(Self.read) ?? Data())
        let (status, body) = Self.responses.isEmpty ? (500, Data()) : Self.responses.removeFirst()
        let response = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: "HTTP/1.1",
                                       headerFields: ["Content-Type": "application/json"])!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: body)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}

    private static func read(_ stream: InputStream) -> Data {
        stream.open()
        defer { stream.close() }
        var data = Data()
        var buffer = [UInt8](repeating: 0, count: 4096)
        while stream.hasBytesAvailable {
            let count = stream.read(&buffer, maxLength: buffer.count)
            if count <= 0 { break }
            data.append(buffer, count: count)
        }
        return data
    }
}
