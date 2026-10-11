# Review — Android 3단계 (인증 흐름 A1·A2·A3·비밀번호 찾기 + 01 시작 + 내비게이션 뼈대)

기준: `.agent/plan.md`, `.agent/report.md`, 작업 트리의 `android/` 미커밋 변경. `ios/`·`src/`·`docs/` 등 범위 밖 파일 변경 없음 확인.
Codex 가 `assembleDebug` / `testDebugUnitTest`(17개 통과) / `lintDebug` / `installDebug` 를 직접 돌린 결과는 보고서대로라고 가정했고, 아래는 코드를 읽어서 확인한 문제만 적었다. 화면·키보드 동작은 폰에서 보지 못했다.

전반: 계획의 구조(인증/메인 NavHost 분리, 타입 안전 라우트, `SignupAccountState` 분리, A2 비밀번호를 인증 흐름 메모리에만 보관, 디버그 전용 로그아웃)와 iOS 문구·수치·규칙은 충실히 옮겼다. 다만 이번에 새로 넣은 `HTextField` 키보드 액션 처리에 모든 입력창에 영향을 주는 결함이 있고, A1 이 키보드/내비게이션 바 inset 을 처리하지 않는다.

---

## P1

### File / Location
`android/app/src/main/java/com/sapphire/hawe/designsystem/components/HTextField.kt` — `BasicTextField(... keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() }))`

### Problem
`KeyboardActions(onAny = ...)` 는 Done/Next/Go/Search/Send/Previous **모든** 액션에 람다를 지정한다. Compose 는 해당 액션의 핸들러가 null 일 때만 기본 동작(Next → 다음 입력으로 포커스 이동, Done → 키보드 숨김)을 실행하므로, `onImeAction` 이 null 이어도 빈 람다가 기본 동작을 대신한다. 즉 `onImeAction` 을 넘기지 않는 모든 `HTextField` 에서 Next/Done 키가 아무 일도 하지 않는다.
- A1 이메일(`ImeAction.Next`) → 비밀번호로 포커스가 넘어가지 않음
- A2 이메일/비밀번호(`Next`) → 다음 칸으로 안 넘어가고, 확인 칸/A3 닉네임/비밀번호 찾기 이메일(`Done`) → 키보드가 안 내려감
(A1 비밀번호는 `onImeAction = login` 이라 로그인은 실행됨.)

### Why It Matters
iOS 에서는 동작하는 기본 키보드 흐름이 깨지고, 4단계 이후 모든 입력 화면(02 그룹 이름, 03 초대 코드, 댓글 입력 등)이 같은 컴포넌트를 쓴다. 폰에서 입력할 때 가장 먼저 눈에 띄는 불편이다. 확인한 근거는 코드 구조(`KeyboardActions` 생성자 의미)이며 폰에서 직접 눌러 보지는 않았다.

### Recommended Fix
`LocalFocusManager` / `LocalSoftwareKeyboardController` 를 가져와 기본 동작을 직접 호출한다.
```kotlin
val focus = LocalFocusManager.current
val keyboard = LocalSoftwareKeyboardController.current
keyboardActions = KeyboardActions(
    onNext = { focus.moveFocus(FocusDirection.Down) },
    onDone = { if (onImeAction != null) onImeAction() else { keyboard?.hide(); focus.clearFocus() } },
)
```
(`onAny` 사용 제거.) 갤러리/기존 호출은 기본값으로 그대로 동작해야 한다.

---

### File / Location
`android/app/src/main/java/com/sapphire/hawe/feature/auth/LoginScreen.kt` — 최상위 `Column(Modifier.fillMaxSize().background(...).verticalScroll(...).padding(top = introTopInset, bottom = ctaBottomInset)`

### Problem
`MainActivity` 가 `enableEdgeToEdge()` 이고 매니페스트에 `windowSoftInputMode` 도 없는데, A1 은 `imePadding()` 도 내비게이션 바 inset 도 적용하지 않는다(`RootScreen` 은 상태 바만 처리). 다른 인증 화면은 `HCtaScaffold` 가 `imePadding` + 내비게이션 바를 처리하지만 A1 은 `HCtaScaffold` 를 쓰지 않는다.
- 3버튼 내비게이션에서 스크롤 끝의 아래 여백이 36dp 뿐이라 마지막 "Google로 계속하기" 버튼의 아래쪽이 내비게이션 바(약 48dp) 뒤로 겹친다.
- 키보드가 열리면 스크롤 영역의 뷰포트가 줄어들지 않아(키보드 위로 올려 주는 inset 처리 없음), 키보드 높이가 큰 기기/작은 화면에서 비밀번호 입력창·로그인 버튼이 키보드 아래에 가려질 수 있다.

