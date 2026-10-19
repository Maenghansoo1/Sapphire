import SwiftUI

/// 알림 목록 — 콕·댓글·멘션·반응·미션 도착. 누르면 읽음으로 바꾸고 그 화면으로 가요.
struct NotificationsView: View {
    @Binding var path: [MainRoute]
    @Environment(DataStore.self) private var store
    @Environment(Services.self) private var services
    @Environment(\.dismiss) private var dismiss

    private enum Load {
        case loading, failed
        case loaded([AppNotification])
    }

    @State private var load: Load = .loading
    @State private var retry = 0
    @State private var toast: String?

    private var items: [AppNotification] {
        if case .loaded(let items) = load { items } else { [] }
    }

    var body: some View {
        VStack(spacing: 0) {
            HNavBar("알림", leading: .back { dismiss() }) {
                if items.contains(where: { !$0.isRead }) {
                    Button("모두 읽음", action: markAllRead)
                        .font(.plex(14, .semibold))
                        .foregroundStyle(.ink)
                        .buttonStyle(.plain)
                }
            }
            content
        }
        .padding(.horizontal, HLayout.screenPaddingCompact)
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
        .hToast($toast)
        .task(id: retry) { await reload() }
    }

    @ViewBuilder
    private var content: some View {
        switch load {
        case .loading:
            Color.bg.frame(maxWidth: .infinity, maxHeight: .infinity)
        case .failed:
            VStack(spacing: 16) {
                Text("알림을 불러오지 못했어요")
                    .font(.sectionTitle)
                    .foregroundStyle(.ink)
                HButton("다시 시도") { retry += 1 }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        case .loaded(let items):
            if items.isEmpty {
                empty
            } else {
                list(items)
            }
        }
    }

    private var empty: some View {
        VStack(spacing: 0) {
            HIconView(icon: .bell, size: 28, color: .textTertiary)
                .frame(width: 64, height: 64)
                .background(.surface, in: Circle())
            Text("아직 받은 알림이 없어요")
                .font(.sectionTitle)
                .foregroundStyle(.ink)
                .padding(.top, 16)
            Text("콕, 댓글, 반응과 새 미션 소식이 여기에 모여요")
                .font(.caption13)
                .foregroundStyle(.textSecondary)
                .padding(.top, 6)
        }
        .padding(.bottom, 80)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func list(_ items: [AppNotification]) -> some View {
        let now = AppClock.now()
        return ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                ForEach(NotificationFormat.sections(items, now: now), id: \.0) { section, rows in
                    Text(section.rawValue)
                        .font(.plex(13, .semibold))
                        .foregroundStyle(.textSecondary)
                        .padding(.top, 16)
                        .padding(.bottom, 4)
                    ForEach(rows) { item in
                        NotificationRow(notification: item, now: now) { open(item) }
                    }
                }
            }
            .padding(.bottom, 24)
        }
        .scrollBounceBehavior(.basedOnSize)
    }

    private func reload() async {
        do {
            let loaded = try await services.notifications.list()
            load = .loaded(loaded)
        } catch is CancellationError {
        } catch {
            if case .loaded = load {
                toast = error.userMessage
            } else {
                load = .failed
            }
        }
    }

    private func setItems(_ items: [AppNotification]) {
        load = .loaded(items)
    }

    private func open(_ notification: AppNotification) {
        if !notification.isRead {
            setItems(items.map { $0.id == notification.id ? readCopy($0) : $0 })
            Task { try? await services.notifications.markRead(id: notification.id) }
        }
        let canOpenResults = notification.missionID.map { missionID in
            store.todayMission(notification.groupID, missionID: missionID)?.didSubmit
                ?? store.calendarRecord(groupID: notification.groupID, missionID: missionID)?.didSubmit
                ?? false
        } ?? false
        switch NotificationFormat.target(notification, groupExists: store.group(notification.groupID) != nil, canOpenResults: canOpenResults) {
        case .missing:
            toast = "더 이상 볼 수 없는 그룹이에요"
        case .mission(let groupID):
            path.append(.mission(groupID: groupID))
        case .results(let groupID, let missionID):
            path.append(.mission(groupID: groupID))
            path.append(.results(groupID: groupID, missionID: missionID))
        }
    }

    private func markAllRead() {
        let before = items
        setItems(items.map(readCopy))
        Task {
            do {
                try await services.notifications.markAllRead()
            } catch is CancellationError {
            } catch {
                setItems(before)
                toast = error.userMessage
            }
        }
    }

    private func readCopy(_ item: AppNotification) -> AppNotification {
        var copy = item
        copy.isRead = true
        return copy
    }
}

private struct NotificationRow: View {
    let notification: AppNotification
    let now: Date
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(alignment: .top, spacing: 12) {
                leading
                VStack(alignment: .leading, spacing: 4) {
                    Text(NotificationFormat.text(notification))
                        .font(.plex(15, notification.isRead ? .regular : .semibold))
                        .foregroundStyle(notification.isRead ? Color.textBody : Color.ink)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    Text("\(notification.groupName) · \(NotificationFormat.relativeText(notification.createdAt, now: now))")
                        .font(.caption12)
                        .foregroundStyle(.textSecondary)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if !notification.isRead {
                    Circle().fill(.accent).frame(width: 8, height: 8).padding(.top, 7)
                }
            }
            .padding(.vertical, 14)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .overlay(alignment: .bottom) {
            Rectangle().fill(.line).frame(height: 1)
        }
        .accessibilityLabel("\(notification.isRead ? "" : "읽지 않음, ")\(NotificationFormat.text(notification)), \(notification.groupName)")
    }

    @ViewBuilder
    private var leading: some View {
        if let from = notification.from {
            HAvatar(content: from.avatar, size: 40)
                .overlay(alignment: .bottomTrailing) {
                    HIconView(icon: NotificationFormat.badgeIcon(notification.type), size: 12, color: .accentPressed)
                        .frame(width: 20, height: 20)
                        .background(.bg, in: Circle())
                        .overlay(Circle().strokeBorder(.line, lineWidth: 1))
                        .offset(x: 2, y: 2)
                }
                .frame(width: 42, height: 42, alignment: .topLeading)
        } else {
            HIconView(icon: .bell, size: 20, color: .accentPressed)
                .frame(width: 40, height: 40)
                .background(.accentSubtle, in: Circle())
                .frame(width: 42, height: 42, alignment: .topLeading)
        }
    }
}

#Preview {
    NavigationStack {
        NotificationsView(path: .constant([]))
    }
    .environment(DataStore.preview())
    .environment(Services.preview)
}
