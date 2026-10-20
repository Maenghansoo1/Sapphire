import SwiftUI

/// 03 초대 코드로 참여 (Figma 1:418)
/// 6자리 코드(영문 대문자+숫자). 6자리가 채워지면 그룹 미리보기, 정원 초과·잘못된 코드는 오류 표시.
/// 전달받은 초대 코드가 있으면 `initialCode` 로 자동 입력돼요.
struct JoinGroupView: View {
    var initialCode = ""
    var onJoined: () -> Void = {}

    @Environment(DataStore.self) private var store
    @Environment(AppSession.self) private var session
    @Environment(Services.self) private var services
    @State private var isSubmitting = false
    @State private var toast: String?

    @Environment(\.dismiss) private var dismiss
    @State private var model = JoinGroupModel()
    @FocusState private var isCodeFocused: Bool

    var body: some View {
        VStack(spacing: 0) {
            HNavBar(leading: .back { dismiss() })
                .padding(.horizontal, HLayout.screenPadding)

            HCTAScaffold(bottomInset: 40) {
                VStack(alignment: .leading, spacing: 14) {
                    title
                    codeCells
                    if let error = model.errorMessage {
                        Text(error)
                            .font(.caption13)
                            .foregroundStyle(.danger)
                    } else {
                        Text("링크로 받았다면 링크를 누르면 자동으로 입력돼요.")
                            .font(.caption13)
                            .foregroundStyle(.textSecondary)
                    }
                    if let preview = model.preview {
                        GroupInviteCard(preview: preview)
                            .transition(.opacity)
                    }
                }
                .padding(.top, 16)
                .animation(.default, value: model.preview)
            } cta: {
                HButton("참여하기", action: join)
                    .disabled(model.preview == nil || isSubmitting)
            }
        }
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
        .hToast($toast)
        // 6자리가 되면 서버에 미리보기를 물어봐요. 코드가 바뀌면 앞 요청은 취소돼요.
        .task(id: model.code) { await lookUp(model.code) }
        .onAppear {
            if model.code.isEmpty && !initialCode.isEmpty {
                model.code = JoinGroupModel.sanitize(initialCode)
            }
            isCodeFocused = model.code.count < JoinGroupModel.codeLength
        }
    }

    private var title: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("초대 코드 입력")
                .font(.screenTitle)
                .foregroundStyle(.ink)
            Text("친구에게 받은 6자리 코드를 입력해 주세요.")
                .font(.body15)
                .foregroundStyle(.textSecondary)
        }
    }

    /// 칸 6개 · 간격 8 · 높이 58 · 모서리 14. 채운 칸은 surface, 지금 입력할 칸은 흰 바탕 + ink 테두리 1.5
    private var codeCells: some View {
        let characters = Array(model.code)
        return HStack(spacing: 8) {
            ForEach(0..<JoinGroupModel.codeLength, id: \.self) { index in
                let isCurrent = isCodeFocused && index == characters.count
                let shape = RoundedRectangle(cornerRadius: HRadius.input, style: .continuous)
                ZStack {
                    shape.fill(isCurrent ? .bg : .surface)
                    if isCurrent {
                        shape.strokeBorder(.ink, lineWidth: 1.5)
                    }
                    if index < characters.count {
                        Text(String(characters[index]))
                            .font(.plex(24, .bold))
                            .foregroundStyle(.ink)
                    }
                }
                .frame(height: 58)
                .frame(maxWidth: .infinity)
            }
        }
        // 실제 입력은 보이지 않는 입력창이 받고, 칸을 누르면 키보드가 올라와요.
        .background {
            TextField("", text: $model.code)
                .keyboardType(.asciiCapable)
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                .focused($isCodeFocused)
                .opacity(0.02)
                .accessibilityLabel("초대 코드")
        }
        .contentShape(Rectangle())
        .onTapGesture { isCodeFocused = true }
        .onChange(of: model.code) { _, newValue in
            let sanitized = JoinGroupModel.sanitize(newValue)
            if sanitized != newValue {
                model.code = sanitized
                return
            }
            if sanitized.count == JoinGroupModel.codeLength {
                isCodeFocused = false
            }
        }
    }
}

