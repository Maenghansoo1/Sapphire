# Review — Android 0~2단계 (뼈대 · 디자인 시스템 · 모델/임시 저장소)

기준: `.agent/plan.md`, `.agent/report.md`, 작업 트리의 `android/` (미커밋). 백엔드·iOS·Cordova 파일 변경 없음 확인.
빌드·테스트는 계획대로 실행하지 않았다 (사용자가 직접 검증). 아래는 코드 읽기로 확인한 문제만 적었다.

전반: 범위는 계획과 맞고 iOS 규칙(미션 창, 초대 코드, DemoSeed, 기록·연속 참여일)도 대부분 정확히 옮겼다. 다만 2단계의 상태 모델이 계획의 핵심 요구(불변 데이터 + 복사 교체)를 지키지 않아 3단계 이후 화면에서 갱신 누락이 생기는 구조이고, 1단계 컴포넌트 두 곳이 iOS 레이아웃과 다르다.

---

## P1

### File / Location
`android/app/src/main/java/com/sapphire/hawe/core/model/{GroupDetail.kt, Submission.kt}`, `core/data/DataStore.kt` — `StoredGroup`/`StoredMission`(37–46행), `update`(199–206행), `updateSubmission`(208–210행), `setMyProfile`(57–63행), `refreshTodayMissions`(122–131행), `submit`, `poke`, `setTopicTime`/`setNotify`/`setMyGroupProfile`

### Problem
모델이 `var` 속성과 `MutableList`/`MutableSet` 을 가진 가변 data class 이고, `DataStore` 는 기존 객체를 **제자리에서 바꾼 뒤** 바깥 껍데기만 얕게 복사해 `groups[index]` 에 다시 넣는다. 안쪽 객체(`Submission`, `GroupMember`, `StoredMission`)는 같은 인스턴스가 그대로 재사용된다. 예: `toggleLike` 는 `Submission.likedByMe/likeCount` 를 직접 바꾸고, `result()` 는 같은 `Submission` 인스턴스들을 담은 리스트를 돌려준다. `groups` 도 `SnapshotStateList` 그대로 공개돼 밖에서 바꿀 수 있다(테스트가 실제로 `store.groups.first().missions.add(...)` 로 바꾼다).
계획 Implementation Step 12 는 "불변 `data class` 를 원소 교체(`groups[i] = groups[i].copy(...)`)로 갱신"과 "공개 상태는 읽기 전용 노출"을 요구했다.

### Why It Matters
Kotlin 2.2 Compose 컴파일러는 strong skipping 이 기본이라, 불안정 타입 인자는 **인스턴스 동일성(===)** 으로 비교해 같으면 재구성을 건너뛴다.
- 7단계에서 `SubmissionCard(submission)` 처럼 작품을 하위 컴포저블로 넘기면, 좋아요·댓글 후에도 같은 `Submission` 인스턴스라 하위 컴포저블이 건너뛰어져 **좋아요 수·댓글이 화면에 안 바뀐다**.
- `derivedStateOf { store.group(id) }` 나 `remember(key)` 를 쓰면, 이전 값이 제자리에서 이미 바뀌어 새 복사본과 `equals` 가 같으므로 변경 알림이 나가지 않아 화면이 옛 값으로 남는다 (16 그룹 알림 토글, 16-1 그룹 내 프로필).
- 3단계 이후 모든 화면이 이 API 를 쓰기 때문에, 화면을 붙이기 전인 지금 고쳐야 비용이 작다.

