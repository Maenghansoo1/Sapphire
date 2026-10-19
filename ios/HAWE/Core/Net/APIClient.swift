import Foundation

/// Spring 서버(`/api/...`)와 주고받는 HTTP 클라이언트.
/// 액세스 토큰이 만료되면(401 TOKEN_EXPIRED) 한 번만 재발급하고 같은 요청을 다시 보내요.
/// 재발급도 실패하면 토큰을 지우고 `onSessionExpired` 로 로그아웃시켜요.
actor APIClient {
    /// multipart/form-data 한 칸
    enum Part: Sendable {
        case text(name: String, value: String)
        case file(name: String, filename: String, mime: String, data: Data)
    }

    nonisolated let baseURL: URL
    private let tokens: TokenStore
    private let session: URLSession
    private let uploadSession: URLSession
    private let onSessionExpired: @Sendable () -> Void
    private var refreshTask: Task<String, Error>?

    private let decoder = JSONDecoder()
    private let encoder = JSONEncoder()

    /// `protocolClasses` 는 테스트에서 서버 대신 응답할 URLProtocol 을 넣을 때만 써요.
    init(baseURL: URL, tokens: TokenStore, onSessionExpired: @escaping @Sendable () -> Void = {}, protocolClasses: [AnyClass]? = nil) {
        self.baseURL = baseURL
        self.tokens = tokens
        self.onSessionExpired = onSessionExpired
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 20
        let upload = URLSessionConfiguration.default
        upload.timeoutIntervalForRequest = 60
        if let protocolClasses {
            config.protocolClasses = protocolClasses
            upload.protocolClasses = protocolClasses
        }
        session = URLSession(configuration: config)
        uploadSession = URLSession(configuration: upload)
    }

    /// 서버가 주는 `/files/...` 같은 루트 상대 경로를 절대 URL 로 바꿔요.
    nonisolated func absoluteURL(_ path: String) -> URL {
        if path.hasPrefix("http://") || path.hasPrefix("https://"), let url = URL(string: path) { return url }
        let base = baseURL.absoluteString.hasSuffix("/") ? String(baseURL.absoluteString.dropLast()) : baseURL.absoluteString
        let trimmed = path.hasPrefix("/") ? String(path.dropFirst()) : path
        return URL(string: "\(base)/\(trimmed)") ?? baseURL
    }

    /// 서버 사진 주소가 있으면 사진, 없으면 캐릭터 (Android `avatarOf`)
    nonisolated func avatar(characterKey: String?, profileImageURL: String?) -> AvatarContent {
        if let path = profileImageURL?.trimmingCharacters(in: .whitespaces), !path.isEmpty {
            return .photo(absoluteURL(path))
        }
        return .character(ProfileCharacter.fromKey(characterKey))
    }

    // MARK: - 요청

    func get<T: Decodable>(_ path: String, auth: Bool = true) async throws -> T {
        try decode(await execute("GET", path, auth: auth, upload: false) { (nil, nil) })
    }

    func post<B: Encodable, T: Decodable>(_ path: String, body: B, auth: Bool = true) async throws -> T {
        let data = try encoder.encode(body)
        return try decode(await execute("POST", path, auth: auth, upload: false) { (data, "application/json; charset=utf-8") })
    }

    func put<B: Encodable, T: Decodable>(_ path: String, body: B, auth: Bool = true) async throws -> T {
        let data = try encoder.encode(body)
        return try decode(await execute("PUT", path, auth: auth, upload: false) { (data, "application/json; charset=utf-8") })
    }

    func patch<B: Encodable, T: Decodable>(_ path: String, body: B, auth: Bool = true) async throws -> T {
        let data = try encoder.encode(body)
        return try decode(await execute("PATCH", path, auth: auth, upload: false) { (data, "application/json; charset=utf-8") })
    }

    func delete(_ path: String, auth: Bool = true) async throws {
        _ = try await execute("DELETE", path, auth: auth, upload: false) { (nil, nil) }
    }

    /// 응답 본문이 있는 DELETE (개발자 시계 초기화)
    func deleteReturning<T: Decodable>(_ path: String, auth: Bool = true) async throws -> T {
        try decode(await execute("DELETE", path, auth: auth, upload: false) { (nil, nil) })
    }

    func postMultipart<T: Decodable>(_ path: String, parts: [Part]) async throws -> T {
        try await multipart("POST", path, parts: parts)
    }

    func putMultipart<T: Decodable>(_ path: String, parts: [Part]) async throws -> T {
        try await multipart("PUT", path, parts: parts)
    }

    private func multipart<T: Decodable>(_ method: String, _ path: String, parts: [Part]) async throws -> T {
        try decode(await execute(method, path, auth: true, upload: true) {
            // 재시도할 때도 같은 본문을 보낼 수 있게 요청마다 새로 만들어요.
            let boundary = "HAWE-\(UUID().uuidString)"
            return (Self.multipartBody(parts, boundary: boundary), "multipart/form-data; boundary=\(boundary)")
        })
    }

    // MARK: - 내부

    private func decode<T: Decodable>(_ result: (Int, Data)) throws -> T {
        do {
            return try decoder.decode(T.self, from: result.1)
        } catch {
            throw APIError(status: result.0, code: "INVALID_RESPONSE", message: "서버 응답을 읽을 수 없습니다")
        }
    }

    private func execute(
        _ method: String, _ path: String, auth: Bool, upload: Bool,
        body: () -> (Data?, String?)
    ) async throws -> (Int, Data) {
        let usedAccess = auth ? tokens.accessToken : nil
        let first = try await send(method, path, body: body(), access: usedAccess, upload: upload)
        if (200..<300).contains(first.0) { return first }
        let error = Self.error(first, decoder: decoder)
        if auth, first.0 == 401, error.code == "TOKEN_EXPIRED" {
            let access = try await refreshOnce(usedAccess: usedAccess)
            let retry = try await send(method, path, body: body(), access: access, upload: upload)
            if (200..<300).contains(retry.0) { return retry }
            if retry.0 == 401 { expireSession() }
            throw Self.error(retry, decoder: decoder)
        }
        if auth, first.0 == 401, error.code == "INVALID_TOKEN" { expireSession() }
        throw error
    }

    /// 동시에 여러 요청이 만료돼도 재발급은 한 번만 해요.
    private func refreshOnce(usedAccess: String?) async throws -> String {
        if let current = tokens.accessToken, current != usedAccess { return current }
        if let refreshTask { return try await refreshTask.value }
        let task = Task { try await self.performRefresh() }
        refreshTask = task
        defer { refreshTask = nil }
        return try await task.value
    }

    private func performRefresh() async throws -> String {
        guard let refresh = tokens.refreshToken else {
            expireSession()
            throw APIError(status: 401, code: "INVALID_TOKEN", message: "다시 로그인해 주세요")
        }
        let body = try encoder.encode(RefreshRequestDTO(refreshToken: refresh))
        let response = try await send("POST", "/api/auth/refresh", body: (body, "application/json; charset=utf-8"), access: nil, upload: false)
        guard (200..<300).contains(response.0) else {
            if response.0 == 401 { expireSession() }
            throw Self.error(response, decoder: decoder)
        }
        let updated: TokenResponseDTO = try decode(response)
        tokens.save(access: updated.accessToken, refresh: updated.refreshToken)
        return updated.accessToken
    }

    private func expireSession() {
        let hadSession = tokens.accessToken != nil || tokens.refreshToken != nil
        guard hadSession else { return }
        tokens.clear()
        onSessionExpired()
    }

    private func send(
        _ method: String, _ path: String, body: (Data?, String?), access: String?, upload: Bool
    ) async throws -> (Int, Data) {
        let url = absoluteURL(path)
        // 개발자 도구의 오류 주입 — 서버에 보내지 않고 가짜 응답을 돌려줘요.
        if let fault = DevFaults.take(path: url.path, method: method) {
            guard let response = DevFaults.response(for: fault, path: url.path, query: url.query) else {
                throw NetworkError(underlying: URLError(.notConnectedToInternet))
            }
            return response
        }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.httpBody = body.0
        if let contentType = body.1 { request.setValue(contentType, forHTTPHeaderField: "Content-Type") }
        if let access { request.setValue("Bearer \(access)", forHTTPHeaderField: "Authorization") }
        do {
            let (data, response) = try await (upload ? uploadSession : session).data(for: request)
            return ((response as? HTTPURLResponse)?.statusCode ?? 0, data)
        } catch {
            if error is CancellationError || (error as? URLError)?.code == .cancelled || Task.isCancelled {
                throw CancellationError()
            }
            throw NetworkError(underlying: error)
        }
    }

    private static func error(_ response: (Int, Data), decoder: JSONDecoder) -> APIError {
        let body = try? decoder.decode(ErrorBodyDTO.self, from: response.1)
        return APIError(
            status: response.0,
            code: body?.code ?? "HTTP_\(response.0)",
            message: body?.message ?? "요청을 처리할 수 없습니다"
        )
    }

    private static func multipartBody(_ parts: [Part], boundary: String) -> Data {
        var data = Data()
        func append(_ string: String) { data.append(Data(string.utf8)) }
        for part in parts {
            append("--\(boundary)\r\n")
            switch part {
            case .text(let name, let value):
                append("Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n")
                append(value)
            case .file(let name, let filename, let mime, let content):
                append("Content-Disposition: form-data; name=\"\(name)\"; filename=\"\(filename)\"\r\n")
                append("Content-Type: \(mime)\r\n\r\n")
                data.append(content)
            }
            append("\r\n")
        }
        append("--\(boundary)--\r\n")
        return data
    }
}

