import XCTest
@testable import HAWE

/// Android `DataStoreTest` 중 서버 연결 뒤 상태 전이(하루 여러 미션·재촉·리롤·지난 미션 결과)를 옮겼어요.
@MainActor
final class DataStoreTests: XCTestCase {
    private let now = ISO8601DateFormatter().date(from: "2026-10-12T00:00:00Z")!
    private let myID = 1

    private func group(id: Int = 3, members: [GroupMember]? = nil) -> GroupDetail {
        GroupDetail(id: id, name: "컴공 동기", relationship: .friend, createdAt: now, missionCount: 10, inviteCode: "K7Q2PX",
                    topicTime: DateComponents(hour: 9, minute: 0), notify: true,
                    members: members ?? [
                        GroupMember(id: myID, defaultProfile: .mockMine, isMe: true, isOwner: true),
                        GroupMember(id: 7, defaultProfile: Profile(avatar: .character(.gamja), nickname: "민준")),
                        GroupMember(id: 8, defaultProfile: Profile(avatar: .character(.kkotnim), nickname: "지우")),
                    ])
    }

    private func mission(_ id: Int, topic: String = "주제", template: String = MissionTemplates.textOneWord,
                         submittedByMe: Bool = false, members: [Int: Bool] = [:], poked: [Int: Set<PokeKind>] = [:]) -> ServerMission {
        ServerMission(id: id, number: id, topic: topic, type: .write, template: template, spec: MissionSpec(),
                      publishedAt: now, deadlineAt: now.addingTimeInterval(3600), closesAt: now.addingTimeInterval(86_400),
                      submittedByMe: submittedByMe, members: members, pokedByMe: poked)
    }

    private func submission(_ id: Int, author: Int, template: String = MissionTemplates.textOneWord) -> Submission {
        Submission(id: id, author: .mockMine, isMine: author == myID, type: .write, createdAt: now, authorID: author, template: template)
    }

    private func loadedStore() -> DataStore {
        let store = DataStore()
        store.applyServerGroups([LoadedGroup(detail: group(), missions: [
            MissionSummary(missionID: 10, type: .write, topic: "주제", submittedCount: 1, submittedByMe: false),
            MissionSummary(missionID: 11, type: .write, topic: "주제", submittedCount: 0, submittedByMe: false),
        ])])
        return store
    }

    func testHomeSummaryCountsMultipleMissions() {
        let store = loadedStore()
        let summary = store.summaries[0]
        XCTAssertEqual(summary.missionCount, 2)
        XCTAssertEqual(summary.mySubmittedCount, 0)
        XCTAssertFalse(summary.didSubmit)
        // 05 화면이 지금 미션들을 받은 뒤에 올려요 (Android 와 같이 받은 미션만 반영해요).
        store.applyCurrentMission(CurrentMissionState(missions: [mission(10, members: [7: true]), mission(11)], nextPublishAt: nil), groupID: 3)
        store.applySubmitted(submission(100, author: myID), missionID: 10, groupID: 3)
        XCTAssertEqual(store.summaries[0].mySubmittedCount, 1)
        XCTAssertEqual(store.summaries[0].submittedCount, 2)
        XCTAssertFalse(store.summaries[0].didSubmit, "두 개 중 하나만 냈어요")
        store.applySubmitted(submission(101, author: myID), missionID: 11, groupID: 3)
        XCTAssertTrue(store.summaries[0].didSubmit)
    }

    func testCurrentMissionsKeepFeedAndPokesOfSameMission() {
        let store = loadedStore()
        store.applyCurrentMission(CurrentMissionState(missions: [mission(10, members: [7: false]), mission(11)], nextPublishAt: nil), groupID: 3)
        XCTAssertEqual(store.todayMissions(3).map(\.id), [10, 11])
        store.applyFeed([submission(100, author: 8)], missionID: 10, groupID: 3)
        store.markPoked(7, kind: .poke, missionID: 10, groupID: 3)
        // 늦게 온 새로고침에 재촉이 없어도 지우지 않아요.
        store.applyCurrentMission(CurrentMissionState(missions: [mission(10, members: [7: false], poked: [7: [.fling]]), mission(11)],
                                                      nextPublishAt: nil), groupID: 3)
        let first = try! XCTUnwrap(store.todayMission(3, missionID: 10))
        XCTAssertEqual(first.members.first { $0.id == 7 }?.poked, [.poke, .fling])
        XCTAssertNotNil(store.result(groupID: 3, missionID: 10), "같은 미션이면 받아 둔 피드를 유지해요")
        // 리롤로 주제가 바뀌면 같은 id 여도 새 미션으로 봐요.
        store.applyCurrentMission(CurrentMissionState(missions: [mission(10, topic: "새 주제"), mission(11)], nextPublishAt: nil), groupID: 3)
        XCTAssertNil(store.result(groupID: 3, missionID: 10))
        XCTAssertEqual(store.todayMission(3, missionID: 10)?.members.first { $0.id == 7 }?.poked, [])
    }

    func testSubmittedStateSurvivesStaleRefresh() {
        let store = loadedStore()
        store.applyCurrentMission(CurrentMissionState(missions: [mission(10)], nextPublishAt: nil), groupID: 3)
        store.applySubmitted(submission(100, author: myID), missionID: 10, groupID: 3)
        XCTAssertTrue(store.todayMission(3)?.didSubmit ?? false)
        store.applyCurrentMission(CurrentMissionState(missions: [mission(10, submittedByMe: false)], nextPublishAt: nil), groupID: 3)
        XCTAssertTrue(store.todayMission(3)?.didSubmit ?? false, "서버가 늦게 알아도 낸 표시는 유지해요")
    }