### Recommended Fix
- 모델의 `var` → `val`, `MutableList`/`MutableSet` → `List`/`Set` (`GroupMember`, `GroupDetail`, `Submission`, `StoredGroup`, `StoredMission`).
- `DataStore.update(groupId) { stored -> stored.copy(...) }` 처럼 **변환 함수가 새 객체를 반환**하게 바꾸고, 바뀐 경로의 객체만 새로 만든다. 예: `toggleLike` → `mission.copy(submissions = submissions.map { if (it.id == id) it.copy(likedByMe = !it.likedByMe, likeCount = ...) else it })` → `stored.copy(missions = ...)`.
- `groups` 는 `private val _groups = mutableStateListOf<StoredGroup>()` + `val groups: List<StoredGroup> get() = _groups` 로 읽기 전용 노출 (`myProfile` 처럼).
- `DemoSeed` 는 리스트를 다 만든 뒤 한 번에 생성하도록 조정.
- 테스트(`avoidsThirdConsecutiveTypeAndRecentTopic`, `streakUsesSeoulDays`)는 `groups` 를 직접 바꾸지 말고, `internal` 테스트용 진입점(예: `internal fun replaceMissionsForTest(groupId, missions)`)이나 `DataStore` 공개 API 로 상태를 만든다.

---

### File / Location
`android/app/src/main/java/com/sapphire/hawe/designsystem/layout/HCtaScaffold.kt` — 24–31행

### Problem
`MainActivity` 가 `enableEdgeToEdge()` 를 켜서 내용이 내비게이션 바 아래까지 그려지는데, `HCtaScaffold` 는 내비게이션 바 inset 을 반영하지 않고 아래 여백을 고정 `36.dp` 로만 둔다. iOS 의 36pt 는 홈 인디케이터(34pt) 영역을 포함한 값이다.

### Why It Matters
3버튼 내비게이션(높이 약 48dp) 기기에서는 하단 CTA 아래쪽 약 12dp 가 시스템 버튼 뒤에 가려지고 터치도 시스템 바가 먼저 받는다. A2·A3·02·03 등 거의 모든 입력 화면이 이 틀을 쓴다. 계획 Edge Cases 에 "제스처/3버튼 내비게이션 모두에서 CTA 위치"로 명시된 항목이다.

### Recommended Fix
CTA 아래 여백을 `max(bottomInset, 내비게이션 바 inset + 최소 여백)` 으로 계산한다. 예:
```kotlin
val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
... .padding(bottom = maxOf(bottomInset, navBottom + 12.dp))
```
키보드가 열렸을 때는 `imePadding()` 이 이미 내비게이션 바 높이를 포함하므로 이중으로 더해지지 않는지(`WindowInsets.ime` 가 열려 있으면 navBottom 대신 0 사용 등) 같이 처리한다.

---

### File / Location
`android/app/src/main/java/com/sapphire/hawe/designsystem/components/MemberRow.kt` — 13–20행

### Problem
iOS `MemberRow` 는 `HStack(spacing: -5)` 로 **모든 이웃(아바타↔장식)이 5pt 씩 겹치고**, 장식이 `.zIndex(1)` 로 아바타 위에 그려지며, 장식 지름은 `(avatarSize * 0.59).rounded()` 이다.
Android 는 아바타에만 `offset(x = -5.dp)` 를 줘서 레이아웃 폭은 그대로이고 그림만 왼쪽으로 옮긴다. 그 결과 (1) 장식과 앞 아바타는 겹치지 않고, (2) 아바타 뒤에 5dp 빈틈이 생기며, (3) 뒤에 그려지는 아바타가 장식을 덮는다. 장식 지름도 반올림하지 않는다(34 × 0.59 = 20.06).

### Why It Matters
04 홈 카드·03 미리보기 등 그룹이 보이는 곳마다 쓰는 컴포넌트라 Figma/iOS 와 다르게 보인다. 1단계의 목적이 iOS 와 같은 모양의 디자인 시스템이다.

### Recommended Fix
```kotlin
Row(horizontalArrangement = Arrangement.spacedBy((-5).dp)) {
    members.forEachIndexed { index, member ->
        if (index > 0) Box(Modifier.zIndex(1f)) { RelationshipDecoration(relationship, (avatarSize.value * 0.59f).roundToInt().dp) }
        HAvatar(member, size = avatarSize, ringed = true)
    }
}
```
(`offset` 제거)

---

## P2

### File / Location
`android/app/src/main/java/com/sapphire/hawe/designsystem/components/HAlert.kt` — 29–33행

