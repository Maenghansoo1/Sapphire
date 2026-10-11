import SwiftUI

/// 아바타에 들어갈 내용: 캐릭터 또는 내 사진(원형 크롭)
enum AvatarContent: Hashable {
    case character(ProfileCharacter)
    case photo(URL)
}

/// Figma `Avatar` — 원형 surface 배경 + 캐릭터(또는 사진). 크기 24–44.
/// 멤버 줄처럼 겹쳐 놓을 때는 `ringed: true` 로 흰 테두리 2pt 를 둘러요.
struct HAvatar: View {
    let content: AvatarContent
    var size: CGFloat = 36
    var ringed: Bool = false

    var body: some View {
        ZStack {
            Circle().fill(.surface)
            switch content {
            case .character(let character):
                character.image
                    .resizable()
                    .scaledToFit()
                    // Figma: 34~38pt 아바타 안의 캐릭터 30pt → 약 0.85배
                    .frame(width: size * 0.85, height: size * 0.85)
            case .photo(let url):
                AsyncImage(url: url) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    Color.surfaceStrong
                }
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
