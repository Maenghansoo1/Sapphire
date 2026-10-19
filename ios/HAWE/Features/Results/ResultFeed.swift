import Foundation
import Observation

/// 결과 공개(10~12)와 작품 상세(14)가 함께 쓰는 상태 — 피드는 서버에서 받아 DataStore 에 넣어요.
@MainActor
@Observable
final class ResultFeed {
    let store: DataStore
    let groupID: Int
    let missionID: Int

    init(store: DataStore, groupID: Int, missionID: Int) {
        self.store = store
        self.groupID = groupID
        self.missionID = missionID
    }

    /// 피드를 아직 못 받았으면 nil
    var loadedResult: MissionResult? {
        store.result(groupID: groupID, missionID: missionID)
    }

    var result: MissionResult {
        loadedResult ?? MissionResult(
            id: missionID, groupName: "", type: .draw, topic: "", date: .now, memberCount: 0, submissions: [], pending: []
        )
    }

    var members: [GroupMember] { store.group(groupID)?.members ?? [] }

    func submission(_ id: Submission.ID) -> Submission? {
        result.submissions.first { $0.id == id }
    }
}