### Why It Matters
첫 화면이다. 지난 리뷰(HCtaScaffold)에서 지적한 것과 같은 종류의 문제가 같은 단계의 다른 화면에서 반복됐고, 계획의 Edge Cases("키보드가 올라와도 CTA/입력이 가려지지 않는다, 3버튼 내비게이션에서 가려지지 않는다")에 명시돼 있다.

### Recommended Fix
스크롤 `Column` 에 `Modifier.imePadding()` 을 붙이고, 아래 여백을 `max(HLayout.ctaBottomInset, navigationBars 하단 + 12.dp)` 로 계산한다(키보드가 열려 있으면 `imePadding` 이 내비게이션 바를 포함하므로 중복되지 않게 `HCtaScaffold` 와 같은 처리). 같은 계산이 두 곳에 생기므로 `HCtaScaffold` 안의 `ctaBottom` 계산을 작은 공용 함수(예: `@Composable fun ctaBottomPadding(bottomInset: Dp): Dp`)로 빼서 A1 과 공유하면 중복이 없다.

---

## P2

### File / Location
`android/app/src/main/java/com/sapphire/hawe/feature/auth/SignupAccountScreen.kt` — `AgreementRow` 의 `androidx.compose.material3.TextButton`

### Problem
"보기" 를 Material3 `TextButton` 으로 만들어 최소 높이 48dp 와 Material 리플(테마 primary 색 = accent)이 생긴다. iOS 는 최소 높이 44, 눌림 효과 없는 밑줄 텍스트다. 이 프로젝트는 시스템 컴포넌트를 쓰지 않고 iOS 수치대로 직접 그린다는 규칙(ANDROID.md §3, 계획 Root Cause 6)과도 어긋난다.

### Why It Matters
약관 박스의 줄 높이가 iOS(44) 보다 커지고 눌림 색이 다르다. 기능 문제는 아니다.

### Recommended Fix
`Text("보기", Modifier.heightIn(min = 44.dp).clickable(...).padding(horizontal = 4.dp), ...)` 로 교체(필요하면 `indication = null`).

---

### File / Location
`android/app/src/main/java/com/sapphire/hawe/app/AuthNavigation.kt` — `var signupPassword by remember { ... }`, `signupConfirm`

### Problem
A2 비밀번호를 `remember` 로 들고 있어, 시스템 글자 크기·언어·다크 모드 같은 구성 변경으로 Activity 가 재생성되면(세로 고정이라 회전은 아님) 비밀번호/확인만 지워지고 `rememberSaveable` 인 이메일·약관은 유지된다.

### Why It Matters
의도(프로세스 복원 시 비밀번호를 저장하지 않음)와 달리 단순 구성 변경에서도 입력이 사라지는 부분 상태가 된다. 영향이 작아 계획의 "허용" 범위일 수 있으나 보고서에 이 한계가 적혀 있지 않다.

### Recommended Fix
허용한다면 보고서에 한계를 명시한다. 유지하고 싶다면 비밀번호를 `AppViewModel`(Activity 범위, 구성 변경에서 유지) 의 휘발성 필드로 옮긴다(저장하지 않음).

---

### File / Location
`android/app/src/test/java/com/sapphire/hawe/feature/auth/`

### Problem
계획 Tests Required 대비 빠진 케이스: 이메일 `""`·`a@b.co`·`a b@c.com` 외에 공백 포함 입력, `canProceed` 에서 약관 둘 중 하나만 빠졌을 때(현재는 `agreePrivacy = false` 한 번만), `emailError`/`passwordError` 가 빈 값에서 null 인 것은 확인하지만 `passwordError` 문구 자체와 `confirmError` 가 빈 확인칸에서 null 인 케이스, 로그인 후 같은 이메일(대소문자 다름) 재입력 시 null 인 케이스는 있음.

### Why It Matters
큰 공백은 아니고 핵심 규칙(검사 4종·전체 동의·로그인 계정 생성)은 테스트로 덮여 있다.

### Recommended Fix
`agreeService = false`/`agreePrivacy = false` 각각 단독일 때 `canProceed` 가 false 인지, `passwordError` 문구를 검증하는 단언 몇 줄만 추가.

---

## 결론

**수정 후 재리뷰 필요** — P1 2건(`HTextField` 키보드 액션, A1 inset 처리)을 고친 뒤 다시 리뷰한다. P2 는 같이 고치는 것을 권장한다.
수정 후 `.agent/report.md` 에 항목별 처리 결과(수정/보류와 이유)를 추가하고, `assembleDebug` / `testDebugUnitTest` / `lintDebug` / `installDebug` 를 다시 돌려 결과를 적어 달라.
