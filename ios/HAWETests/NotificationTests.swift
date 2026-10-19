import XCTest
@testable import HAWE

/// Android `NotificationRepositoryTest` · `NotificationFormatTest` 를 옮겼어요.
final class NotificationTests: XCTestCase {
    private var api: APIClient!

    override func setUp() {
        StubProtocol.reset()
        let tokens = InMemoryTokenStore()
        tokens.save(access: "access", refresh: "refresh")
        api = APIClient(baseURL: URL(string: "http://stub.local")!, tokens: tokens, protocolClasses: [StubProtocol.self])
    }

    private let now = ISO8601DateFormatter().date(from: "2026-10-19T06:00:00Z")! // 서울 15:00

    private func item(_ type: NotificationType, from: String? = "서연", body: String? = nil, missionID: Int? = 10, at: Date? = nil) -> AppNotification {
        AppNotification(id: 1, type: type, groupID: 3, groupName: "컴공 동기",
                        from: from.map { Profile(avatar: .character(.mulbangul), nickname: $0) },
                        missionID: missionID, submissionID: nil, body: body, isRead: false, createdAt: at ?? now)
    }

    func testListMapsAndSkipsUnknownTypes() async throws {
        StubProtocol.respond(200, #"""
        {"content":[
          {"id":1,"type":"poke","groupId":3,"groupName":"컴공 동기",
           "fromUser":{"userId":7,"nickname":"서연","characterKey":"mulbangul","profileImageUrl":null},
           "missionId":10,"submissionId":null,"body":"서연님이 콕 찔렀어요","isRead":false,"createdAt":"2026-10-19T15:41:48+09:00"},
          {"id":2,"type":"topic","groupId":3,"groupName":"컴공 동기","fromUser":null,"missionId":11,"isRead":true,"createdAt":"2026-10-19T09:00:00+09:00"},
          {"id":3,"type":"hug","groupId":3,"createdAt":"2026-10-19T09:00:00+09:00"}],
         "page":0,"size":50,"totalPages":1,"totalElements":3}
        """#)
        let items = try await NotificationRepository(api: api).list()
        XCTAssertEqual(StubProtocol.requests.first?.url?.path, "/api/notifications")
        XCTAssertEqual(items.map(\.id), [1, 2])
        XCTAssertEqual(items[0].type, .poke)
        XCTAssertEqual(items[0].from?.nickname, "서연")
        XCTAssertEqual(items[0].createdAt, ISO8601DateFormatter().date(from: "2026-10-19T06:41:48Z"))
        XCTAssertFalse(items[0].isRead)
        XCTAssertNil(items[1].from)
        XCTAssertNil(items[1].body)
    }

    func testMarkReadUsesPatch() async throws {
        StubProtocol.respond(204, "")
        StubProtocol.respond(204, "")
        let repository = NotificationRepository(api: api)
        try await repository.markRead(id: 5)
        try await repository.markAllRead()
        XCTAssertEqual(StubProtocol.requests.map(\.httpMethod), ["PATCH", "PATCH"])
        XCTAssertEqual(StubProtocol.requests.map { $0.url?.path }, ["/api/notifications/5/read", "/api/notifications/read-all"])
    }

    func testFallbackTextPerType() {
        XCTAssertEqual(NotificationFormat.text(item(.topic, from: nil)), "오늘의 미션이 열렸어요")
        XCTAssertEqual(NotificationFormat.text(item(.poke)), "서연님이 콕 찔렀어요")
        XCTAssertEqual(NotificationFormat.text(item(.comment)), "서연님이 내 작품에 댓글을 남겼어요")
        XCTAssertEqual(NotificationFormat.text(item(.mention)), "서연님이 댓글에서 나를 불렀어요")
        XCTAssertEqual(NotificationFormat.text(item(.reaction)), "서연님이 내 작품에 반응을 남겼어요")
        XCTAssertEqual(NotificationFormat.text(item(.poke, body: "서버 문장")), "서버 문장")
    }

    func testTargetFollowsContractRules() {
        XCTAssertEqual(NotificationFormat.target(item(.poke), groupExists: false, canOpenResults: true), .missing)
        XCTAssertEqual(NotificationFormat.target(item(.poke), groupExists: true, canOpenResults: true), .mission(groupID: 3))
        XCTAssertEqual(NotificationFormat.target(item(.comment), groupExists: true, canOpenResults: true), .results(groupID: 3, missionID: 10))
        XCTAssertEqual(NotificationFormat.target(item(.reaction), groupExists: true, canOpenResults: true), .results(groupID: 3, missionID: 10))
        XCTAssertEqual(NotificationFormat.target(item(.mention), groupExists: true, canOpenResults: false), .mission(groupID: 3))
        XCTAssertEqual(NotificationFormat.target(item(.comment, missionID: nil), groupExists: true, canOpenResults: true), .mission(groupID: 3))
    }

    func testSplitsTodayAndEarlierBySeoulDate() {
        let today = item(.poke, at: ISO8601DateFormatter().date(from: "2026-10-18T15:30:00Z")) // 서울 10/19 00:30
        let yesterday = item(.poke, at: ISO8601DateFormatter().date(from: "2026-10-18T14:30:00Z")) // 서울 10/18 23:30
        let sections = NotificationFormat.sections([today, yesterday], now: now)
        XCTAssertEqual(sections.map(\.0), [.today, .earlier])
        XCTAssertEqual(sections.map(\.1), [[today], [yesterday]])
        XCTAssertEqual(NotificationFormat.relativeText(now.addingTimeInterval(-300), now: now), "5분 전")
    }
}
