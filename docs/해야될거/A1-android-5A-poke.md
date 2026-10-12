# A1. Android 5A — 콕 찌르기 · 똥뿌리기 서버 연결 (임시 이미지)

> 범위: **Android 앱만**(`android/`). 서버(5단계)는 이미 커밋됨. iOS 는 A5. 알림 수신은 범위 밖(푸시 연결은 알림 단계). 구현자: Claude 또는 Codex(둘 다 가능). 디자인이 없는 버튼 이미지는 **임시 이미지로 채운다**(사용자 지시).
> 빌드/테스트: JDK 21 필요 — `set JAVA_HOME=C:\Users\kimch\.jdks\ms-21.0.10` 후 `android\gradlew.bat :app:testDebugUnitTest --offline`(이 PC 기본 JDK 25 는 Gradle 이 지원하지 않음).

## Goal

05 미션 화면의 "참여 현황"에서, 아직 안 올린 멤버에게 **콕 찌르기**와 **똥뿌리기** 버튼을 보여 주고, 눌렀을 때 서버(`POST /api/groups/{groupId}/pokes`)로 보내며, 이미 보낸 종류는 "보냈어요" 상태로 표시한다(서버가 `members[].pokedByMe` 로 내려줌).

## Findings

- 서버 계약(`CONTRACT_DECISIONS.md` §4, `API_DESIGN.md` §5, 5단계 커밋):
  - `POST /api/groups/{groupId}/pokes` `{ "toUserId": 7, "type": "poke" | "fling" }` → 201 `{ id, toUserId, type, missionId, sentAt }`.
  - 규칙: (보낸 사람, 받는 사람, **미션**, 종류) 당 1회. 오류: 409 `ALREADY_POKED`, 409 `TARGET_ALREADY_SUBMITTED`, 409 `NO_OPEN_MISSION`, 400 `CANNOT_POKE_SELF`, 404 `TARGET_NOT_GROUP_MEMBER`, 403 `NOT_GROUP_MEMBER`, 404 `GROUP_NOT_FOUND`.
  - 현재 미션 응답 `GET /api/groups/{groupId}/missions/today` 의 `missions[].members[]` = `{ userId, submitted, pokedByMe: ["poke","fling"] }`(없으면 `[]`).
- 앱 현재 상태: 05 화면 `MissionMemberRow`(`feature/mission/MissionScreen.kt` 약 182행)는 완료/"아직 안 했어요" 텍스트만 있고 **콕/똥 버튼이 없다**(1차에서 숨김). `DataStore.poke(memberID, groupID)` 는 로컬 `StoredMission.poked: Set<Long>` 만 갱신(서버 미연결). `MissionMember(id, profile, isMe, isDone, pokedToday)`(`core/model/TodayMission.kt`). `MissionMemberDto(userId, submitted)` 에 `pokedByMe` 필드 없음(`core/net/MissionApi.kt`). `ServerMission.members: Map<Long, Boolean>`(`core/net/MissionRepository.kt`), `DataStore.applyCurrentMission` 이 `StoredMission(…, serverMembers = it.members + …)` 로 반영. 토스트 문구 `pokeToastText(nickname)`(`feature/mission/MissionFormat.kt:41`). `HToastHost(toast)` 가 05 화면에 있음.
- 네트워크 계층: `ApiClient`(`post<B,T>`, `ApiException(status, code, message)`, `NetworkException`), 레포지토리 패턴(`GroupRepository`, `MissionRepository`, `SubmissionRepository`), 오류 문구 매핑 `core/net/ApiMessages.kt`. `AppViewModel` 이 레포지토리를 만들어 `MainNavigation` 에 전달(`submissions`, `missions`, `groups`, `calendar`).
- 푸시 모델 `core/push/PushModel.kt` 에 `PushKind.POKE` 가 있다(알림 단계에서 `fling` 추가 — 이번 범위 아님).
- `HIcon` 은 벡터 드로어블 enum(`designsystem/assets/HIcon.kt`). 임시 도장 이미지는 `res/drawable/stamp_*.xml` 로 이미 같은 방식으로 추가돼 있다.

## Root Cause / Key Implementation Points

