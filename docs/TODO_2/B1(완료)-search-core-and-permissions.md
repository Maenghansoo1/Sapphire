# B1. AI 미션 검색 — 검색 API 코어 + 권한 필터 (서버, LLM 없이)

> 범위: 백엔드 신규 feature 패키지 `com.sapphire.hawe.search`(5개 하위 폴더 규칙: controller/service/dto/domain/dao). **LLM 질의 확장은 B2.** 근거: `docs/FEATURE/AI_FEATURES.md` §2. 빌드/테스트: `./mvnw test`(Java 25).

## Goal

`GET /api/search/missions?q=&groupId=&limit=` — 키워드로 **내가 볼 수 있는 지난 미션 문장**을 형태소(Nori) 기반으로 찾는다. 이 단위만으로도 동작하는 검색을 완성하고, **권한 필터가 잠금 규칙을 우회하지 못함**을 테스트로 고정한다.

## Findings

- 검색 대상은 서버·AI가 만든 **미션 문장**뿐이다: `missions.content`, 틀 `template`(한국어 이름), `params` 의 서버 생성 값(`subject`, `word`, `condition`, `color`, `optionA/B`, `a/b`, `sentence`). **사용자 제출물·댓글·캡션은 색인/검색하지 않는다.**
- Nori 는 이미 의존성에 있다(`pom.xml` 의 `lucene-analysis-nori`). `src/main/java/com/sapphire/hawe/insight/service/NoriKeywordExtractor.java` 가 `nouns(String text)` 를 제공한다 → 재사용(인터페이스/빈 구조는 구현 시 확인, 필요하면 `search` 에서 같은 빈을 주입).
- 잠금 규칙: 내가 제출하지 않은 미션의 **피드**는 볼 수 없다(`GET /api/missions/{id}/submissions` → 423). 미션 문장 자체는 피드가 아니지만, 결과에서 제출물로 연결되는 링크는 이 규칙에 위임한다.
- `missions` 컬럼: `group_id`, `content`, `category`, `template`, `params`(JSON 문자열), `slot_no`, `published_at`, `deadline_at`, `mission_date`. 그룹 멤버십은 `group_members`. "열릴 때 그룹에 있던 사람" 기준 같은 복잡한 규칙은 필요 없고 **현재 멤버 여부**만 본다.
- 응답에 점수를 노출하지 않는다.

## Key Implementation Points

1. **엔드포인트**: `GET /api/search/missions` (인증 필요). 쿼리: `q`(필수, 정규화 후 2~50자), `groupId`(선택, 지정 시 내가 멤버인 그룹이어야 함 — 아니면 403/404 중 기존 그룹 API 와 같은 방식), `limit`(기본 20, 1~50). 빈/너무 짧은/너무 긴 질의는 400 `VALIDATION_ERROR`.
2. **권한 규칙(핵심)**: 결과에 포함되는 미션 = 내가 **현재 멤버인 그룹**의 미션 중 **(마감이 지났다 OR 내가 제출했다)**. 현재 열려 있고 내가 아직 제출하지 않은 미션은 제외(답을 유도하는 힌트 노출 방지). 그룹을 나갔으면 그 그룹 미션은 검색되지 않는다.
3. **매칭**: 질의를 Nori 로 명사 토큰화(없으면 공백 분리 폴백). 각 미션의 검색 텍스트(`content` + 틀 한국어 이름 + 서버 생성 `params` 값들)를 토큰화해 질의 토큰과 겹치는 개수/가중으로 점수 계산. 정렬은 점수 내림차순 → 같은 점수는 `mission_date` 최신 순 → `id` 내림차순. 점수는 응답에 넣지 않는다. (B2 가 확장어를 가중 0.5 로 추가할 수 있도록 `Map<String token, Double weight>` 형태의 입력을 받는 서비스 메서드로 설계.)
4. **후보 조회**: 먼저 SQL 로 권한 필터를 통과한 미션 후보를 가져온다(그룹 ID 목록 + 마감/제출 조건, 최근 N개월/상한 예: 최근 1000개) → 메모리에서 토큰 매칭. 성능이 문제면 보조 테이블 `mission_search_terms(mission_id, term)` 도입은 후속(이 단위에서는 만들지 않는다 — 결정 보류).
5. **응답**: `[{missionId, groupId, groupName, content, template, missionDate, matchedTerms[], submitted}]`. `matchedTerms` 는 실제로 겹친 토큰(원 질의 토큰 위주). 대상 지정 미션의 `[대상]` 치환은 기존 미션 응답과 같은 방식으로 처리하거나, 검색에서는 닉네임을 풀지 않고 `[대상]` 그대로 두는 것 중 **프라이버시 면에서 안전한 후자**를 기본으로 한다(구현 시 기존 미션 응답 코드를 확인해 일관성 결정, 결정 내용을 보고서에 적는다).
6. **로그**: 질의 원문은 로그에 남기지 않는다(길이/건수만).

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `src/main/java/com/sapphire/hawe/search/controller/SearchController.java` (신규) | 엔드포인트 |
| `.../search/service/MissionSearchService.java` (신규) | 질의 정규화·권한 필터 호출·매칭·정렬 |
| `.../search/dto/MissionSearchResult.java` 등 (신규) | 응답 DTO |
| `.../search/dao/MissionSearchMapper.java` + XML (신규) | 권한 필터 후보 조회 |
| `docs/BACKEND/MISSIONS_API.md` | 검색 API 절 추가(요청/응답/권한 규칙) — B4 에서 최종 갱신하되 이 단위에서 초안 가능 |
| 테스트 | 아래 |

