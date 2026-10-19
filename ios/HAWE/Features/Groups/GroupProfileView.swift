import SwiftUI

/// 16-1 그룹 내 프로필 (Figma 1:1503)
/// '기본 프로필 사용'을 끄면 이 그룹에서만 쓸 캐릭터/사진·닉네임을 정해요. 다른 그룹에는 영향이 없어요.
/// 들어올 때 서버에서 멤버 정보를 다시 받고, 저장하면 서버에 올린 뒤 그룹 정보를 다시 받아요.
struct GroupProfileView: View {
    let groupID: Int
    /// 그룹에서 빠졌을 때 — 홈으로 가요.
    var onLeft: () -> Void = {}

    @Environment(\.dismiss) private var dismiss
    @Environment(DataStore.self) private var store
    @Environment(AppSession.self) private var session
    @Environment(Services.self) private var services

    @State private var editing: GroupProfileState?
    @State private var isSaving = false
    @State private var toast: String?

    private var group: GroupDetail? { store.group(groupID) }

    var body: some View {
        VStack(spacing: 0) {
            HNavBar("그룹 내 프로필", leading: .back { dismiss() })
                .padding(.horizontal, HLayout.screenPadding)

            if let group, let editing {
                form(group: group, editing: editing)
            } else {
                Color.bg.frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
        .hToast($toast)
        .task { await load() }
    }

    private func form(group: GroupDetail, editing: GroupProfileState) -> some View {
        let binding = Binding<GroupProfileState>(get: { self.editing ?? editing }, set: { self.editing = $0 })
        let reselect = editing.needsPhotoReselect
        return HCTAScaffold {
            VStack(alignment: .leading, spacing: 18) {
                Text("'\(group.name)' 그룹에서만 이렇게 보여요. 다른 그룹에는 영향이 없어요.")
                    .font(.plex(14))
                    .plexLineHeight(1.5, size: 14)
                    .foregroundStyle(.textSecondary)

                useDefaultCard(binding)
                previewRow(editing.preview)

                if !editing.useDefault {
                    CharacterPicker(selection: binding.avatar, cellSize: 64, characterSize: 48, columnSpacing: 22, rowSpacing: 10)
                    HTextField(
                        "그룹 내 닉네임",
                        placeholder: "닉네임을 입력해 주세요",
                        text: binding.nickname,
                        maxLength: 10,
                        textSize: 17
                    )
                    if reselect {
                        Text("사진 프로필의 닉네임을 바꾸려면 사진을 다시 골라 주세요")
                            .font(.caption13)
                            .foregroundStyle(.danger)
                    }
                }
            }
            .padding(.top, 8)
            .animation(.snappy(duration: 0.25), value: editing.useDefault)
        } cta: {
            HButton(isSaving ? "저장 중" : "저장", action: save)
                .disabled(!editing.canSave || reselect || isSaving || editing.profileToSave == editing.originalProfile)
        }
    }

    /// 기본 프로필 사용 — surface · 모서리 16 · 좌우 16 · 위아래 14
    private func useDefaultCard(_ editing: Binding<GroupProfileState>) -> some View {
        let profile = editing.wrappedValue.defaultProfile
        return HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text("기본 프로필 사용")
                    .font(.plex(15, .semibold))
                    .foregroundStyle(.ink)
                Text("마이페이지의 프로필(\(profile.avatar.choiceName) · \(profile.nickname))을 그대로 써요")
                    .font(.caption12)
                    .foregroundStyle(.textSecondary)
            }
            Spacer(minLength: 0)
            HToggle(isOn: editing.useDefault, accessibilityLabel: "기본 프로필 사용")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(.surface, in: RoundedRectangle(cornerRadius: HRadius.button, style: .continuous))
    }

    /// 원 80 · 캐릭터 64 + "그룹 멤버에게 보이는 모습" 13 / 닉네임 20 Bold
    private func previewRow(_ preview: Profile) -> some View {
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

    private func load() async {
        do {
            let detail = try await services.groups.detail(groupID: groupID, myUserID: session.account.userID)
            store.applyServerGroup(detail)
            guard let me = detail.me else {
                onLeft()
                return
            }
            if editing == nil { editing = GroupProfileState(member: me, myDefault: session.account.profile) }
        } catch is CancellationError {
        } catch {
            if error.apiStatus == 403 || error.apiStatus == 404 {
                store.leave(groupID)
                onLeft()
            } else {
                toast = error.userMessage
            }
        }
    }

    private func save() {
        guard let editing, !isSaving else { return }
        isSaving = true
        Task {
            defer { isSaving = false }
            do {
                if let profile = editing.profileToSave {
                    try await services.groups.saveGroupProfile(groupID: groupID, avatar: profile.avatar, nickname: profile.nickname)
                } else {
                    try await services.groups.clearGroupProfile(groupID: groupID)
                }
                store.applyServerGroup(try await services.groups.detail(groupID: groupID, myUserID: session.account.userID))
                dismiss()
            } catch is CancellationError {
            } catch is AvatarPhotoError {
                toast = "사진을 불러오지 못했어요"
            } catch {
                toast = error.userMessage
            }
        }
    }
}

/// 16-1 입력 상태 (Android `GroupProfileState`)
struct GroupProfileState: Equatable {
    var defaultProfile: Profile
    var useDefault: Bool
    var avatar: AvatarContent
    var nickname: String {
        didSet { if nickname.count > 10 { nickname = String(nickname.prefix(10)) } }
    }
    /// 서버에 저장된 그룹 내 프로필 — 없으면 기본 프로필을 쓰는 중
    var originalProfile: Profile?

    init(defaultProfile: Profile, useDefault: Bool, avatar: AvatarContent, nickname: String, originalProfile: Profile?) {
        self.defaultProfile = defaultProfile
        self.useDefault = useDefault
        self.avatar = avatar
        self.nickname = nickname
        self.originalProfile = originalProfile
    }

    init(member: GroupMember, myDefault: Profile) {
        self.init(defaultProfile: myDefault, useDefault: member.groupProfile == nil, avatar: member.displayed.avatar,
                  nickname: member.displayed.nickname, originalProfile: member.groupProfile)
    }

    var preview: Profile { useDefault ? defaultProfile : Profile(avatar: avatar, nickname: nickname.isEmpty ? " " : nickname) }

    /// 닉네임 2–10자 (A3 와 같은 규칙)
    var canSave: Bool { useDefault || (2...10).contains(nickname.trimmingCharacters(in: .whitespaces).count) }

    var profileToSave: Profile? {
        useDefault ? nil : Profile(avatar: avatar, nickname: nickname.trimmingCharacters(in: .whitespaces))
    }

    /// 서버의 사진 프로필은 사진과 닉네임을 같이 올려야 해서, 닉네임만 바꾸려면 사진을 다시 골라야 해요.
    var needsPhotoReselect: Bool {
        guard !useDefault, case .photo = avatar else { return false }
        return originalProfile == nil || nickname.trimmingCharacters(in: .whitespaces) != originalProfile?.nickname
    }
}

#Preview {
    let store = DataStore.preview()
    NavigationStack {
        GroupProfileView(groupID: store.groups[0].id)
    }
    .environment(store)
    .environment(AppSession(state: .signedIn))
    .environment(Services.preview)
}
