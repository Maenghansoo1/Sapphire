/// 오늘의 미션 콘텐츠 유형 (CLAUDE.md §1, DB `topics.type`)
enum MissionType: String, CaseIterable, Identifiable, Codable {
    case draw, photo, write, voice

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .draw: "그림"
        case .photo: "사진"
        case .write: "글"
        case .voice: "음성"
        }
    }

    var icon: HIcon {
        switch self {
        case .draw: .pencil
        case .photo: .camera
        case .write: .text
        case .voice: .mic
        }
    }
}
