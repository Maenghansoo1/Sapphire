import SwiftUI

/// 04 홈 · 내 그룹 목록 (Figma 1:461)
struct HomeView: View {
    let groups: [GroupSummary]

    var body: some View {
        VStack(spacing: 0) {
            header
                .padding(.horizontal, HLayout.screenPaddingCompact)

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text("\(Self.todayText) · 그룹 \(groups.count)개")
                        .font(.plex(14))
                        .foregroundStyle(.textSecondary)
                        .padding(.top, 6)
                        .padding(.bottom, 20)

                    LazyVStack(spacing: 12) {
                        ForEach(groups) { group in
                            // TODO: 05 그룹 · 오늘의 미션 연결
                            GroupCard(group: group)
                        }
                    }
                }
                .padding(.horizontal, HLayout.screenPaddingCompact)
                .padding(.bottom, 24)
            }
        }
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
    }

    private var header: some View {
        HStack {
            Text("내 그룹")
                .font(.screenTitle)
                .foregroundStyle(.ink)
            Spacer()
            HStack(spacing: 16) {
                // TODO: 02 그룹 만들기 / 17 마이페이지 연결
                headerButton(.plus, size: 24, label: "그룹 추가") {}
                headerButton(.person, size: 22, label: "마이페이지") {}
            }
        }
        .frame(height: HLayout.headerHeight)
    }

    /// 아이콘은 Figma 크기 그대로 두고, 눌리는 영역만 44pt 로 넓혀요.
    private func headerButton(_ icon: HIcon, size: CGFloat, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HIconView(icon: icon, size: size)
                .contentShape(Rectangle().inset(by: -(HLayout.minTouch - size) / 2))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    private static var todayText: String {
        Date.now.formatted(.dateTime.month(.defaultDigits).day().weekday(.wide).locale(Locale(identifier: "ko_KR")))
    }
}

/// 홈 그룹 카드 — 테두리 line · 모서리 20 · 안쪽 18 · 간격 14
struct GroupCard: View {
    let group: GroupSummary

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            VStack(alignment: .leading, spacing: 4) {
                Text(group.name)
                    .font(.plex(18, .bold))
                    .foregroundStyle(.ink)
                    .lineLimit(1)
                Text(subtitle)
                    .font(.caption13)
                    .foregroundStyle(.textSecondary)
            }

            MemberRow(members: group.members, relationship: group.relationship)

            VStack(alignment: .leading, spacing: 6) {
                Text("오늘의 미션 · \(group.missionType.displayName)")
                    .font(.plex(12, .semibold))
                    .foregroundStyle(.textSecondary)
                Text(group.topic)
                    .font(.plex(15, .semibold))
                    .foregroundStyle(.ink)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .background(.surface, in: RoundedRectangle(cornerRadius: HRadius.input, style: .continuous))

            HStack {
                Text("\(group.submittedCount) / \(group.members.count)명 완료")
                    .font(.caption13)
                    .foregroundStyle(.textSecondary)
                Spacer()
                statusBadge
            }
        }
        .padding(18)
        .overlay(
            RoundedRectangle(cornerRadius: HRadius.card, style: .continuous)
                .strokeBorder(.line, lineWidth: 1)
        )
    }

    /// 관계 · 인원 (연인은 '함께한 지 N일' 추가)
    private var subtitle: String {
        var parts = [group.relationship.displayName, "\(group.members.count)명"]
        if let days = group.daysTogether() {
            parts.append("함께한 지 \(days)일")
        }
        return parts.joined(separator: " · ")
    }

    /// 내 미션 남음 = accent / 열어보기 = surface
    private var statusBadge: some View {
        let remaining = !group.didSubmit
        return Text(remaining ? "내 미션 남음" : "열어보기")
            .font(.plex(13, .semibold))
            .foregroundStyle(remaining ? .onAccent : .ink)
            .padding(.horizontal, 12)
            .frame(height: 28)
            .background(remaining ? .accent : .surface, in: Capsule())
    }
}

#Preview {
    NavigationStack {
        HomeView(groups: GroupSummary.mock)
    }
}
