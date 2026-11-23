# B3. AI 미션 검색 — Android 검색 화면

> 범위: Android 앱만. **선행: B1**(API). B2 가 없어도 동작하고, B2 완료 후 `expanded`/`viaExpansion` 문구가 활성화된다. 근거: `AI_FEATURES.md` §2.3. 빌드/테스트: `android\gradlew.bat :app:testDebugUnitTest --offline`(JDK 21).

## Goal

키워드를 입력하면 지난 미션 목록을 보여 주고, 확장어로 찾은 항목은 "'우산' 때문에 찾았어요" 같은 이유 문구를 붙인다. 항목을 누르면 해당 미션의 기존 상세/결과 화면으로 이동한다(제출하지 않은 미션은 기존 잠금 규칙대로 막힘).

## Findings

- API(B1/B2): `GET /api/search/missions?q=&groupId=&limit=` → `[{missionId, groupId, groupName, content, template, missionDate, matchedTerms[], submitted, expanded?}]`. 점수는 없음. `matchedTerms` 항목이 확장으로 매칭된 경우 `viaExpansion=true`(B2 이후).
- Android 는 기능별 폴더(`feature/<name>`)와 `core`(모델/저장소), `designsystem` 컴포넌트(`HButton`, `HListRow`, `HIcon` 등)를 쓴다. 진입점 후보: 마이페이지(`feature/mypage/MyPageScreen.kt`) 또는 그룹 홈. 최종 진입점은 구현 시 기존 내비게이션을 확인해 결정하고 보고서에 기록.
- 디자인(Figma) 없음 → 기존 컴포넌트로 구성하고 임시 에셋은 `임시` 주석을 단다.
- 제출하지 않은 미션의 피드는 서버가 423 을 준다. 앱은 미션 상세/결과로의 이동만 하고 새 잠금 로직을 만들지 않는다.

## Key Implementation Points

1. **네트워크 계층**: 기존 API 클라이언트/리포지토리 패턴(`core/…`)을 확인해 `SearchRepository.searchMissions(q, groupId?)` 추가. DTO 는 serialization 모델.
2. **화면** `feature/search/SearchScreen.kt` + `SearchViewModel`(기존 상태 관리 방식 따름): 검색창(디바운스 400ms 또는 키보드 검색 버튼), 그룹 필터(내 그룹 칩, 기본 전체), 결과 리스트(문장, 틀 이름, 날짜, 그룹명, 내가 제출했는지 표시), 빈 결과/오류/로딩 상태.
3. **이유 문구**: `matchedTerms` 에서 `viaExpansion=true` 가 있으면 "'<확장어>' 때문에 찾았어요", 원 질의 매칭이면 문구 생략 또는 "'<질의>' 포함". 문구 조립은 순수 함수로 분리해 테스트.
4. **내비게이션**: 항목 클릭 → 해당 미션 화면(기존 `missionId` 진입 경로 재사용). 내가 제출하지 않은 마감 미션은 결과 화면에서 서버가 423 → 기존 처리(잠금 안내).
5. 질의 최소 길이(2자) 미만이면 요청하지 않고 안내. 요청 중복/취소(최신 질의만 반영).
6. 디자인 없는 아이콘은 임시(`HIcon` 한 곳에 모아 `임시` 주석).

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `android/app/src/main/java/com/sapphire/hawe/feature/search/SearchScreen.kt`, `SearchViewModel.kt` (신규) | 화면·상태 |
| `core/…` 저장소·API 정의 (기존 위치 확인) | 검색 API 호출 |
| 내비게이션 파일 + 진입점(마이페이지 또는 그룹 홈) | 라우트·버튼 |
| `designsystem/assets/HIcon.kt` | 검색 아이콘(임시 허용) |
| 테스트 | 아래 |

## Constraints

- `android/` 만. 서버 API 변경 요구 금지(필요하면 B1/B2 로 되돌려 요청). 새 의존성 금지.
- 검색어를 로컬 저장(최근 검색어 등)하려면 DataStore 에 저장하되 **1차에서는 저장하지 않는다**(범위 밖).
- 점수/순위 표시 금지.
- 커밋/푸시 금지.

## Edge Cases

- 오프라인/서버 오류 → 재시도 안내.
- 결과 0건 → "관련 미션이 없어요".
- 아주 긴 문장 → 말줄임.
- 그룹이 하나뿐 → 그룹 필터 숨김.
- 확장 없이(B2 미배포/폴백) 결과가 와도 정상 표시.
- 접근성: 결과 항목에 문장·그룹·제출 여부 `contentDescription`.

## Tests Required

- 이유 문구 조립 순수 함수(확장 매칭/원 질의 매칭/없음).
- ViewModel 상태 전이(빈 질의, 로딩, 성공, 빈 결과, 오류) — 기존 테스트 방식(가짜 저장소)을 따른다.
- 질의 정규화(앞뒤 공백, 최소 길이).

## Validation Steps

`android\gradlew.bat :app:testDebugUnitTest --offline` 통과. 수동: 실서버(B1 배포) 대상으로 검색→상세 이동, 제출 안 한 미션 진입 시 잠금 처리, 그룹 필터. 못 했으면 "미검증".

## Potential Merge Conflicts

내비게이션 파일, 마이페이지/홈 화면, `HIcon.kt`.
