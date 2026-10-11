import PhotosUI
import SwiftUI

/// A3 회원가입 · 프로필 (Figma 1:256)
/// 캐릭터 7종 + 내 사진 중 선택(기본 몽실이), 닉네임 2–10자 입력 시 '시작하기' 활성화
struct SignupProfileView: View {
    /// 간편 로그인으로 바로 온 경우엔 A2 를 건너뛰어 "2 / 2" 를 숨겨요.
    var showsStep = true

    @Environment(\.dismiss) private var dismiss
    @Environment(AppSession.self) private var session

    @State private var selection: Selection = .character(.default)
    @State private var photo: UIImage?
    @State private var photoItem: PhotosPickerItem?
    @State private var nickname = ""

    enum Selection: Hashable {
        case character(ProfileCharacter)
        case photo
    }

    private var canStart: Bool {
        (2...10).contains(nickname.trimmingCharacters(in: .whitespaces).count)
    }

    var body: some View {
        VStack(spacing: 0) {
            HNavBar(leading: .back { dismiss() }) {
                if showsStep { HNavStep(text: "2 / 2") }
            }
            .padding(.horizontal, HLayout.screenPadding)

            HCTAScaffold {
                VStack(alignment: .leading, spacing: 18) {
                    title
                    preview
                    characterGrid
                    HTextField(
                        "닉네임",
                        placeholder: "하늘",
                        text: $nickname,
                        helper: "캐릭터와 이름은 나중에 바꿀 수 있어요",
                        maxLength: 10,
                        contentType: .nickname
                    )
                }
                .padding(.top, 8)
            } cta: {
                // TODO: 서버 연결 시 프로필 저장. 지금은 그룹이 없는 신규 사용자로 01 시작에 들어가요.
                HButton("시작하기") { session.signIn(hasGroups: false) }
                    .disabled(!canStart)
            }
        }
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
        .onChange(of: photoItem) { _, item in
            Task { await loadPhoto(item) }
        }
    }

    private var title: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("어떻게 불러드릴까요?")
                .font(.screenTitle)
                .foregroundStyle(.ink)
            Text("캐릭터를 고르고 이름을 정해주세요.")
                .font(.body15)
                .foregroundStyle(.textSecondary)
        }
    }

    private var preview: some View {
        HStack(spacing: 16) {
            ZStack {
                Circle().fill(.surface)
                switch selection {
                case .character(let character):
                    character.image.resizable().scaledToFit().frame(width: 70, height: 70)
                case .photo:
                    photoImage(size: 88)
                }
            }
            .frame(width: 88, height: 88)

            VStack(alignment: .leading, spacing: 4) {
                Text("선택한 캐릭터")
                    .font(.caption13)
                    .foregroundStyle(.textSecondary)
                Text(selectedName)
                    .font(.plex(20, .bold))
                    .foregroundStyle(.ink)
            }
        }
    }

    private var selectedName: String {
        switch selection {
        case .character(let character): character.displayName
        case .photo: "내 사진"
        }
    }

    /// 4열 · 원 66 · 가로 간격 26 · 세로 간격 12 (Figma Character Grid)
    /// 열 너비는 유연하게 둬서 좁은 화면(SE·mini)에서는 간격만 줄어들어요.
    private var characterGrid: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 26), count: 4), spacing: 12) {
            ForEach(ProfileCharacter.allCases) { character in
                Button {
                    selection = .character(character)
                } label: {
                    choiceCell(name: character.displayName, isSelected: selection == .character(character)) {
                        character.image.resizable().scaledToFit().frame(width: 50, height: 50)
                    }
                }
                .buttonStyle(.plain)
            }

            PhotosPicker(selection: $photoItem, matching: .images) {
                choiceCell(name: "내 사진", isSelected: selection == .photo, dashed: photo == nil) {
                    if photo != nil {
                        photoImage(size: 66)
                    } else {
                        HIconView(icon: .image, size: 22)
                    }
                }
            }
            .buttonStyle(.plain)
            .simultaneousGesture(TapGesture().onEnded {
                // 이미 고른 사진이 있으면 다시 고르기 전에 선택 상태로 바꿔 둬요.
                if photo != nil { selection = .photo }
            })
        }
    }

    private func choiceCell(name: String, isSelected: Bool, dashed: Bool = false, @ViewBuilder content: () -> some View) -> some View {
        VStack(spacing: 6) {
            ZStack {
                Circle().fill(isSelected ? .bg : .surface)
                content()
            }
            .frame(width: 66, height: 66)
            .clipShape(Circle())
            .overlay {
                if isSelected {
                    Circle().strokeBorder(.ink, lineWidth: 2.5)
                } else if dashed {
                    Circle().strokeBorder(.controlBorder, style: StrokeStyle(lineWidth: 2, dash: [4, 3]))
                }
            }
            Text(name)
                .font(.plex(12, isSelected ? .bold : .medium))
                .foregroundStyle(isSelected ? .ink : .textSecondary)
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    @ViewBuilder
    private func photoImage(size: CGFloat) -> some View {
        if let photo {
            Image(uiImage: photo)
                .resizable()
                .scaledToFill()
                .frame(width: size, height: size)
                .clipShape(Circle())
        }
    }

    private func loadPhoto(_ item: PhotosPickerItem?) async {
        guard let item,
              let data = try? await item.loadTransferable(type: Data.self),
              let image = UIImage(data: data) else { return }
        photo = image
        selection = .photo
    }
}

#Preview {
    NavigationStack {
        SignupProfileView()
    }
    .environment(AppSession())
}