1. **레포지토리**: `core/net/PokeApi.kt`(DTO `PokeRequestDto(toUserId, type)`, `PokeResponseDto`) + `core/net/PokeRepository.kt`(`suspend fun send(groupId, toUserId, kind: PokeKind): PokeResult`). `enum class PokeKind(val wire: String) { POKE("poke"), FLING("fling") }` 는 `core/model/` 에 둔다. 오류는 `ApiException` 코드로 분류하는 순수 함수(예: `classifyPokeFailure(error): PokeOutcome`)로 만들어 단위 테스트 가능하게:
   - `ALREADY_POKED`, `TARGET_ALREADY_SUBMITTED` → **상태를 새로고침하고 조용히 처리**(토스트 없이 또는 "이미 보냈어요"/"이미 올렸어요" 정도의 가벼운 안내 — CONTRACT §4: 조용히 처리), `NO_OPEN_MISSION` → 새로고침, `NOT_GROUP_MEMBER`/`GROUP_NOT_FOUND`/`TARGET_NOT_GROUP_MEMBER` → 홈 복귀 또는 새로고침, `NetworkException` → "네트워크 연결을 확인해 주세요", 그 외 → "잠시 후 다시 시도해 주세요".
2. **모델 확장**: `MissionMemberDto` 에 `val pokedByMe: List<String> = emptyList()`; `ServerMission` 에 `pokedByMe: Map<Long, Set<PokeKind>>`; `StoredMission.poked: Set<Long>` 를 `Map<Long, Set<PokeKind>>` 로 교체(로컬 낙관적 반영 + 서버 값으로 덮어씀); `MissionMember.pokedToday: Boolean` 를 `poked: Set<PokeKind>` 로 교체(`DataStore.todayMission` 에서 채움). 모르는 `pokedByMe` 값은 무시. `applyCurrentMission` 은 서버 값을 우선한다.
3. **DataStore**: `fun poke(memberID, groupID)` 를 `fun markPoked(groupID, memberID, kind)`(서버 성공 후 낙관 반영) 로 바꾸고, 기존 호출부/테스트(`DataStoreTest`)를 맞춘다. 서버 연결 로직은 화면(또는 상위 컴포저블)에서 `PokeRepository` 를 호출하고 성공 시 `store.markPoked`, 실패는 분류대로.
4. **UI (05 화면)**: `MissionMemberRow` 에서 `!member.isDone && !member.isMe` 일 때 오른쪽에 버튼 2개(콕, 똥) — 이미 보낸 종류는 비활성+"보냈어요" 스타일(투명도/체크). 접근성 `contentDescription`("OO님 콕 찌르기", "OO님에게 똥 뿌리기", 보낸 상태 "…보냈어요"). 연타 방지: 요청 진행 중 해당 (멤버,종류) 버튼 비활성. 성공 토스트: `pokeToastText(nickname)` 재사용 + 새 `flingToastText(nickname)`("OO님에게 똥을 뿌렸어요"). 내가 아직 미션을 안 올렸어도 버튼은 보인다(서버는 제출 여부와 무관하게 허용 — 대상이 이미 제출했으면 409).
5. **임시 이미지**: `res/drawable/ic_poke_temp.xml`, `ic_fling_temp.xml`(단순 벡터: 손가락/똥 모양이 어려우면 둥근 사각 배경 + 단순 도형으로) 두 개를 추가하고 **파일 첫 줄 주석 `<!-- 임시 이미지: Figma 디자인 확정 시 교체 -->`**. 이모지 텍스트(👉/💩) 대체도 가능하나 폰트 의존이라 벡터를 권장. `HIcon` enum 에 `POKE_TEMP`, `FLING_TEMP` 추가(교체 지점을 한 곳으로).
6. **여러 미션(B7 이후)**: 이번에는 `missions.firstOrNull()` 한 개만 다룬다(현재 앱 구조). 다중 미션 UI 는 A6. 요청에 `missionId` 는 넣지 않는다(서버 기본값 = 가장 작은 슬롯의 현재 미션).

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `core/model/PokeKind.kt` (신규) | enum |
| `core/model/TodayMission.kt` | `MissionMember.pokedToday` → `poked: Set<PokeKind>` (`isMe/isDone` 유지) |
| `core/net/MissionApi.kt`, `MissionRepository.kt` | `pokedByMe` 매핑 |
| `core/net/PokeApi.kt`, `PokeRepository.kt` (신규), `core/net/ApiMessages.kt` | API 호출·오류 분류·문구 |
| `app/AppViewModel.kt`, `app/MainNavigation.kt` | `PokeRepository` 생성·전달(`MissionScreen` 에 주입) |
| `core/data/DataStore.kt` | `StoredMission.poked` 타입 변경, `applyCurrentMission` 서버 값 반영, `markPoked`, `todayMission` 에서 `poked` 채우기 |
| `feature/mission/MissionScreen.kt`, `MissionFormat.kt` | 버튼 UI, 토스트 문구 |
| `designsystem/assets/HIcon.kt`, `res/drawable/ic_poke_temp.xml`, `ic_fling_temp.xml` (신규) | 임시 이미지 |
| 테스트 | `core/net/PokeRepositoryTest`(MockWebServer), `MissionRepositoryTest`(pokedByMe 매핑), `DataStoreTest`(applyCurrentMission 서버 값, markPoked), `feature/mission/MissionFormatTest`(토스트 문구) |

