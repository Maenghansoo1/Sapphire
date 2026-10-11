import StoreKit
import SwiftUI

/// 17-3 앱 정보 (Figma 1:1697)
/// 앱 아이콘·버전·최신 여부, 공지사항, 문의하기, 앱 평가하기, 오픈소스 라이선스
struct AppInfoView: View {
    @Binding var path: [MainRoute]

    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    @Environment(\.requestReview) private var requestReview

    /// TODO: 서버 연결 시 App Store 최신 버전을 받아와요. 지금은 현재 버전을 최신으로 봐요.
    private let latestVersion = AppInfo.version
    private var isLatest: Bool { AppInfo.version == latestVersion }

    var body: some View {
        VStack(spacing: 0) {
            HNavBar("앱 정보", leading: .back { dismiss() })

            ScrollView {
                VStack(spacing: 0) {
                    app.padding(.top, 28)
                    versionStatus.padding(.top, 22)
                    rows.padding(.top, 22)
                }
            }
            .scrollBounceBehavior(.basedOnSize)

            Text("Made by \(AppInfo.teamName)\n© 2026 \(AppInfo.teamName). All rights reserved.")
                .font(.caption12)
                .plexLineHeight(1.6, size: 12)
                .multilineTextAlignment(.center)
                .foregroundStyle(.textSecondary)
                .padding(.top, 12)
                .padding(.bottom, 32)
        }
        .padding(.horizontal, HLayout.screenPaddingCompact)
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
    }

    /// 앱 아이콘 88(흰 바탕 · 테두리 · 모서리 20, 심볼 62) + 워드마크 26 + 한 줄 소개 13 + 버전 14
    private var app: some View {
        VStack(spacing: 10) {
            HLogoSymbol(size: 62)
                .frame(width: 88, height: 88)
                .background(.bg, in: RoundedRectangle(cornerRadius: HRadius.card, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: HRadius.card, style: .continuous)
                        .strokeBorder(.disabled, lineWidth: 1)
                )
            // Figma 17-3 의 노란 아포스트로피는 실수라서 다른 화면과 같은 accent 로 써요 (사용자 확인).
            HWordmark(size: 26)
            Text("Hi + We, 매일 건네는 우리의 인사")
                .font(.caption13)
                .foregroundStyle(.textSecondary)
            Text("버전 \(AppInfo.version)")
                .font(.plex(14))
                .foregroundStyle(.textSecondary)
        }
        .frame(maxWidth: .infinity)
    }

    /// surface · 모서리 16 · 안쪽 16
    private var versionStatus: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(isLatest ? "최신 버전을 사용 중이에요" : "새 버전이 있어요")
                    .font(.plex(15, .semibold))
                    .foregroundStyle(.ink)
                Text("최신 버전 \(latestVersion)")
                    .font(.caption12)
                    .foregroundStyle(.textSecondary)
            }
            Spacer()
            if isLatest {
                HIconView(icon: .check, size: 22)
            }
        }
        .padding(16)
        .background(.surface, in: RoundedRectangle(cornerRadius: HRadius.button, style: .continuous))
    }

    /// 목록 줄 54
    private var rows: some View {
        VStack(spacing: 0) {
            // TODO: 공지사항 주소를 받으면 Safari View 로 열어요.
            HListRow("공지사항", height: 54) {}
            HListRow("문의하기", value: AppInfo.contactEmail, height: 54) {
                if let url = URL(string: "mailto:\(AppInfo.contactEmail)") { openURL(url) }
            }
            HListRow("앱 평가하기", value: "App Store", height: 54) { requestReview() }
            HListRow("오픈소스 라이선스", height: 54) { path.append(.myPage(.licenses)) }
        }
    }
}

extension AppInfo {
    /// Figma 17-3 문의하기 주소
    static let contactEmail = "devleeekh@gmail.com"
    /// TODO: Figma 에 "[팀 이름]" 으로 비어 있어요. 이름을 받으면 바꿔요.
    static let teamName = "[팀 이름]"
}

/// 오픈소스 라이선스 — 앱에 넣은 글꼴
struct LicensesView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var openedLink: WebLink?

    private let items: [(name: String, license: String, url: String)] = [
        ("IBM Plex Sans KR", "SIL Open Font License 1.1", "https://github.com/IBM/plex"),
        ("Fredoka", "SIL Open Font License 1.1", "https://github.com/hafontia-zz/Fredoka"),
    ]

    var body: some View {
        VStack(spacing: 0) {
            HNavBar("오픈소스 라이선스", leading: .back { dismiss() })
            ScrollView {
                VStack(spacing: 0) {
                    ForEach(items, id: \.name) { item in
                        HListRow(item.name, value: item.license, height: 54) {
                            if let url = URL(string: item.url) { openedLink = WebLink(url: url) }
                        }
                    }
                    Text("SIL Open Font License 1.1 전문은 openfontlicense.org 에서 볼 수 있어요.")
                        .font(.caption12)
                        .foregroundStyle(.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.top, 16)
                }
            }
        }
        .padding(.horizontal, HLayout.screenPaddingCompact)
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
        .safariSheet($openedLink)
    }
}

#Preview {
    NavigationStack {
        AppInfoView(path: .constant([]))
    }
}
