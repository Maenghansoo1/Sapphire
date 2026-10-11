import SwiftUI

/// 02 그룹 만들기 (Figma 1:338) / 02-1 연인 선택 (Figma 1:375)
/// 그룹 이름(최대 16자), 관계 5종, 관계별 장식 미리보기.
/// 연인을 고르면 '우리의 1일' 날짜 입력이 나타나고 최대 인원이 2명이 돼요.
struct CreateGroupView: View {
    var onCreated: (_ name: String, _ relationship: Relationship) -> Void = { _, _ in }

    @Environment(\.dismiss) private var dismiss

    @State private var name = ""
    /// Figma 02 기본 화면이 '친구' 선택 상태예요.
    @State private var relationship: Relationship = .friend
    @State private var startDate = Calendar.current.startOfDay(for: .now)
    @State private var showsDatePicker = false

    private var canCreate: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty
    }

    var body: some View {
        VStack(spacing: 0) {
            HNavBar(leading: .back { dismiss() })
                .padding(.horizontal, HLayout.screenPadding)

            HCTAScaffold(bottomInset: 40) {
                VStack(alignment: .leading, spacing: 20) {
                    title
                    HTextField(
                        "그룹 이름",
                        placeholder: "그룹 이름을 입력해 주세요",
                        text: $name,
                        maxLength: 16,
                        textSize: 17,
                        labelSpacing: 10
                    )
                    relationshipSection
                    if relationship == .lover {
                        startDateSection
                            .transition(.opacity.combined(with: .move(edge: .top)))
                    }
                    maxMembers
                }
                .padding(.top, 12)
                .animation(.snappy(duration: 0.25), value: relationship)
            } cta: {
                // TODO: 서버 연결 시 그룹 생성 요청
                HButton("그룹 만들고 초대 코드 받기") {
                    onCreated(name.trimmingCharacters(in: .whitespaces), relationship)
                }
                    .disabled(!canCreate)
            }
        }
        .background(.bg)
        .toolbar(.hidden, for: .navigationBar)
        .sheet(isPresented: $showsDatePicker) {
            StartDateSheet(date: $startDate)
        }
    }

    private var title: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("새 그룹 만들기")
                .font(.screenTitle)
                .foregroundStyle(.ink)
            Text(relationship == .lover
                 ? "만든 뒤 초대 코드로 연인 1명을 초대할 수 있어요."
                 : "만든 뒤 초대 코드로 최대 \(relationship.maxMembers - 1)명을 더 초대할 수 있어요.")
                .font(.body15)
                .foregroundStyle(.textSecondary)
        }
    }

    private var relationshipSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("어떤 사이인가요?")
                .font(.fieldLabel)
                .foregroundStyle(.textSecondary)

            FlowLayout(spacing: 8) {
                ForEach(Relationship.allCases) { relation in
                    HChip(relation.displayName, isSelected: relationship == relation) {
                        relationship = relation
                    }
                }
            }

            decorPreview
        }
    }

    /// 홈 화면 미리보기 — 테두리 line · 모서리 16 · 좌우 16 · 위아래 14 · 아바타 38
    private var decorPreview: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("홈 화면에서 이렇게 보여요")
                    .font(.plex(13, .semibold))
                    .foregroundStyle(.ink)
                Text("캐릭터 사이에 \(relationship.decorPhrase) 떠요")
                    .font(.caption12)
                    .foregroundStyle(.textSecondary)
            }
            Spacer(minLength: 8)
            MemberRow(members: [.character(.mongsil), .character(.gomdol)], relationship: relationship, avatarSize: 38)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .overlay(
            RoundedRectangle(cornerRadius: HRadius.button, style: .continuous)
                .strokeBorder(.line, lineWidth: 1)
        )
    }

    private var startDateSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("우리의 1일")
                .font(.fieldLabel)
                .foregroundStyle(.textSecondary)

            Button {
                showsDatePicker = true
            } label: {
                HStack {
                    Text(startDate.formatted(.dateTime.year().month(.defaultDigits).day().locale(Locale(identifier: "ko_KR"))))
                        .font(.plex(17))
                        .foregroundStyle(.ink)
                    Spacer()
                    HIconView(icon: .calendar, size: 20)
                }
                .padding(.horizontal, 16)
                .frame(height: 54)
                .overlay(
                    RoundedRectangle(cornerRadius: HRadius.input, style: .continuous)
                        .strokeBorder(.border, lineWidth: 1)
                )
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("우리의 1일 선택")

            Text("이 날부터 함께한 날을 세고, 100일·1주년 같은 기념일엔 특별한 주제가 와요.")
                .font(.caption13)
                .plexLineHeight(1.5, size: 13)
                .foregroundStyle(.textSecondary)
        }
    }

    /// 최대 인원 — surface · 모서리 16 · 좌우 18 · 위아래 16
    private var maxMembers: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("최대 인원")
                    .font(.plex(15, .semibold))
                    .foregroundStyle(.ink)
                Text(relationship == .lover ? "연인 그룹은 둘만 함께해요" : "나를 포함해 2~\(relationship.maxMembers)명")
                    .font(.caption13)
                    .foregroundStyle(.textSecondary)
            }
            Spacer()
            Text("\(relationship.maxMembers)명")
                .font(.plex(17, .bold))
                .foregroundStyle(.ink)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 16)
        .background(.surface, in: RoundedRectangle(cornerRadius: HRadius.button, style: .continuous))
    }
}

/// '우리의 1일' 날짜 고르기 — 오늘 이후 날짜는 고를 수 없어요.
private struct StartDateSheet: View {
    @Binding var date: Date
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 0) {
            HNavBar("우리의 1일") {
                Button("완료") { dismiss() }
                    .font(.plex(16, .semibold))
                    .foregroundStyle(.ink)
                    .buttonStyle(.plain)
            }
            .padding(.horizontal, HLayout.screenPaddingCompact)

            DatePicker("우리의 1일", selection: $date, in: ...Date.now, displayedComponents: .date)
                .datePickerStyle(.graphical)
                .labelsHidden()
                .tint(.ink)
                .environment(\.locale, Locale(identifier: "ko_KR"))
                .padding(.horizontal, HLayout.screenPaddingCompact)

            Spacer(minLength: 0)
        }
        .padding(.top, 8)
        .background(.bg)
        .presentationDetents([.height(480)])
        .presentationDragIndicator(.visible)
    }
}

#Preview("친구") {
    NavigationStack {
        CreateGroupView()
    }
}
