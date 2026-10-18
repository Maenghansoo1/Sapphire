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
        case "SUCCESSOR_REQUIRED", "INVALID_SUCCESSOR": return "그룹장을 받을 멤버를 다시 골라 주세요"
        case "NOT_GROUP_OWNER": return "그룹장만 할 수 있어요"
        case "TOO_MANY_REQUESTS": return "시도가 너무 많아요. 잠시 후 다시 시도해 주세요"
        case "WRONG_CURRENT_PASSWORD": return "현재 비밀번호가 맞지 않아요"
        case "TARGET_NOT_GROUP_MEMBER": return "그룹을 나간 멤버예요"
        case "CANNOT_POKE_SELF": return "나에게는 보낼 수 없어요"
        case "INVALID_TOKEN", "TOKEN_EXPIRED": return "다시 로그인해 주세요"
        case "OWNER_MUST_TRANSFER": return "다른 멤버가 있는 그룹의 그룹장이에요. 먼저 그룹장을 넘기고 탈퇴해 주세요"
        case "FILE_TOO_LARGE": return "사진 용량이 너무 커요 (10MB 이하)"
        case "UNSUPPORTED_FILE_TYPE": return "지원하지 않는 사진 형식이에요"
        default: return "잠시 후 다시 시도해 주세요"
        }
    }

    var apiCode: String? { (self as? APIError)?.code }
    var apiStatus: Int? { (self as? APIError)?.status }
}