### Problem
1. 카드에 `clickable(enabled = false) {}` 를 달았지만 비활성 clickable 은 터치를 소비하지 않아, 카드 안(제목·내용·여백)을 눌러도 바깥 `Box` 의 `clickable(onClick = onCancel)` 이 실행돼 알림창이 닫힌다. iOS 는 배경에만 탭 제스처가 있다.
2. Compose `Dialog` 창은 기본으로 뒤 배경을 어둡게(window dim) 하므로 직접 그린 ink 45% 와 겹쳐 Figma 보다 어둡고, `decorFitsSystemWindows` 기본값 때문에 상태 바 영역은 직접 그린 dim 이 덮지 못한다. (에뮬레이터에서 확인 필요)

### Why It Matters
로그아웃·탈퇴·댓글 삭제 확인에 쓰는 알림창이라 내용을 읽다 눌러도 닫히는 동작은 iOS 와 다르다. 색은 시각 차이.

### Recommended Fix
1. 카드의 `clickable(enabled = false) {}` 를 터치를 소비하는 `Modifier.pointerInput(Unit) { detectTapGestures { } }` 또는 `clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}` 로 바꾼다.
2. `DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)` 로 하고, 창 dim 은 끈다 (`(LocalView.current.parent as DialogWindowProvider).window.setDimAmount(0f)` 를 `SideEffect` 에서). 에뮬레이터에서 dim 이 한 겹인지 확인.

---

### File / Location
`android/app/src/main/java/com/sapphire/hawe/designsystem/components/HToggle.kt` — 16–21행

### Problem
iOS `HToggleStyle` 은 비활성일 때 `.opacity(0.4)` 로 흐리게 그리고 상태 전환에 짧은 애니메이션이 있다. Android 는 `enabled = false` 여도 모양이 그대로이고 애니메이션이 없다.

### Why It Matters
8단계 17-1 알림 설정에서 "전체 OFF 면 나머지 비활성"을 화면으로 구분할 수 없다.

### Recommended Fix
루트에 `Modifier.alpha(if (enabled) 1f else 0.4f)`, 손잡이 위치는 `animateDpAsState(if (isOn) 23.dp else 3.dp, tween(200))`.

---

### File / Location
`android/app/src/main/res/values/themes.xml`

### Problem
`android:windowLightNavigationBar` 는 API 27 부터인데 minSdk 26 의 기본 `values/` 에 들어 있다. lint `NewApi` 로 걸릴 가능성이 높아 사용자 검증 단계의 `lintDebug` 를 막을 수 있다. 또한 `enableEdgeToEdge()` 가 시스템 바 색·아이콘 대비를 이미 설정하므로 중복이다.

### Recommended Fix
`windowLightStatusBar`/`windowLightNavigationBar` 줄을 지우거나(권장, `enableEdgeToEdge` 에 맡김) `values-v27/themes.xml` 로 옮긴다.

---

### File / Location
`android/app/src/test/java/com/sapphire/hawe/core/data/DataStoreTest.kt`

### Problem
계획 Tests Required 대비 빠진 항목:
- 미션 유형 규칙을 **여러 시드로 반복** 확인하지 않음 (`Random(7)` 한 번뿐이라 우연히 통과할 수 있음)
- `deleteComment` 에 **남의 댓글 id** 를 넘기면 지워지지 않는지
- `LOVE22` 참여 성공(1명 + 나 = 2명) 후 재조회 `ALREADY_MEMBER`
- 연인 그룹 생성 시 `startDate` 보존
- `setNotify` / `setTopicTime` / `setMyGroupProfile` / `poke` 후 `group()`·`todayMission()` 에 반영되는지 (P1 수정 후 회귀 방지에 필요)

### Recommended Fix
위 케이스를 추가한다. 유형 규칙은 `repeat(20) { seed -> ... Random(seed) ... }` 로 반복.

---

## 결론

**수정 후 재리뷰 필요** — P1 3건(불변 상태 모델, CTA 내비게이션 바 겹침, MemberRow 겹침 모양)을 고친 뒤 다시 리뷰한다. P2 는 같이 고치는 것을 권장한다.
수정 후 `.agent/report.md` 에 항목별 처리 결과(수정/보류와 이유)를 추가해 달라.
