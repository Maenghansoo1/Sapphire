import Foundation

/// 서버가 돌려준 오류 — 본문 `{ "code", "message" }` 를 그대로 담아요.
struct APIError: Error, Equatable {
    let status: Int
    let code: String
    let message: String
}

/// 서버에 닿지 못했을 때 (오프라인, 시간 초과 등)
struct NetworkError: Error {
    let underlying: Error
}

extension Error {
    /// 화면에 보여 줄 안내 문구
    var userMessage: String {
        if self is NetworkError { return "네트워크 연결을 확인해 주세요" }
        guard let error = self as? APIError else { return "잠시 후 다시 시도해 주세요" }
        switch error.code {
        case "EMAIL_TAKEN": return "이미 가입된 이메일이에요"
        case "INVALID_CREDENTIALS": return "이메일 또는 비밀번호가 올바르지 않아요"
        case "VALIDATION_ERROR": return "입력한 내용을 다시 확인해 주세요"
        case "INVITE_NOT_FOUND": return "초대 코드를 찾을 수 없어요"
        case "GROUP_FULL": return "이 그룹은 정원이 다 찼어요"
        case "ALREADY_MEMBER": return "이미 참여 중인 그룹이에요"
        case "GROUP_NOT_FOUND": return "그룹을 찾을 수 없어요"
        case "NOT_GROUP_MEMBER": return "이 그룹의 멤버가 아니에요"
        case "SUCCESSOR_REQUIRED": return "그룹장은 다른 멤버에게 그룹장을 넘긴 뒤 나갈 수 있어요. 지금은 준비 중이에요"
        case "INVALID_TOKEN", "TOKEN_EXPIRED": return "다시 로그인해 주세요"
        default: return "잠시 후 다시 시도해 주세요"
        }
    }

    var apiCode: String? { (self as? APIError)?.code }
    var apiStatus: Int? { (self as? APIError)?.status }
}
