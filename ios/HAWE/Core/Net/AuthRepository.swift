import UIKit

/// 프로필 저장 결과 — 일부만 저장됐어도 서버의 최신 내 정보(`account`)를 같이 돌려줘요.
struct SaveProfileResult {
    var account: MyAccount?
    var error: Error?
}

/// 로그인·회원가입·내 정보 (`/api/auth`, `/api/me`)
final class AuthRepository: Sendable {
    private let api: APIClient
    private let tokens: TokenStore

    init(api: APIClient, tokens: TokenStore) {
        self.api = api
        self.tokens = tokens
    }

    /// 저장된 세션이 있으면 앱을 켤 때 로그인을 이어가요.
    var hasSession: Bool { tokens.refreshToken != nil }

    func login(email: String, password: String) async throws -> MyAccount {
        try await authenticate(email: email, password: password)
        return try await restore()
    }

    /// 가입 → 로그인 → 고른 캐릭터(또는 사진) 저장. `skipRegister` 는 가입은 됐는데 다음 단계에서 실패해 다시 누른 경우예요.
    func signUp(
        email: String, password: String, nickname: String, avatar: AvatarContent,
        agreeTerms: Bool, agreePrivacy: Bool, skipRegister: Bool, onRegistered: () -> Void
    ) async throws -> MyAccount {
        let trimmedEmail = email.trimmingCharacters(in: .whitespaces)
        if !skipRegister {
            let _: RegisterResponseDTO = try await api.post("/api/auth/register", body: RegisterRequestDTO(
                email: trimmedEmail, password: password, nickname: nickname.trimmingCharacters(in: .whitespaces),
                agreeTerms: agreeTerms, agreePrivacy: agreePrivacy
            ), auth: false)
            onRegistered()
        }
        try await authenticate(email: trimmedEmail, password: password)
        do {
            switch avatar {
            case .character(let character):
                try await updateCharacter(character)
            case .image(let image):
                try await uploadProfileImage(image)
            case .photo:
                break
            }
        } catch is APIError {
            // 가입·로그인은 끝났어요. 기본 캐릭터로 두고, 나중에 프로필 편집에서 다시 고를 수 있어요.
        } catch is NetworkError {
        } catch is AvatarPhotoError {
        }
        return try await restore()
    }

    func restore() async throws -> MyAccount {
        let me: MeDTO = try await api.get("/api/me")
        return MyAccount(
            profile: Profile(avatar: api.avatar(characterKey: me.characterKey, profileImageURL: me.profileImageUrl), nickname: me.nickname),
            email: me.email,
            joinedAt: ServerDate.instant(me.createdAt) ?? .now,
            signUpProvider: .email,
            linkedProviders: [.email],
            userID: me.id
        )
    }

    /// Retry only temporary failures while the startup screen is restoring the session.
    func restoreWithRetry(delays: [TimeInterval] = [1, 2]) async throws -> MyAccount {
        for seconds in delays {
            try Task.checkCancellation()
            do {
                return try await restore()
            } catch is CancellationError {
                throw CancellationError()
            } catch {
                guard error is NetworkError || (error as? APIError).map({ $0.status >= 500 }) == true else { throw error }
                try await Task.sleep(for: .seconds(seconds))
            }
        }
        try Task.checkCancellation()
        return try await restore()
    }

    /// 기본 프로필 저장 — 닉네임 → 캐릭터(또는 사진) → `GET /me` 순서예요 (Android 와 같아요).
    /// 중간에 실패해도 서버의 최신 내 정보를 받아 화면을 맞춰요.
    func saveProfile(current: Profile, nickname: String, avatar: AvatarContent) async throws -> SaveProfileResult {
        var failure: Error?
        do {
            let trimmed = nickname.trimmingCharacters(in: .whitespaces)
            if trimmed != current.nickname {
                let _: MeDTO = try await api.patch("/api/me/profile", body: ProfileUpdateDTO(nickname: trimmed))
            }
            switch avatar {
            case .character(let character):
                if current.avatar != .character(character) { try await updateCharacter(character) }
                if current.avatar.isPhoto { try await api.delete("/api/me/profile-image") }
            case .image(let image):
                try await uploadProfileImage(image)
            case .photo:
                break
            }
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            failure = error
        }
        do {
            return SaveProfileResult(account: try await restore(), error: failure)
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            return SaveProfileResult(account: nil, error: failure ?? error)
        }
    }

    /// 회원 탈퇴 (`DELETE /api/me`) — 다른 멤버가 있는 그룹의 그룹장이면 서버가 막아요(OWNER_MUST_TRANSFER).
    func withdraw() async throws {
        try await api.delete("/api/me")
    }

    /// 비밀번호를 바꾸면 서버가 다른 기기의 토큰을 끊고 새 토큰을 줘요.
    func changePassword(current: String, new: String) async throws {
        let result: TokenResponseDTO = try await api.patch(
            "/api/me/password", body: PasswordChangeRequestDTO(currentPassword: current, newPassword: new)
        )
        tokens.save(access: result.accessToken, refresh: result.refreshToken)
    }

    func clearSession() { tokens.clear() }

    private func authenticate(email: String, password: String) async throws {
        let result: TokenResponseDTO = try await api.post(
            "/api/auth/login",
            body: LoginRequestDTO(email: email.trimmingCharacters(in: .whitespaces), password: password),
            auth: false
        )
        tokens.save(access: result.accessToken, refresh: result.refreshToken)
    }

    private func updateCharacter(_ character: ProfileCharacter) async throws {
        let page: PageDTO<CharacterDTO> = try await api.get("/api/characters?page=0&size=100", auth: false)
        guard let match = page.content.first(where: { $0.svgKey == character.rawValue }) else { return }
        let _: CharacterUpdateResponseDTO = try await api.patch("/api/me/character", body: CharacterUpdateDTO(characterId: match.id))
    }

    private func uploadProfileImage(_ image: UIImage) async throws {
        let data = try await AvatarPhoto.uploadData(image)
        let _: ProfileImageResponseDTO = try await api.putMultipart("/api/me/profile-image", parts: [
            .file(name: "file", filename: "profile.jpg", mime: "image/jpeg", data: data),
        ])
    }
}

struct AvatarPhotoError: Error {}

/// 프로필 사진 올리기 — 서버 한도(10MB) 안에 들게 긴 변 2048 까지 줄여 JPEG 로 보내요.
enum AvatarPhoto {
    static let maxSide: CGFloat = 2048

    static func uploadData(_ image: UIImage) async throws -> Data {
        let data = await Task.detached(priority: .userInitiated) { () -> Data? in
            let size = image.size
            let longest = max(size.width, size.height)
            let scale = longest > maxSide ? maxSide / longest : 1
            let target = CGSize(width: (size.width * scale).rounded(), height: (size.height * scale).rounded())
            let format = UIGraphicsImageRendererFormat()
            format.scale = 1
            let upright = UIGraphicsImageRenderer(size: target, format: format).image { _ in
                image.draw(in: CGRect(origin: .zero, size: target))
            }
            return upright.jpegData(compressionQuality: 0.9)
        }.value
        guard let data else { throw AvatarPhotoError() }
        return data
    }
}