extension JoinGroupView {
    private func lookUp(_ code: String) async {
        // 소문자 등은 onChange 가 고쳐서 다시 들어와요.
        guard code == JoinGroupModel.sanitize(code) else { return }
        model.begin(code)
        guard code.count == JoinGroupModel.codeLength else { return }
        do {
            let preview = try await services.groups.preview(code: code)
            model.finish(.success(preview), for: code)
        } catch is CancellationError {
        } catch {
            model.finish(.failure(InviteLookupError(error: error)), for: code)
        }
    }

    private func join() {
        guard model.preview != nil, !isSubmitting else { return }
        isSubmitting = true
        let code = model.code
        Task {
            do {
                let groupID = try await services.groups.join(code: code)
                // 상세를 못 받아도 참여는 끝났어요 — 홈이 목록을 다시 받아요.
                if let detail = try? await services.groups.detail(groupID: groupID, myUserID: session.account.userID) {
                    store.applyServerGroup(detail)
                }
                onJoined()
            } catch is CancellationError {
                isSubmitting = false
            } catch {
                toast = error.userMessage
                isSubmitting = false
            }
        }
    }
}

/// 참여할 그룹 미리보기 카드 — 테두리 line · 모서리 18 · 안쪽 18 · 아바타 32
private struct GroupInviteCard: View {
    let preview: GroupInvitePreview

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("참여할 그룹")
                .font(.plex(13, .semibold))
                .foregroundStyle(.textSecondary)
            VStack(alignment: .leading, spacing: 4) {
                Text(preview.name)
                    .font(.plex(18, .bold))
                    .foregroundStyle(.ink)
                    .lineLimit(1)
                Text("\(preview.relationship.displayName) · \(preview.memberCount) / \(preview.maxMembers)명 참여 중")
                    .font(.plex(14))
                    .foregroundStyle(.textSecondary)
            }
            MemberRow(members: preview.members, relationship: preview.relationship, avatarSize: 32)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(18)
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .strokeBorder(.line, lineWidth: 1)
        )
    }
}

/// 초대 코드로 찾은 그룹
struct GroupInvitePreview: Hashable {
    var name: String
    var relationship: Relationship
    var members: [AvatarContent]
    var memberCount: Int
    var maxMembers: Int

    init(name: String, relationship: Relationship, members: [AvatarContent], memberCount: Int? = nil, maxMembers: Int? = nil) {
        self.name = name
        self.relationship = relationship
        self.members = members
        self.memberCount = memberCount ?? members.count
        self.maxMembers = maxMembers ?? relationship.maxMembers
    }

    var isFull: Bool { memberCount >= maxMembers }
}

/// 03 입력 상태와 코드 조회 결과
@Observable
final class JoinGroupModel {
    static let codeLength = 6

    var code = ""
    private(set) var preview: GroupInvitePreview?
    private(set) var errorMessage: String?
    /// 서버에 묻는 중
    private(set) var isLoading = false

    /// 영문 대문자 + 숫자만, 6자리까지
    static func sanitize(_ text: String) -> String {
        String(text.uppercased().filter { $0.isASCII && ($0.isLetter || $0.isNumber) }.prefix(codeLength))
    }

    func begin(_ code: String) {
        preview = nil
        errorMessage = nil
        isLoading = code.count == Self.codeLength
    }

    /// 그사이 코드가 바뀌었으면 늦게 온 결과는 버려요.
    func finish(_ result: Result<GroupInvitePreview, InviteLookupError>, for code: String) {
        guard code == self.code else { return }
        isLoading = false
        switch result {
        case .success(let found): preview = found
        case .failure(let error): errorMessage = error.message
        }
    }
}

extension GroupInvitePreview {
    /// Figma 03 화면의 목업 데이터
    static let mock = GroupInvitePreview(
        name: "컴공 동기",
        relationship: .friend,
        members: [.character(.gamja), .character(.kkotnim), .character(.mulbangul), .character(.pilleum)]
    )
}

#Preview("빈 칸") {
    NavigationStack {
        JoinGroupView()
    }
    .environment(DataStore())
    .environment(AppSession(state: .signedIn))
    .environment(Services.preview)
}

#Preview("링크로 열림") {
    NavigationStack {
        JoinGroupView(initialCode: "k7q2px")
    }
    .environment(DataStore())
    .environment(AppSession(state: .signedIn))
    .environment(Services.preview)
}
