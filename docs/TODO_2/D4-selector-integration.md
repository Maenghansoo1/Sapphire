# D4. 멀티 암드 밴딧 — 틀 선택기 통합 (스위치 기본 off)

> 범위: 백엔드 `mission` 패키지. **선행: D2(통계), D3(반영).** D1 의 prior JSON 은 선택. 근거: `AI_FEATURES.md` §4.3·§4.6, `docs/BACKEND/MISSIONS_API.md` §6-1. 빌드/테스트: `./mvnw test`.

## Goal

틀 선택에서 후보 집합이 정해진 뒤의 **"무작위 선택" 자리를 계층 Thompson Sampling 으로 교체**한다. 기본값은 `random`(기존 동작·기존 회귀 테스트 그대로). 켜면 그룹별로 참여·정성이 높은 틀 위주로 발행하되 규칙 1~3·5 와 탐색 하한을 지킨다.

## Findings (코드로 확인한 선택 구조)

`src/main/java/com/sapphire/hawe/mission/domain/MissionTemplateSelector.java` — 상태 없는 순수 정적 유틸이다.
- `select(groupId, date, slotNo, dailyMissionCount, yesterdayMedia, pool)`:
  1. 풀을 정렬하고 매체 순서(`PHOTO, TEXT, DRAW`)를 `seed(groupId, date, 0)` 로 섞는다.
  2. **하루 1개**: 섞인 매체 순서에서 `yesterdayMedia` 에 없는 첫 매체를 고르고(없으면 첫 매체), 그 매체 틀들 중 `seed(groupId,date,slotNo)` 로 무작위 1개.
  3. **하루 2개 이상**: 슬롯 번호로 매체를 순환(`position = (slotNo-1) % 매체수`)하고, 그 매체의 시작 틀을 `seed(groupId,date,position+1)` 로 정한 뒤 `round = (slotNo-1)/매체수` 만큼 다음 틀로 넘긴다(같은 매체가 다시 오면 다음 틀).
- 호출: `MissionPublisher.java` 약 147행 `MissionTemplateSelector.select(groupId, date, slotNo, group.getDailyMissionCount(), yesterdayMedia, properties.enabledTemplates())`. `yesterdayMedia` 는 어제 미션의 틀 → 매체로 계산된다. 대상 지정(`needsTarget`)과 AI 생성은 틀 선택 **뒤** 단계라 영향받지 않는다.
- 기본 풀 7종: PHOTO 3(`PHOTO_NOW`,`PHOTO_GALLERY`,`PHOTO_COLOR`), DRAW 2(`DRAW_SKETCH`,`DRAW_A_TO_B`), TEXT 2(`TEXT_ACROSTIC`,`TEXT_FILL_BLANK`).
- **그래서 밴딧이 실제로 고를 수 있는 범위(정직한 한계)**:
  - 하루 1개: 어제 매체를 뺀 **매체들의 틀 전체**(최대 5개 중). 매체 선택 자체가 밴딧의 영향을 받는다 → 규칙 3(어제와 같은 매체 금지)은 그대로 지켜진다.
  - 하루 2개 이상: 슬롯별 매체는 순환 규칙(규칙 2)이 고정하므로 **그 매체 안의 시작 틀(PHOTO 3개 중 1, 나머지 매체 2개 중 1)** 만 고를 수 있다. 같은 매체가 같은 날 다시 오면 기존대로 다음 틀(`start + round`)을 쓴다.
  - 즉 `docs/FEATURE/AI_FEATURES.md` §4.6 의 "후보 집합 안 선택 교체"는 맞지만 **선택 자유도가 작다**. 시뮬레이션(D1)에 같은 제약을 넣어 기대 이득을 현실적으로 평가한다.
