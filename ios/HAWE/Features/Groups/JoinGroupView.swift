import SwiftUI

/// 03 초대 코드로 참여 (Figma 1:418)
/// 6자리 코드(영문 대문자+숫자). 6자리가 채워지면 그룹 미리보기, 정원 초과·잘못된 코드는 오류 표시.
/// 초대 링크(Universal Link)로 열면 `initialCode` 로 코드가 자동 입력돼요.
struct JoinGroupView: View {
    var initialCode = ""
    var onJoined: () -> Void = {}

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
                // TODO: 서버 연결 시 그룹 참여 요청
                HButton("참여하기") { onJoined() }
                    .disabled(model.preview == nil)
            }
        }
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
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
            model.lookUp()
            if sanitized.count == JoinGroupModel.codeLength {
                isCodeFocused = false
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
                Text("\(preview.relationship.displayName) · \(preview.members.count) / \(preview.relationship.maxMembers)명 참여 중")
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

    var isFull: Bool { members.count >= relationship.maxMembers }
}

/// 03 입력 상태와 코드 조회 (지금은 목업)
@Observable
final class JoinGroupModel {
    static let codeLength = 6

    var code = ""
    private(set) var preview: GroupInvitePreview?
    private(set) var errorMessage: String?

    /// 영문 대문자 + 숫자만, 6자리까지
    static func sanitize(_ text: String) -> String {
        String(text.uppercased().filter { $0.isASCII && ($0.isLetter || $0.isNumber) }.prefix(codeLength))
    }

    /// TODO: 서버 연결 시 실제 조회. 목업 규칙:
    /// `000000` → 잘못된 코드, `FULL00` → 정원 초과, 나머지 6자리 → '컴공 동기' 미리보기
    func lookUp() {
        preview = nil
        errorMessage = nil
        guard code.count == Self.codeLength else { return }

        switch code {
        case "000000":
            errorMessage = "초대 코드를 찾을 수 없어요. 다시 확인해 주세요."
        case "FULL00":
            errorMessage = "이 그룹은 정원이 다 찼어요."
        default:
            let found = GroupInvitePreview.mock
            if found.isFull {
                errorMessage = "이 그룹은 정원이 다 찼어요."
            } else {
                preview = found
            }
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
}

#Preview("링크로 열림") {
    NavigationStack {
        JoinGroupView(initialCode: "k7q2p8")
    }
}
