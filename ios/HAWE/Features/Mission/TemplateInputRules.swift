import Foundation

/// 새 틀 입력 한도. 서버(`MISSIONS_API.md` §3-2, B5)와 같은 값이에요.
/// 글은 글자(Swift `Character` = grapheme), 이유(caption)는 코드포인트로 세요.
enum TemplateLimits {
    static let oneWord = 20
    static let oneWordReason = 100
    static let review = 100
    static let balanceReason = 200
    static let stars = 5
}

enum TemplateInput {
    static func codePointCount(_ text: String) -> Int { text.unicodeScalars.count }

    static func limitCodePoints(_ text: String, _ limit: Int) -> String {
        guard text.unicodeScalars.count > limit else { return text }
        var scalars = String.UnicodeScalarView()
        scalars.append(contentsOf: text.unicodeScalars.prefix(limit))
        return String(scalars)
    }

    static func limitGraphemes(_ text: String, _ limit: Int) -> String {
        text.count <= limit ? text : String(text.prefix(limit))
    }

    /// 한 단어: 띄어쓰기·줄바꿈은 입력하는 순간 지워요 (서버는 단어 안의 공백을 거부해요).
    static func oneWord(_ input: String) -> String {
        limitGraphemes(String(input.filter { !$0.isWhitespace }), TemplateLimits.oneWord)
    }

    /// 한 줄 후기: 줄바꿈은 띄어쓰기로 바꿔요.
    static func review(_ input: String) -> String {
        let flattened = input.replacingOccurrences(of: "\r\n", with: " ")
            .replacingOccurrences(of: "\n", with: " ")
            .replacingOccurrences(of: "\r", with: " ")
        return limitGraphemes(flattened, TemplateLimits.review)
    }

    static func reason(_ input: String, limit: Int) -> String { limitCodePoints(input, limit) }

    /// 비었거나 공백뿐인 이유는 보내지 않아요.
    static func optionalCaption(_ reason: String) -> String? {
        let trimmed = reason.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}

struct OneWordState: Equatable {
    var word = ""
    var reason = ""

    var canSubmit: Bool { !word.isEmpty }
    var hasDraft: Bool { !word.isEmpty || !reason.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    var wordCounter: String { "\(word.count) / \(TemplateLimits.oneWord)" }
    var reasonCounter: String { "\(TemplateInput.codePointCount(reason)) / \(TemplateLimits.oneWordReason)" }
    var content: ScalarAnswer { ScalarAnswer(text: word, caption: TemplateInput.optionalCaption(reason)) }
}

struct ReviewState: Equatable {
    var stars = 0
    var line = ""

    var canSubmit: Bool { (1...TemplateLimits.stars).contains(stars) && !line.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    var hasDraft: Bool { stars > 0 || !line.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    var counter: String { "\(line.count) / \(TemplateLimits.review)" }
    var content: ScalarAnswer { ScalarAnswer(number: stars, text: line.trimmingCharacters(in: .whitespacesAndNewlines)) }
}

struct BalanceState: Equatable {
    var choice: String?
    var reason = ""

    var canSubmit: Bool { choice == "A" || choice == "B" }
    var hasDraft: Bool { choice != nil || !reason.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    var counter: String { "\(TemplateInput.codePointCount(reason)) / \(TemplateLimits.balanceReason)" }
    var content: ScalarAnswer { ScalarAnswer(choice: choice, caption: TemplateInput.optionalCaption(reason)) }
}
