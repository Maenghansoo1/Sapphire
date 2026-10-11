import SwiftUI

/// 아바타에 들어갈 내용: 캐릭터 또는 내 사진(원형 크롭)
enum AvatarContent: Hashable {
    case character(ProfileCharacter)
    /// 서버에 올라간 사진
    case photo(URL)
    /// 방금 앨범에서 고른 사진 (아직 올리기 전)
    case image(UIImage)

    var isPhoto: Bool {
        if case .character = self { false } else { true }
    }
}

/// Figma `Avatar` — 원형 surface 배경 + 캐릭터(또는 사진). 크기 24–44.
/// 멤버 줄처럼 겹쳐 놓을 때는 `ringed: true` 로 흰 테두리 2pt 를 둘러요.
struct HAvatar: View {
    let content: AvatarContent
    var size: CGFloat = 36
    var ringed: Bool = false
    /// Figma: 34~38pt 아바타 안의 캐릭터 30pt → 약 0.85배. 큰 미리보기(80·88)는 0.8배.
    var characterScale: CGFloat = 0.85

    var body: some View {
        ZStack {
            Circle().fill(.surface)
            switch content {
            case .character(let character):
                character.image
                    .resizable()
                    .scaledToFit()
                    .frame(width: size * characterScale, height: size * characterScale)
            case .photo(let url):
                AsyncImage(url: url) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    Color.surfaceStrong
                }
                .frame(width: size, height: size)
                .clipShape(Circle())
            case .image(let image):
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
                    .frame(width: size, height: size)
                    .clipShape(Circle())
            }
        }
        .frame(width: size, height: size)
        .overlay {
            if ringed {
                Circle().strokeBorder(.bg, lineWidth: 2)
            }
        }
        .accessibilityHidden(true)
    }
}

#Preview {
    HStack(spacing: 12) {
        HAvatar(content: .character(.mongsil), size: 24)
        HAvatar(content: .character(.gomdol), size: 30)
        HAvatar(content: .character(.kkotnim), size: 36)
        HAvatar(content: .character(.gamja), size: 44)
        HAvatar(content: .photo(URL(string: "https://picsum.photos/100")!), size: 44)
    }
    .padding()
}