- 규칙 4(관계별 선호·가중치 없음)는 확정: 밴딧은 **관계를 입력으로 쓰지 않는다**. 선택은 그룹별 실측과 관계 무관 전역 prior 만으로 한다.
- 같은 날 슬롯 간 일관성: 각 슬롯은 발행 시각이 달라도 같은 날 같은 통계를 읽어야 한다(D3 가 새벽 1회만 갱신). 다중 슬롯의 "시작 틀"은 `seed(groupId,date,position+1)` 과 같은 시드로 표본을 뽑아 슬롯마다 재계산해도 같은 결과가 나오게 한다.

## Key Implementation Points

1. **선택기는 순수 유지**: `MissionTemplateSelector` 에 오버로드 추가 — `select(..., pool, TemplateSampler sampler)`. `TemplateSampler` = `double score(MissionTemplate template, long seed)`(결정적: 같은 `(template, seed, 통계 스냅샷)` → 같은 값). `sampler == null` 이면 **기존 코드 경로 그대로**(회귀 보장).
2. **후보 구성은 기존 로직 재사용**: 하루 1개 → 후보 = (어제 매체 제외, 후보 부족 시 전체) 매체들의 틀. 하루 2개↑ → 후보 = 해당 매체의 틀들. 후보가 1개뿐이면 샘플링 없이 그것.
3. **Thompson Sampling** (`BanditSampler`, `mission/service` 또는 `domain`): 후보 틀마다 그룹 posterior 를 구성
   - `α = κ·(전역 평균 α/(α+β)) + alpha_real_group (+ 합성 prior 기여)`, `β` 동일 방식(수축). 합성 기여 = `K_syn·exp(−N_real/N0)` 를 prior 의사 관측에 곱함(저장은 원값).
   - 시드 `RandomGenerator`로 Beta 표본을 뽑아 점수 = 표본 × 반복 패널티(`repeatPenalty` ×0.85, 최근 3일 안에 같은 틀 사용 시; 최근 틀 목록은 `MissionPublisher` 가 이미 읽는 `findTemplatesByGroupIdAndDate` 계열로 3일치 확보 — 쿼리 추가 필요하면 최소로).
   - 최고 점수 틀 선택. **탐색 하한 ε**: 시드 기반 `u < ε` 이면 후보 중 무작위(규칙: 어떤 후보의 선택 확률도 ≥ ε/후보수).
   - 점유율 상한(결정 6 기본 7일 창 40%): 해당 틀의 최근 7일 점유율이 상한을 넘으면 그 틀의 점수를 0 으로 눌러 다른 후보를 우선(후보가 모두 초과면 상한 무시).
   - 신규 틀(통계 행 없음) → `Beta(1,1)` 낙관 시작.
4. **`selection_prob` 기록**: 선택 시점의 근사 확률(몬테카를로: 같은 통계로 시드를 바꿔 N=200 번 시뮬레이션해 선택 빈도 + ε 항, 또는 해석적 근사). 목적은 D5 의 역확률 가중(IPS) 보정이므로 근사 정확도는 상대 오차 ±0.05 면 충분. 정책이 `random` 이면 `1/후보수`, `bandit` 이면 위 값.
5. **정책 스위치**: `hawe.mission.template-policy=random|bandit`(기본 `random`, `MissionProperties`/`BanditProperties` 에 추가). 정책 값과 `selection_prob` 를 미션 저장 시 `missions.template_policy`/`selection_prob` 에 기록(D2 컬럼). 강제 템플릿 지정(`requestedTemplate != null`, 관리/테스트 경로)은 `template_policy='FORCED'` 또는 NULL 로 두어 분석에서 제외.
6. **그룹 신규(통계 없음)**: 전역 prior 만으로 진행 → 사실상 탐색. 그룹의 닫힌 미션 수 `N_g < N_min`(초안 10)이면 전역 평균에 가깝게 수축됨(별도 분기 불필요).
7. **일관성**: 하루 안에서 슬롯 간 표본이 흔들리지 않도록 시드는 `seed(groupId, date, ·)` 만 사용(현재 시각 사용 금지).

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `.../mission/domain/MissionTemplateSelector.java` | `TemplateSampler` 를 받는 오버로드(기존 시그니처·동작 불변) |
| `.../mission/domain/TemplateSampler.java` (신규, 인터페이스) | 점수 함수 |
| `.../mission/service/BanditSampler.java` (신규) | Thompson 표본·패널티·ε·점유율 상한·`selection_prob` |
| `.../mission/service/MissionPublisher.java` | 정책이 `bandit` 이면 sampler 구성 후 새 오버로드 호출, `template_policy`/`selection_prob` 저장 |
| `.../mission/domain/Mission*.java`, 미션 INSERT 매퍼 | 새 컬럼 저장 |
| `application.properties`, `BanditProperties` | `hawe.mission.template-policy`, 관련 설정 |
| 테스트 | 아래 |

