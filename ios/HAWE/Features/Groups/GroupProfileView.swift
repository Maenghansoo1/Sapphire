import SwiftUI

/// 16-1 그룹 내 프로필 (Figma 1:1503)
/// '기본 프로필 사용'을 끄면 이 그룹에서만 쓸 캐릭터/사진·닉네임을 정해요. 다른 그룹에는 영향이 없어요.
struct GroupProfileView: View {
    let groupName: String
    @Binding var member: GroupMember

    @Environment(\.dismiss) private var dismiss

    @State private var useDefault: Bool
    @State private var avatar: AvatarContent
    @State private var nickname: String
    @State private var toast: String?

    init(groupName: String, member: Binding<GroupMember>) {
        self.groupName = groupName
        self._member = member
        let current = member.wrappedValue
        self._useDefault = State(initialValue: current.groupProfile == nil)
        self._avatar = State(initialValue: current.displayed.avatar)
        self._nickname = State(initialValue: current.displayed.nickname)
    }

    private var defaultProfile: Profile { member.defaultProfile }

    /// 멤버들에게 보일 모습
    private var preview: Profile {
        useDefault ? defaultProfile : Profile(avatar: avatar, nickname: nickname)
    }

    /// 닉네임 2–10자 (A3 와 같은 규칙)
    private var canSave: Bool {
        useDefault || (2...10).contains(nickname.trimmingCharacters(in: .whitespaces).count)
    }

    var body: some View {
        VStack(spacing: 0) {
            HNavBar("그룹 내 프로필", leading: .back { dismiss() })
                .padding(.horizontal, HLayout.screenPadding)

            HCTAScaffold {
                VStack(alignment: .leading, spacing: 18) {
                    Text("'\(groupName)' 그룹에서만 이렇게 보여요. 다른 그룹에는 영향이 없어요.")
                        .font(.plex(14))
                        .plexLineHeight(1.5, size: 14)
                        .foregroundStyle(.textSecondary)

                    useDefaultCard
                    previewRow

                    if !useDefault {
                        CharacterPicker(selection: $avatar, cellSize: 64, characterSize: 48, columnSpacing: 22, rowSpacing: 10)
                        HTextField(
                            "그룹 내 닉네임",
                            placeholder: "닉네임을 입력해 주세요",
                            text: $nickname,
                            maxLength: 10,
                            textSize: 17
                        )
                    }
                }
                .padding(.top, 8)
                .animation(.snappy(duration: 0.25), value: useDefault)
            } cta: {
                // 그룹 내 프로필 API(group_members 의 *_override)가 생기면 save() 로 저장해요.
                HButton("저장") { toast = ComingSoon.message }
                    .disabled(!canSave)
            }
        }
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
        .hToast($toast)
    }

    /// 기본 프로필 사용 — surface · 모서리 16 · 좌우 16 · 위아래 14
    private var useDefaultCard: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text("기본 프로필 사용")
                    .font(.plex(15, .semibold))
                    .foregroundStyle(.ink)
                Text("마이페이지의 프로필(\(defaultProfile.avatar.choiceName) · \(defaultProfile.nickname))을 그대로 써요")
                    .font(.caption12)
                    .foregroundStyle(.textSecondary)
            }
            Spacer(minLength: 0)
            HToggle(isOn: $useDefault, accessibilityLabel: "기본 프로필 사용")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(.surface, in: RoundedRectangle(cornerRadius: HRadius.button, style: .continuous))
    }

    /// 원 80 · 캐릭터 64 + "그룹 멤버에게 보이는 모습" 13 / 닉네임 20 Bold
    private var previewRow: some View {
        HStack(spacing: 16) {
            HAvatar(content: preview.avatar, size: 80, characterScale: 0.8)
            VStack(alignment: .leading, spacing: 4) {
                Text("그룹 멤버에게 보이는 모습")
                    .font(.caption13)
                    .foregroundStyle(.textSecondary)
                Text(preview.nickname.isEmpty ? " " : preview.nickname)
                    .font(.plex(20, .bold))
                    .foregroundStyle(.ink)
                    .lineLimit(1)
            }
        }
    }

    private func save() {
        member.groupProfile = useDefault
            ? nil
            : Profile(avatar: avatar, nickname: nickname.trimmingCharacters(in: .whitespaces))
        dismiss()
    }
}

#Preview("그룹 내 프로필 사용 중") {
    @Previewable @State var member = GroupDetail.mock.members[0]
    NavigationStack {
        GroupProfileView(groupName: "컴공 동기", member: $member)
    }
}

#Preview("기본 프로필 사용 중") {
    @Previewable @State var member = GroupMember(id: LocalID.next(), defaultProfile: .mockMine, isMe: true)
    NavigationStack {
        GroupProfileView(groupName: "우리 둘", member: $member)
    }
}
