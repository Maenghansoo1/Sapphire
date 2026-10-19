/// 오늘의 미션 콘텐츠 유형 (서버 `media`: DRAW · PHOTO · TEXT)
enum MissionType: String, CaseIterable, Identifiable, Codable {
    case draw, photo, write

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .draw: "그림"
        case .photo: "사진"
        case .write: "글"
        }
    }

    var icon: HIcon {
        switch self {
        case .draw: .pencil
        case .photo: .camera
        case .write: .text
        }
    }

    /// "그림이", "글이"처럼 받침에 맞춘 이름 + 이/가
    var subjectForm: String { displayName + KoreanText.josa(displayName, "이", "가") }

    /// "그림을", "글을"처럼 받침에 맞춘 이름 + 을/를
    var objectForm: String { displayName + KoreanText.josa(displayName, "을", "를") }
}

/// 한국어 조사
enum KoreanText {
    /// 마지막 글자에 받침이 있는지 (한글이 아니면 없다고 봐요)
    static func hasFinalConsonant(_ word: String) -> Bool {
        guard let scalar = word.unicodeScalars.last, (0xAC00...0xD7A3).contains(scalar.value) else { return false }
        return (scalar.value - 0xAC00) % 28 != 0
    }

    /// 받침에 맞는 조사: `josa("달", "이라고", "라고")` → "이라고"
    static func josa(_ word: String, _ withConsonant: String, _ withoutConsonant: String) -> String {
        hasFinalConsonant(word) ? withConsonant : withoutConsonant
    }
}