기존 컨트롤러·서비스 수정 금지(새 기능 패키지 내부 추가만). 인증/예외 처리 방식은 기존 컨트롤러 관례를 따른다.

## Constraints

- **사용자 제출물·댓글을 검색 대상에 넣지 않는다.**
- 권한 필터는 서비스가 아닌 SQL 단계에서 먼저 적용하고, 서비스에서 한 번 더 검증하지 않아도 되도록 쿼리로 보장한다(이중 방어가 필요하면 서비스에서 그룹 멤버십 재확인).
- 외부 LLM 호출 없음(B2).
- 질의 원문 로그 금지. SQL 은 파라미터 바인딩만.
- 새 마이그레이션 필요 없음(스키마 변경 금지).
- 커밋/푸시 금지.

## Edge Cases

- 결과 0건 → 빈 배열(200). (최근 미션 제안은 앱/후속에서)
- `groupId` 가 내 그룹이 아님 → 권한 오류, 존재 여부를 숨기려면 404 로 통일(기존 관례 확인).
- 한 질의에 특수문자/SQL 메타문자 → 정규화 후 토큰만 사용.
- 한글 외 질의(영문/숫자) → Nori 가 토큰화하므로 정상, 영문은 소문자화.
- 같은 문장이 여러 그룹에 존재 → 각각 별도 결과(그룹명 표시).
- `params` JSON 파싱 실패 → 해당 미션은 `content` 만으로 매칭(예외 금지).
- 대량 후보 → 상한(예: 1000개)으로 방어.

## Tests Required

- **권한 테스트(필수)**: 타 그룹 미션 비노출 / 나간 그룹 미션 비노출 / 현재 열려 있고 내가 안 낸 미션 비노출 / 마감됐으나 내가 안 낸 미션은 노출 / 내가 낸 미션은 열려 있어도 노출.
- 정규화(길이 경계 1·2·50·51자, 공백/특수문자), 400 검증.
- 매칭 순서(점수 동률 시 최신 우선), `matchedTerms`, 점수 비노출.
- `params` 에서 서버 생성 값 매칭(예: `subject` 안의 단어), 제출물 텍스트는 매칭되지 않음을 확인.
- 매퍼 테스트(H2/MariaDB 설정은 기존 `*MapperTests` 방식을 따른다).

## Validation Steps

`./mvnw test` 통과, 보고서에 권한 테스트 목록과 결과, 대상 지정 미션 문장 처리 결정을 기록. 수동: 가입한 두 계정으로 한쪽만 제출한 미션을 검색해 노출 차이 확인.

## Potential Merge Conflicts

신규 패키지라 낮음. `MISSIONS_API.md`(문서), 보안 설정(`SecurityConfig`)에서 새 경로가 인증 대상인지 확인(기본 인증 필요일 가능성이 높아 수정 불필요).
