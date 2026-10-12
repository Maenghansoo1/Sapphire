import Foundation

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

    /// 가입 → 로그인 → 고른 캐릭터 저장. `skipRegister` 는 가입은 됐는데 다음 단계에서 실패해 다시 누른 경우예요.
    func signUp(
        email: String, password: String, nickname: String, character: ProfileCharacter,
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
            let page: PageDTO<CharacterDTO> = try await api.get("/api/characters?page=0&size=100", auth: false)
            if let match = page.content.first(where: { $0.svgKey == character.rawValue }) {
                let _: CharacterUpdateResponseDTO = try await api.patch("/api/me/character", body: CharacterUpdateDTO(characterId: match.id))
            }
        } catch is APIError {
            // 가입·로그인은 끝났어요. 기본 캐릭터로 두고, 나중에 프로필 편집에서 다시 고를 수 있어요.
        } catch is NetworkError {
        }
        return try await restore()
    }

    func restore() async throws -> MyAccount {
        let me: MeDTO = try await api.get("/api/me")
        return MyAccount(
            profile: Profile(avatar: .character(ProfileCharacter.fromKey(me.characterKey)), nickname: me.nickname),
            email: me.email,
            joinedAt: ServerDate.instant(me.createdAt) ?? .now,
            signUpProvider: .email,
            linkedProviders: [.email],
            userID: me.id
        )
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
}