## Constraints

- **기본 `random` 일 때 기존 선택 결과가 비트 단위로 같아야 한다**(기존 `MissionTemplateSelector` 테스트·`MissionPublisher` 테스트 전부 불변 통과).
- 규칙 2(매체 순환)·규칙 3(어제 매체 제외, 후보 부족 예외)·규칙 5(대상 지정)를 깨지 않는다. 관계 유형을 입력으로 쓰지 않는다.
- 선택은 `(groupId, date, slotNo)` 와 통계 스냅샷에 대해 결정적이어야 한다(현재 시각·전역 난수 금지).
- 발행 트랜잭션 안에서 외부 호출 없음. DB 조회 추가는 그룹의 팔 통계 + 최근 3~7일 틀 이력만(쿼리 수 최소).
- 통계 조회 실패 시 **random 으로 폴백**(발행을 막지 않는다), 사유만 WARN.
- 커밋/푸시 금지.

## Edge Cases

- 후보 1개 → 샘플링 없이 선택, `selection_prob=1.0`.
- 풀이 1~2개로 설정됨(`hawe.mission.enabled-templates`) → 후보가 없거나 1개: 기존 후보 부족 규칙을 따르고 밴딧은 무의미해도 오류 없음.
- 그룹 삭제 중 발행 → 통계 조회 없음 → random 폴백.
- 새 틀이 풀에 추가됨(A4 등으로 `enabled-templates` 확장) → 통계 행 없음 → Beta(1,1).
- 하루 여러 슬롯에서 같은 매체가 반복 → 기존 `start + round` 규칙 유지(같은 날 같은 틀이 겹치지 않음).
- `repeatPenalty` 가 모든 후보에 적용(최근 3일 모두 사용) → 상대 비교는 유지되므로 문제없음.

## Tests Required

- **회귀**: `sampler == null`/정책 `random` 에서 기존 테스트 전체 통과, 임의의 (그룹,날짜,슬롯) 1000개에 대해 새 오버로드(null sampler)와 기존 `select` 결과 동일.
- 제약: 어제 매체 제외 준수, 매체 순환 준수(하루 2개↑), 같은 날 같은 매체 재등장 시 다음 틀.
- 결정성: 같은 입력·통계 → 같은 틀.
- 탐색: 시드 1만 개로 모든 후보가 ε/후보수 이상 선택됨(통계적 허용오차 안).
- 학습: 한 틀의 통계를 압도적으로 좋게 만들면 해당 틀 선택 비율이 크게 오름(예: > 60%), 점유율 상한이 이를 40% 근처로 제한.
- 신규 틀 Beta(1,1) 시작, 통계 조회 실패 시 random 폴백, `selection_prob` 범위(0,1] 와 합이 대체로 1(후보별 확률 합 검증).

## Validation Steps

`./mvnw test` 통과. 보고서에 (a) 기본 off 회귀 증거, (b) 위 "선택 자유도" 분석이 코드와 일치함, (c) 정책 on 으로 로컬에서 14일치 발행을 시뮬레이션했을 때의 틀 분포 표. D1 시뮬레이터가 있으면 같은 제약으로 결과를 대조.

## Potential Merge Conflicts

`MissionTemplateSelector.java`, `MissionPublisher.java`(다른 미션 발행 작업·프롬프트 개선 작업과 동시 수정 금지 — 순차), 미션 INSERT 매퍼 XML, `application.properties`.
