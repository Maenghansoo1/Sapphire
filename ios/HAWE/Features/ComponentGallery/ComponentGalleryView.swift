import SwiftUI

/// 1단계 결과 확인용: 디자인 토큰·에셋·공통 컴포넌트를 한 화면에서 봐요.
struct ComponentGalleryView: View {
    @State private var relationship: Relationship = .friend
    @State private var email = ""
    @State private var nickname = "하늘"
    @State private var notifyAll = true
    @State private var notifyFriendDone = false
    @State private var agreeAll = false
    @State private var agreeTerms = true

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 36) {
                HLogoLockup()
                    .padding(.top, 8)

                section("색상 토큰") { colors }
                section("타이포그래피") { typography }
                section("로고") { logos }
                section("캐릭터 7종") { characters }
                section("아이콘") { icons }
                section("관계 장식") { decorations }
                section("HButton") { buttons }
                section("HChip") { chips }
                section("HNavBar") { navBars }
                section("HAvatar") { avatars }
                section("HTextField") { textFields }
                section("HToggle") { toggles }
                section("HCheckbox") { checkboxes }
                section("HListRow") { listRows }
                section("MemberRow") { memberRows }
            }
            .padding(.horizontal, HLayout.screenPadding)
            .padding(.bottom, 48)
        }
        .background(.bg)
    }

    // MARK: - Sections

    private var colors: some View {
        let tokens: [(String, Color)] = [
            ("bg", .bg), ("surface", .surface), ("surfaceStrong", .surfaceStrong),
            ("line", .line), ("border", .border), ("ink", .ink),
            ("textSecondary", .textSecondary), ("textTertiary", .textTertiary),
            ("accent", .accent), ("accentPressed", .accentPressed), ("accentSubtle", .accentSubtle),
            ("danger", .danger), ("disabled", .disabled),
            ("controlBorder", .controlBorder), ("controlOff", .controlOff),
        ]
        return LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), spacing: 12)], alignment: .leading, spacing: 12) {
            ForEach(tokens, id: \.0) { name, color in
                VStack(alignment: .leading, spacing: 6) {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(color)
                        .frame(height: 44)
                        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(.line))
                    Text(name).font(.caption12).foregroundStyle(.textSecondary)
                }
            }
        }
    }

    private var typography: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("화면 제목 26 Bold").font(.screenTitle)
            Text("내비 제목 17 Bold").font(.navTitle)
            Text("섹션 제목 16 Bold").font(.sectionTitle)
            Text("입력 라벨 14 SemiBold").font(.fieldLabel).foregroundStyle(.textSecondary)
            Text("본문 15 Regular — 매일 하나의 미션, 우리만의 기록").font(.body15)
            Text("보조 13 Regular").font(.caption13).foregroundStyle(.textSecondary)
            Text("캡션 12 Regular").font(.caption12).foregroundStyle(.textSecondary)
            HStack(spacing: 12) {
                Text("Regular").font(.plex(15, .regular))
                Text("Medium").font(.plex(15, .medium))
                Text("SemiBold").font(.plex(15, .semibold))
                Text("Bold").font(.plex(15, .bold))
            }
        }
        .foregroundStyle(.ink)
    }

    private var logos: some View {
        HStack(alignment: .center, spacing: 24) {
            HLogoSymbol(size: 88)
            HLogoSymbol(size: 44)
            HLogoSymbol(size: 30)
            HWordmark(size: 26)
        }
    }

    private var characters: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 4), spacing: 12) {
            ForEach(ProfileCharacter.allCases) { character in
                VStack(spacing: 6) {
                    HAvatar(content: .character(character), size: 66)
                    Text(character.displayName).font(.plex(12, .medium)).foregroundStyle(.textSecondary)
                }
            }
        }
    }

    private var icons: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 7), spacing: 16) {
            ForEach(HIcon.allCases, id: \.self) { icon in
                HIconView(icon: icon)
            }
        }
    }

    private var decorations: some View {
        HStack(spacing: 16) {
            ForEach(Relationship.allCases) { relation in
                VStack(spacing: 6) {
                    RelationshipDecoration(relationship: relation, diameter: 28)
                        .padding(6)
                        .background(.surface, in: Circle())
                    Text(relation.displayName).font(.caption12).foregroundStyle(.textSecondary)
                }
            }
        }
    }

    private var buttons: some View {
        VStack(spacing: 12) {
            HButton("새 그룹 만들기", kind: .primary) {}
            HButton("초대 코드로 참여하기", kind: .secondary) {}
            HStack(spacing: 8) {
                HButton("취소", kind: .gray, height: 50) {}
                HButton("로그아웃", kind: .danger, height: 50) {}
            }
            HButton("탈퇴하기", kind: .disabled) {}
        }
    }

    private var chips: some View {
        FlowLayout(spacing: 8) {
            ForEach(Relationship.allCases) { relation in
                HChip(relation.displayName, isSelected: relationship == relation) {
                    relationship = relation
                }
            }
        }
    }

    private var navBars: some View {
        VStack(spacing: 8) {
            HNavBar("그룹 정보", leading: .back {})
            HNavBar(leading: .back {}) { HNavStep(text: "1 / 2") }
            HNavBar("알림 설정", leading: .close {})
        }
        .background(Color.surface.opacity(0.5))
    }

    private var avatars: some View {
        HStack(spacing: 12) {
            ForEach([24, 30, 36, 44] as [CGFloat], id: \.self) { size in
                HAvatar(content: .character(.mongsil), size: size)
            }
        }
    }

    private var textFields: some View {
        VStack(spacing: 18) {
            HTextField("이메일", placeholder: "example@email.com", text: $email)
            HTextField("닉네임", placeholder: "닉네임", text: $nickname, helper: "캐릭터와 이름은 나중에 바꿀 수 있어요", maxLength: 10)
        }
    }

    private var toggles: some View {
        VStack(spacing: 4) {
            Toggle(isOn: $notifyAll) { Text("전체 알림").font(.plex(16, .bold)) }
                .toggleStyle(.hawe)
            Toggle(isOn: $notifyFriendDone) { Text("친구가 미션을 완료했을 때").font(.body15) }
                .toggleStyle(.hawe)
                .disabled(!notifyAll)
        }
        .foregroundStyle(.ink)
    }

    private var checkboxes: some View {
        VStack(alignment: .leading, spacing: 0) {
            HCheckbox(isOn: $agreeAll) { Text("전체 동의").font(.plex(15, .bold)) }
            HCheckbox(isOn: $agreeTerms) { Text("[필수] 서비스 이용약관 동의").font(.plex(14)) }
        }
        .foregroundStyle(.ink)
    }

    private var listRows: some View {
        VStack(spacing: 0) {
            HListRow("주제 도착 시간", value: "오전 9:00") {}
            HListRow("알림", value: "켜짐") {}
            HListRow("오픈소스 라이선스") {}
        }
    }

    private var memberRows: some View {
        let members: [AvatarContent] = [.character(.gamja), .character(.kkotnim), .character(.mulbangul), .character(.pilleum), .character(.gomdol)]
        return VStack(alignment: .leading, spacing: 14) {
            ForEach(Relationship.allCases) { relation in
                HStack {
                    MemberRow(members: Array(members.prefix(relation == .lover ? 2 : 4)), relationship: relation)
                    Spacer()
                    Text(relation.displayName).font(.caption12).foregroundStyle(.textSecondary)
                }
            }
        }
    }

    // MARK: - Helpers

    private func section(_ title: String, @ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(title).font(.sectionTitle).foregroundStyle(.ink)
            content()
        }
    }
}

#Preview {
    ComponentGalleryView()
}