## Implementation Steps

1. 모델/DTO/레포지토리/오류 분류 + 단위 테스트. 2. `DataStore` 변경 + 기존 테스트 수정. 3. 네비게이션 주입. 4. 05 화면 UI + 임시 이미지. 5. `android\gradlew.bat :app:testDebugUnitTest --offline` 전체 통과(JDK 21). 6. 서버를 띄운 실기기/에뮬레이터 확인은 사용자 몫 — 못 했으면 "미검증".

## Constraints

- `android/` 만 수정. iOS/백엔드 수정 금지. 알림(푸시 수신/채널)·권한 처리 금지. 목업 데이터와 서버 데이터를 섞지 않는다(서버 값이 있으면 서버 우선).
- 임시 이미지는 교체 쉽게 한 곳(HIcon + 두 파일)으로. 새 라이브러리 추가 금지. 커밋/푸시 금지.

## Edge Cases

- 대상이 그 사이 제출함(409 `TARGET_ALREADY_SUBMITTED`) → 새로고침 후 "완료" 표시, 버튼 사라짐. 이미 보냄(409 `ALREADY_POKED`) → 버튼을 "보냈어요"로 맞춤. 미션 없음/닫힘(`NO_OPEN_MISSION`) → 새로고침.
- 본인 행에는 버튼 없음. 이미 완료한 멤버 행에는 버튼 없음. 멤버가 그룹을 나감 → 404 → 새로고침.
- 네트워크 실패 → 토스트, 버튼 상태 원복(낙관 반영하지 않음 — 서버 성공 후에만 반영).
- 화면 회전/재진입 시 서버 `pokedByMe` 로 상태 복원. 로그아웃/토큰 만료 → 기존 `ApiClient` 세션 만료 처리에 맡김.
- 접근성: TalkBack 에서 버튼 의미와 보낸 상태가 읽힘.

## Regression Risks

- `MissionMember`/`StoredMission` 필드 타입 변경이 `DataStore`, 05·홈·결과 화면, `DataStoreTest`, `MissionFormatTest` 에 번짐 → 컴파일 오류로 누락을 잡는다.
- 05 화면 레이아웃(행 높이 55dp) 안에서 버튼 2개가 닉네임을 과도하게 줄이지 않는지(긴 닉네임 10자) 확인.
- `applyCurrentMission` 이 매 새로고침마다 서버 값으로 덮어쓰므로 낙관 반영이 깜빡이지 않게(성공 후 `markPoked` → 이후 새로고침 값도 동일).

## Tests Required

- `PokeRepositoryTest`: 요청 경로/본문(`type` 소문자), 201 파싱, 각 오류 코드 → 분류 결과, 네트워크 오류.
- `MissionRepositoryTest`: `pokedByMe` `["poke"]`/`["poke","fling"]`/누락/알 수 없는 값 매핑.
- `DataStoreTest`: 서버 `pokedByMe` 반영, `markPoked` 후 `todayMission().members[].poked`, 서버 새로고침 후 유지.
- `MissionFormatTest`: 두 토스트 문구.
- (UI 자동화 테스트가 없는 repo — 화면은 컴파일 + 수동 확인.)

## Validation Steps

`android\gradlew.bat :app:testDebugUnitTest --offline`(JDK 21) 전체 통과를 기록, `rg "pokedToday" android` 로 잔여 사용처 없음 확인, 가능하면 백엔드 서버 + 에뮬레이터에서 콕 1회/중복 409 확인(사용자). 보고서에 "임시 이미지 파일 목록과 교체 지점" 명시.

## Potential Merge Conflicts

`core/data/DataStore.kt`, `MissionScreen.kt`, `MissionRepository.kt`, `TodayMission.kt`(A6 다중 미션·A4 새 틀과 겹침 — A1 먼저), `app/MainNavigation.kt`·`AppViewModel.kt`(모든 앱 계획이 레포지토리를 추가하므로 순차 권장).
