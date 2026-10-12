import Foundation

extension DataStore {
    /// #Preview 용 — 체험 그룹(컴공 동기)에 참여하고 오늘 미션을 올린 상태
    static func preview(submitted: Bool = true) -> DataStore {
        let store = DataStore()
        guard let id = store.join(code: "K7Q2PX") else { return store }
        if submitted { store.markSubmittedForPreview(id) }
        return store
    }
}

/// #Preview 용 체험 그룹 (실제 화면은 서버 데이터를 써요).
/// K7Q2PX: 컴공 동기(친구 4명, 지난 기록·오늘 올린 친구 있음) · LOVE22: 우리 둘(연인 1명) · FULL00: 정원 초과
enum DemoSeed {
    static func group(code: String) -> StoredGroup? {
        switch code {
        case "K7Q2PX": friends
        case "LOVE22": lovers
        case "FULL00": full
        default: nil
        }
    }

    private static var calendar: Calendar { DataStore.calendar }

    /// 컴공 동기 — 2026년 6월 2일부터, 오늘은 민준·지우·서연이 이미 올렸어요.
    private static var friends: StoredGroup {
        let members = [
            member("민준", .gamja, owner: true),
            member("지우", .kkotnim),
            member("서연", .mulbangul),
            member("현우", .pilleum),
        ]
        let created = calendar.date(from: DateComponents(year: 2026, month: 6, day: 2)) ?? .now
        var group = StoredGroup(detail: GroupDetail(
            id: LocalID.next(),
            name: "컴공 동기",
            relationship: .friend,
            createdAt: created,
            missionCount: 0,
            inviteCode: "K7Q2PX",
            topicTime: DateComponents(hour: 9, minute: 0),
            notify: true,
            members: members
        ))
        group.missions = history(from: created, members: members, todayDone: 3)
        group.detail.missionCount = group.missions.count
        group.missionState = .loaded
        return group
    }

    /// 우리 둘 — 연인, 함께한 지 128일. 상대만 있어요.
    private static var lovers: StoredGroup {
        let members = [member("도윤", .ipsagwi, owner: true)]
        var detail = GroupDetail(
            id: LocalID.next(),
            name: "우리 둘",
            relationship: .lover,
            createdAt: calendar.date(byAdding: .day, value: -127, to: .now) ?? .now,
            missionCount: 0,
            inviteCode: "LOVE22",
            topicTime: DateComponents(hour: 9, minute: 0),
            notify: true,
            members: members
        )
        detail.startDate = detail.createdAt
        return StoredGroup(detail: detail, missionState: .loaded)
    }

    /// 정원 초과 확인용 (6명)
    private static var full: StoredGroup {
        let names: [(String, ProfileCharacter)] = [("가온", .gamja), ("나래", .gomdol), ("다온", .kkotnim), ("라온", .ipsagwi), ("마루", .mulbangul), ("바다", .pilleum)]
        return StoredGroup(detail: GroupDetail(
            id: LocalID.next(),
            name: "꽉 찬 그룹",
            relationship: .friend,
            createdAt: .now,
            missionCount: 0,
            inviteCode: "FULL00",
            topicTime: DateComponents(hour: 9, minute: 0),
            notify: true,
            members: names.map { member($0.0, $0.1) }
        ))
    }

    private static func member(_ name: String, _ character: ProfileCharacter, owner: Bool = false) -> GroupMember {
        GroupMember(id: LocalID.next(), defaultProfile: Profile(avatar: .character(character), nickname: name), isOwner: owner)
    }

    /// 만든 날부터 오늘까지 미션. 지난날은 3~4명이 올렸고, 오늘은 앞 `todayDone` 명이 올렸어요.
    private static func history(from start: Date, members: [GroupMember], todayDone: Int) -> [StoredMission] {
        let pattern: [MissionType] = [.draw, .photo, .write, .voice, .draw, .photo, .voice, .write, .draw, .photo]
        let topicTime = DateComponents(hour: 9, minute: 0)
        let todayWindow = TodayMission.window(topicTime: topicTime, now: .now)
        var missions: [StoredMission] = []
        var opens = calendar.date(bySettingHour: 9, minute: 0, second: 0, of: start) ?? start
        var index = 0
        while opens <= todayWindow.opens {
            let type = pattern[index % pattern.count]
            let topics = TopicPool.topics(for: type)
            let isToday = opens == todayWindow.opens
            let doneCount = isToday ? todayDone : (index % 5 == 4 ? 0 : 3 + index % 2)
            let submissions = members.prefix(doneCount).enumerated().map { offset, member in
                sample(by: member, type: type, at: opens.addingTimeInterval(Double(offset + 1) * 47 * 60), seed: index + offset)
            }
            missions.append(StoredMission(
                id: LocalID.next(),
                type: type,
                topic: topics[index % topics.count],
                number: index + 1,
                opensAt: opens,
                closesAt: calendar.date(byAdding: .day, value: 1, to: opens) ?? opens,
                submissions: submissions,
                serverMembers: Dictionary(submissions.compactMap { $0.authorID.map { ($0, true) } }, uniquingKeysWith: { a, _ in a })
            ))
            index += 1
            opens = calendar.date(byAdding: .day, value: 1, to: opens) ?? opens.addingTimeInterval(86_400)
        }
        return missions
    }

    private static func sample(by member: GroupMember, type: MissionType, at date: Date, seed: Int) -> Submission {
        let texts = [
            "편의점 사장님이 \"오늘도 수고 많았어요\" 하면서 영수증을 건네주셨다. 별말 아닌데 하루가 풀렸음.",
            "엄마가 전화로 밥은 잘 먹고 다니냐고 물어봤다.",
            "조별과제 팀원이 네 덕분에 끝났다고 말해줬다. 사실 나도 덕분이었는데.",
            "교수님이 질문 좋다고 하셨다.",
        ]
        let base: [CGFloat] = [6, 10, 16, 22, 14, 20, 24, 12, 8, 18, 22, 16, 10, 6, 12, 20, 24, 18, 12, 8, 14, 20, 16, 10, 6, 10, 14, 8]
        let shift = (seed * 5) % base.count
        var submission = Submission(
            id: LocalID.next(),
            author: member.displayed,
            isMine: false,
            type: type,
            createdAt: date,
            text: type == .write ? texts[seed % texts.count] : nil,
            duration: type == .voice ? TimeInterval(7 + seed % 6) : nil,
            waveform: type == .voice ? (base[shift...] + base[..<shift]).map { $0 / 24 } : []
        )
        submission.authorID = member.id
        return submission
    }
}