/// 서버 날짜·시간 문자열 읽기 (`2026-09-28T21:00:00+09:00`, 소수 초가 붙어도 돼요)
enum ServerDate {
    static func instant(_ text: String) -> Date? {
        // 소수 초는 3자리까지만 남겨요 (DB 가 마이크로초까지 줄 수 있어요).
        let normalized = text.replacingOccurrences(of: #"\.(\d{3})\d+"#, with: ".$1", options: .regularExpression)
        let fractional = ISO8601DateFormatter()
        fractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = fractional.date(from: normalized) { return date }
        let plain = ISO8601DateFormatter()
        plain.formatOptions = [.withInternetDateTime]
        if let date = plain.date(from: normalized) { return date }
        // 시간대가 빠져 있으면 서울 시각으로 봐요.
        let local = DateFormatter()
        local.locale = Locale(identifier: "en_US_POSIX")
        local.timeZone = TodayMission.seoul
        for format in ["yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss"] {
            local.dateFormat = format
            if let date = local.date(from: normalized) { return date }
        }
        return nil
    }

    /// `2026-10-12` → 그날 서울 0시
    static func day(_ text: String) -> Date? {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TodayMission.seoul
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.date(from: text)
    }

    /// 서울 날짜 → `2026-10-12`
    static func dayString(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TodayMission.seoul
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }

    /// `09:00` · `09:00:00` → 시·분
    static func time(_ text: String) -> DateComponents? {
        let parts = text.split(separator: ":").compactMap { Int($0) }
        guard parts.count >= 2 else { return nil }
        return DateComponents(hour: parts[0], minute: parts[1])
    }
}
