import Foundation

/// 15 그룹 달력과 17 활동 요약 (`/api/groups/{id}/calendar`, `/api/me/stats`)
final class CalendarRepository: Sendable {
    private let api: APIClient

    init(api: APIClient) {
        self.api = api
    }

    /// 그 달에 미션이 있었던 날의 기록. 앱이 모르는 유형(media)의 미션은 빼요.
    func month(groupID: Int, month: Date) async throws -> [DayRecord] {
        let response: CalendarDTO = try await api.get("/api/groups/\(groupID)/calendar?month=\(Self.monthString(month))")
        return response.days.flatMap { day -> [DayRecord] in
            guard let date = ServerDate.day(day.date) else { return [] }
            return day.missions.compactMap { mission in
                guard let type = mission.media.flatMap(MissionType.init(media:)) else { return nil }
                let stamp = mission.stamp.flatMap(Stamp.init(rawValue:))
                return DayRecord(
                    date: date,
                    type: type,
                    topic: mission.content,
                    doneCount: mission.submittedCount,
                    memberCount: mission.memberCount,
                    didSubmit: stamp == .good || stamp == .late,
                    missionID: mission.missionId,
                    stamp: stamp
                )
            }
        }
    }

    func stats() async throws -> ActivityStats {
        let response: MyStatsDTO = try await api.get("/api/me/stats")
        var byType: [MissionType: Int] = [:]
        for (media, count) in response.byMedia ?? [:] {
            if let type = MissionType(media: media) { byType[type] = count }
        }
        return ActivityStats(
            completedMissions: response.completedMissions,
            streakDays: response.streakDays,
            groupCount: response.groupCount,
            byType: byType
        )
    }

    /// 서울 기준 달 → `2026-10`
    static func monthString(_ month: Date) -> String {
        String(ServerDate.dayString(month).prefix(7))
    }
}