    func testRerollClearsSubmissionsPokesAndSummary() {
        let store = loadedStore()
        store.applyCurrentMission(CurrentMissionState(missions: [mission(10, submittedByMe: true, members: [myID: true, 7: true],
                                                                         poked: [8: [.poke]])], nextPublishAt: nil), groupID: 3)
        store.applyFeed([submission(100, author: myID)], missionID: 10, groupID: 3)
        store.clearMissionForReroll(10, groupID: 3)
        let mission = try! XCTUnwrap(store.todayMission(3))
        XCTAssertFalse(mission.didSubmit)
        XCTAssertEqual(mission.doneCount, 0)
        XCTAssertTrue(mission.members.allSatisfy { $0.poked.isEmpty })
        XCTAssertNil(store.result(groupID: 3, missionID: 10))
        XCTAssertEqual(store.storedGroup(3)?.summaries.first?.submittedCount, 0)
    }

    func testPastMissionResultUsesCalendarAndSpec() {
        let store = loadedStore()
        store.applyCurrentMission(CurrentMissionState(missions: [mission(10)], nextPublishAt: nil), groupID: 3)
        let record = DayRecord(date: now, type: .write, topic: "지난 주제", doneCount: 1, memberCount: 3, didSubmit: true, missionID: 5)
        store.applyCalendar([record], groupID: 3, month: now)
        XCTAssertTrue(store.needsMissionSpec(groupID: 3, missionID: 5))
        XCTAssertFalse(store.needsMissionSpec(groupID: 3, missionID: 10), "지금 미션은 이미 알아요")
        store.applyFeed([submission(200, author: 7, template: MissionTemplates.textBalance)], missionID: 5, groupID: 3)
        var result = try! XCTUnwrap(store.result(groupID: 3, missionID: 5))
        XCTAssertEqual(result.topic, "지난 주제")
        XCTAssertEqual(result.template, MissionTemplates.textBalance, "틀은 제출물에서 알 수 있어요")
        XCTAssertEqual(result.pending.count, 2)
        store.applyMissionSpec(5, template: MissionTemplates.textBalance, spec: MissionSpec(optionA: "짜장면", optionB: "짬뽕"))
        XCTAssertFalse(store.needsMissionSpec(groupID: 3, missionID: 5))
        result = try! XCTUnwrap(store.result(groupID: 3, missionID: 5))
        XCTAssertEqual(result.spec.optionA, "짜장면")
    }

    func testProfileAndTopicTimeUpdates() {
        let store = loadedStore()
        let profile = Profile(avatar: .character(.gomdol), nickname: "새 이름")
        store.setMyProfile(profile)
        XCTAssertEqual(store.group(3)?.me?.defaultProfile, profile)
        store.setTopicTime(DateComponents(hour: 21, minute: 30), groupID: 3)
        XCTAssertEqual(store.group(3)?.topicTime, DateComponents(hour: 21, minute: 30))
        XCTAssertEqual(GroupInfoView.timeText(DateComponents(hour: 21, minute: 30)), "오후 9:30")
        XCTAssertEqual(GroupInfoView.timeText(DateComponents(hour: 0, minute: 5)), "오전 12:05")
    }

    func testWithdrawBlockersAreOwnedGroupsWithOthers() {
        let store = loadedStore()
        store.applyServerGroup(group(id: 4, members: [GroupMember(id: myID, defaultProfile: .mockMine, isMe: true, isOwner: true)]))
        XCTAssertEqual(WithdrawView.blockers(store.groups), ["컴공 동기"], "혼자 있는 그룹은 함께 지워져서 막지 않아요")
    }

    func testGroupProfileStateRules() {
        let me = GroupMember(id: myID, defaultProfile: .mockMine, isMe: true)
        var state = GroupProfileState(member: me, myDefault: .mockMine)
        XCTAssertTrue(state.useDefault)
        XCTAssertNil(state.profileToSave)
        XCTAssertTrue(state.canSave)
        state.useDefault = false
        state.nickname = "가"
        XCTAssertFalse(state.canSave, "닉네임은 2–10자")
        state.nickname = "열한글자가넘는닉네임이에요"
        XCTAssertEqual(state.nickname.count, 10)
        // 서버 사진 프로필은 닉네임만 바꿀 수 없어요.
        let photo = Profile(avatar: .photo(URL(string: "http://stub.local/p.jpg")!), nickname: "사진")
        var withPhoto = GroupProfileState(member: GroupMember(id: myID, defaultProfile: .mockMine, groupProfile: photo, isMe: true),
                                          myDefault: .mockMine)
        XCTAssertFalse(withPhoto.needsPhotoReselect)
        withPhoto.nickname = "바꾼 이름"
        XCTAssertTrue(withPhoto.needsPhotoReselect)
        withPhoto.avatar = .character(.gamja)
        XCTAssertFalse(withPhoto.needsPhotoReselect)
    }

    func testChangePasswordRules() {
        XCTAssertFalse(ChangePasswordState(current: "old12345", new: "short1", confirm: "short1").canSave)
        XCTAssertEqual(ChangePasswordState(new: "abcdefgh").newError, "8자 이상, 영문과 숫자를 함께 써 주세요")
        XCTAssertEqual(ChangePasswordState(new: "abcd1234", confirm: "abcd123").confirmError, "비밀번호가 일치하지 않아요")
        XCTAssertFalse(ChangePasswordState(current: "abcd1234", new: "abcd1234", confirm: "abcd1234").canSave, "같은 비밀번호로는 못 바꿔요")
        XCTAssertTrue(ChangePasswordState(current: "old12345", new: "abcd1234", confirm: "abcd1234").canSave)
    }
}
