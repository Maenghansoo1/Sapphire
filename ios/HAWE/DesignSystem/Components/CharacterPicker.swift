import PhotosUI
import SwiftUI

/// 캐릭터 7종 + 내 사진 고르기 (A3 회원가입 · 프로필, 16-1 그룹 내 프로필)
/// 4열 격자. 고른 칸은 흰 바탕 + ink 테두리 2.5, 이름은 12 Bold ink / 나머지는 surface + 12 Medium textSecondary.
/// '내 사진'은 점선 칸이고, 누르면 앨범이 열려요.
struct CharacterPicker: View {
    @Binding var selection: AvatarContent
    /// A3: 66 · 50 · 가로 26 · 세로 12 / 16-1: 64 · 48 · 가로 22 · 세로 10
    var cellSize: CGFloat = 66
    var characterSize: CGFloat = 50
    var columnSpacing: CGFloat = 26
    var rowSpacing: CGFloat = 12

    @State private var photoItem: PhotosPickerItem?
    /// 마지막으로 고른 사진 — 캐릭터로 바꿨다가 다시 '내 사진'을 누르면 앨범을 열어요.
    @State private var lastPhoto: AvatarContent?

    var body: some View {
        // 열 너비는 유연하게 둬서 좁은 화면(SE·mini)에서는 간격만 줄어들어요.
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: columnSpacing), count: 4), spacing: rowSpacing) {
            ForEach(ProfileCharacter.allCases) { character in
                Button {
                    selection = .character(character)
                } label: {
                    cell(name: character.displayName, isSelected: selection == .character(character)) {
                        character.image.resizable().scaledToFit()
                            .frame(width: characterSize, height: characterSize)
                    }
                }
                .buttonStyle(.plain)
            }

            PhotosPicker(selection: $photoItem, matching: .images) {
                let photo = selection.isPhoto ? selection : lastPhoto
                cell(name: "내 사진", isSelected: selection.isPhoto, dashed: photo == nil) {
                    if let photo {
                        HAvatar(content: photo, size: cellSize)
                    } else {
                        HIconView(icon: .image, size: 22)
                    }
                }
            }
            .buttonStyle(.plain)
        }
        .onAppear {
            if selection.isPhoto { lastPhoto = selection }
        }
        .onChange(of: photoItem) { _, item in
            Task { await loadPhoto(item) }
        }
    }

    private func cell(name: String, isSelected: Bool, dashed: Bool = false, @ViewBuilder content: () -> some View) -> some View {
        VStack(spacing: 6) {
            ZStack {
                Circle().fill(isSelected ? .bg : .surface)
                content()
            }
            .frame(width: cellSize, height: cellSize)
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

    private func loadPhoto(_ item: PhotosPickerItem?) async {
        guard let item,
              let data = try? await item.loadTransferable(type: Data.self),
              let image = UIImage(data: data) else { return }
        let photo = AvatarContent.image(image)
        lastPhoto = photo
        selection = photo
    }
}

extension AvatarContent {
    /// 고른 캐릭터 이름, 사진이면 '내 사진'
    var choiceName: String {
        if case .character(let character) = self { character.displayName } else { "내 사진" }
    }
}

#Preview {
    @Previewable @State var selection: AvatarContent = .character(.mongsil)
    CharacterPicker(selection: $selection)
        .padding(24)
}
