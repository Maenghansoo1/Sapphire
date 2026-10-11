import SwiftUI

/// 하단 고정 CTA 화면 틀 (CLAUDE.md §3-4)
/// 내용은 위쪽만 스크롤되고, CTA 는 화면 아래 끝에서 `bottomInset`(기본 36, Figma 프레임 pb) 위에 고정돼요.
/// 키보드가 올라오면 CTA 도 키보드 위로 올라와요.
struct HCTAScaffold<Content: View, CTA: View>: View {
    private let horizontalPadding: CGFloat
    private let bottomInset: CGFloat
    private let content: Content
    private let cta: CTA

    init(
        horizontalPadding: CGFloat = HLayout.screenPadding,
        bottomInset: CGFloat = HLayout.ctaBottomInset,
        @ViewBuilder content: () -> Content,
        @ViewBuilder cta: () -> CTA
    ) {
        self.horizontalPadding = horizontalPadding
        self.bottomInset = bottomInset
        self.content = content()
        self.cta = cta()
    }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                content
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, horizontalPadding)
                    .padding(.bottom, 24)
            }
            .scrollBounceBehavior(.basedOnSize)
            .scrollDismissesKeyboard(.interactively)

            cta
                .padding(.horizontal, horizontalPadding)
                .padding(.top, 12)
                .padding(.bottom, bottomInset)
        }
        .ignoresSafeArea(.container, edges: .bottom)
    }
}

#Preview {
    HCTAScaffold {
        VStack(alignment: .leading, spacing: 18) {
            Text("계정을 만들어요").font(.screenTitle)
            ForEach(0..<12) { Text("내용 \($0)").font(.body15) }
        }
    } cta: {
        HButton("다음") {}
    }
}
