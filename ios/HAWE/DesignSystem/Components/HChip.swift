import SwiftUI

/// Figma `Chip` — Default(흰 바탕+테두리) / Selected(검정)
/// 높이 44 · 알약형 · 좌우 18 · 15pt · 너비 최소 80(글자에 맞춰 늘어남) · 글자 가운데 정렬
struct HChip: View {
    private let title: String
    private let isSelected: Bool
    private let action: () -> Void

    init(_ title: String, isSelected: Bool, action: @escaping () -> Void) {
        self.title = title
        self.isSelected = isSelected
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.plex(15, isSelected ? .semibold : .regular))
                .foregroundStyle(isSelected ? .onAccent : .ink)
                .lineLimit(1)
                .padding(.horizontal, 18)
                // Figma 는 너비 80 고정이라 '그저그런사이' 가 넘쳐요. 최소 80 + 글자에 맞춰 늘어나고, 글자는 가운데 정렬.
                .frame(minWidth: 80, minHeight: 44, alignment: .center)
                .background(isSelected ? .ink : .bg, in: Capsule())
                .overlay(Capsule().strokeBorder(isSelected ? .ink : .border, lineWidth: 1))
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

#Preview {
    @Previewable @State var selected: Relationship = .lover
    HStack(spacing: 8) {
        ForEach(Relationship.allCases.prefix(3)) { relation in
            HChip(relation.displayName, isSelected: selected == relation) { selected = relation }
        }
    }
    .padding()
}
