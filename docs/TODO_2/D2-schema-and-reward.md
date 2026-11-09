# D2. 멀티 암드 밴딧 — 스키마 + 보상 계산 (서버)

> 범위: 백엔드. `mission` 패키지에 보상 계산·통계 저장 추가. **선택기 통합은 D4, 주기 반영은 D3.** 근거: `AI_FEATURES.md` §4.2·§4.5. 빌드/테스트: `./mvnw test`(Java 25).

## Goal

닫힌 미션 하나에서 **보상 합 R 과 대상 인원 n** 을 계산하는 서비스와, 팔(틀)별 Beta 통계를 저장하는 스키마를 만든다. 아직 선택에는 쓰지 않는다.

## Findings

- 닫힌 미션·대상 인원 계산은 B9 실험 리포트가 이미 한다: `src/main/java/com/sapphire/hawe/mission/service/ExperimentReportService.java` 가 `experiments.findClosedExperimentMissions(from, to, now)` 로 행을 읽고 `eligible`(대상 인원), `submitted`, `onTime`, `engagement`(반응+댓글) 를 센다. 대상 인원 정의(`docs/BACKEND/EXPERIMENT_TOPIC.md` §3): 미션이 열릴 때 그룹에 있던 사람 + 그 미션에 낸 뒤 나간 사람(달력과 같은 기준). **같은 정의를 재사용**하고 새로 만들지 않는다 — 해당 쿼리/매퍼를 확인해 실험 군 조건(`experiment_arm`)과 분리된 일반 버전이 필요하면 추가한다.
- "닫힌" 기준(B9): 마감이 지났고 같은 슬롯에 다음 미션이 열렸거나 없어진 슬롯. 제출이 더 들어올 수 있는 미션은 반영하지 않는다.
- 제출 데이터: `submissions` — `media_url`, `number_value`(별점), `choice_value`, `answer_data`(JSON, 글 본문 `{"text":...}`), `caption`, `submitted_at`, `is_late`. 사진 적합도: `submission_ai_reviews.fit_score`(DB 에만 있고 API 비노출 — 서버 내부 계산에는 사용 가능, 결과 점수를 사용자에게 노출하지 않는다).
- 보상 식(기본): 미제출 `r=0`, 제출 `r = 0.6 + 0.2·effort + 0.2·engage` (모두 0~1 클램프). 가중치는 설정으로 조정 가능(`hawe.bandit.reward.*`).
  - `effort`(틀별 정규화): 글 틀(`TEXT_*`) — 본문 길이를 틀별 상한(예: 삼행시 3줄 합 30자, 빈칸 채우기 40자)으로 나눠 클램프 + 캡션 작성 시 가산; 사진 틀 — `fit_score` 가 있으면 B10 표시 환산(0→0, good→60, perfect→80, ≥0.5→100)/100, 없으면 0.5(중립); 그림 틀 — **결정 4 기본값: 사용하지 않음(중립 0.5)**.
  - `engage`: 제출물이 받은 반응+댓글 수 ÷ (그 미션 제출물들의 평균 + 1) 을 `min(1, x/2)` 로 완만하게 클램프(인기 투표화 방지). 그룹 크기가 작은 경우(≤3) 분모 보정.
- 스키마(AI_FEATURES.md §4.5): `template_bandit_stats`, `template_bandit_applied`, `missions.template_policy`, `missions.selection_prob`.
- 마이그레이션: 현재 `src/main/resources/db/migration/` 에 `V1`~`V4` 가 있다. 팀 방식은 "V1 직접 수정 + 로컬 DB 재생성"이지만 이후 V3·V4 가 별도 파일로 추가돼 있으므로 **새 파일(`V5__template_bandit.sql`, 번호는 구현 시 최신 확인)** 로 추가하는 것을 기본으로 하고, 방식이 다르면 팀 방식을 따른다(보고서에 선택 이유 기록).
- 그룹 삭제/탈퇴 삭제 순서: 통계·적용 기록은 `missions`/`groups` 보다 먼저 삭제해야 한다(기존 삭제 쿼리 위치 확인: 그룹 삭제 서비스, 계정 탈퇴 서비스).

## Key Implementation Points

1. **스키마**
   ```sql
   template_bandit_stats(
     id BIGINT PK AUTO_INCREMENT,
     scope VARCHAR(10) NOT NULL,            -- 'GLOBAL' | 'GROUP'
     group_id BIGINT NULL,                  -- GLOBAL 이면 NULL, FK groups ON DELETE 없음(서비스가 먼저 삭제)
     template VARCHAR(40) NOT NULL,
     alpha_real DOUBLE NOT NULL DEFAULT 0, beta_real DOUBLE NOT NULL DEFAULT 0,
     alpha_syn  DOUBLE NOT NULL DEFAULT 0, beta_syn  DOUBLE NOT NULL DEFAULT 0,
     n_missions INT NOT NULL DEFAULT 0,
     updated_at DATETIME NOT NULL,
     UNIQUE (scope, group_id, template))   -- MariaDB UNIQUE 는 NULL 중복 허용에 유의: GLOBAL 은 group_id 를 0 센티넬 또는 별도 유니크 처리
   template_bandit_applied(mission_id BIGINT PK, applied_at DATETIME NOT NULL, FK missions)
   ALTER missions ADD template_policy VARCHAR(10) NULL, ADD selection_prob DOUBLE NULL;
   ```
   (`UNIQUE` 의 NULL 처리 문제는 `group_id` 를 NOT NULL + GLOBAL 은 `0` 센티넬로 두거나, GLOBAL 을 별도 테이블로 분리하는 것 중 **더 단순한 쪽**을 구현자가 선택하고 이유를 보고서에 적는다. FK 는 GROUP 행에만 의미가 있으므로 센티넬 방식이면 FK 를 걸지 않는다.)
2. **보상 계산 서비스** `BanditRewardCalculator`(mission/domain 또는 service): 입력 = 닫힌 미션 한 건의 집계 행(대상 인원, 멤버별 제출 여부·본문 길이·캡션 여부·fit_score·받은 반응+댓글 수), 출력 = `Reward(missionId, template, groupId, n, R)`. **순수 함수**로 분리해 단위 테스트(SQL 은 매퍼가 별도로 제공).
3. 매퍼: 닫힌 미션 목록(아직 `template_bandit_applied` 에 없는 것, 기간/상한 인자) + 미션별 멤버 제출 집계 조회. 대량이 되지 않게 `LIMIT`.
4. 통계 repository: 팔 통계 upsert(`alpha_real += R`, `beta_real += n − R`, `n_missions += 1`), 전역·그룹 두 행에 함께 반영, 조회(그룹의 팔 통계 + 전역 통계 + 합성 prior).
5. 설정 클래스 `BanditProperties`(`hawe.bandit.*`): 가중치 `0.6/0.2/0.2`, 클램프 상한, `kappa`(10), `kSyn`(10), `epsilon`(0.05), `repeatPenalty`(0.85, 3일), `halfLifeDays`(30), 팔 점유율 상한(0.4, 7일). 기본값을 코드/`application.properties` 에 둔다. D3·D4 가 이 설정을 쓴다.
6. 합성 prior 로딩: D1 산출 JSON(`synthetic_prior.json`)을 클래스패스 리소스로 읽어 `alpha_syn/beta_syn` 에 **초기 1회 시드**(없으면 0 으로 두고 경고 로그). D1 이 아직 없으면 prior 없이도 동작해야 한다(선택 의존).

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `src/main/resources/db/migration/V5__template_bandit.sql` (신규, 번호 확인) | 스키마 |
| `.../mission/domain/BanditReward.java`, `BanditRewardCalculator.java` (신규) | 보상 계산(순수) |
| `.../mission/dao/…BanditMapper.java` + XML (신규; 기존 `dao` 규칙 따라 위치 결정) | 닫힌 미션 집계·통계 upsert |
| `.../global/config/BanditProperties.java` (신규) | 설정 |
| 그룹 삭제 서비스, 계정 탈퇴 서비스 | `template_bandit_stats(scope=GROUP)`, `template_bandit_applied` 삭제를 미션/그룹 삭제 앞에 추가 |
| `application.properties` | `hawe.bandit.*` 기본값(주석) |
| 테스트 | 아래 |

기존 `MissionTemplateSelector`/`MissionPublisher` 는 이 단위에서 수정하지 않는다.

## Constraints

- 전역 통계·리포트에는 그룹을 식별할 값이 없다(집계만).
- 점수(fit_score)는 서버 내부 계산에만 쓰고 API 에 노출하지 않는다(B10 규칙).
- 지연 보상: **닫힌 미션만** 반영한다.
- 외부 호출 없음.
- 마이그레이션은 기존 파일을 수정하지 않고 새 파일을 추가(체크섬 불일치 방지)가 기본.
- 커밋/푸시 금지.

## Edge Cases

- 대상 인원 0(이론상) → 반영 건너뜀(0 나눗셈 방지).
- `R > n` (클램프 오류) → `min(R, n)`.
- 지각 제출 → 제출로 센다(도장 기준과 동일), `engage` 와 `effort` 에서는 불이익 없음.
- 같은 미션이 두 번 반영되지 않게 `template_bandit_applied` 로 멱등(동시 실행 시 PK 충돌을 정상 흐름으로 처리).
- 탈퇴한 멤버의 제출 → 대상 인원 정의(B9)와 동일하게 처리.
- 새 틀은 통계 행이 없음 → 조회 시 기본값 반환(행은 첫 반영 때 생성).

## Tests Required

- `BanditRewardCalculator` 순수 테스트: 전원 미제출(R=0), 전원 제출 최소 정성(R=0.6n), 최대(R=n), 클램프, 사진 `fit_score` 없음/있음 매핑, 그림 틀 중립, 글 길이 상한, engage 완만 클램프, 그룹 크기 ≤3 보정.
- 매퍼 테스트: 닫힌 미션만 조회, 이미 반영된 미션 제외, upsert 누적, GLOBAL/GROUP 동시 반영.
- 삭제 순서: 그룹 삭제/탈퇴가 통계 때문에 FK 오류를 내지 않음.

## Validation Steps

`./mvnw test` 통과. 보고서에 마이그레이션 방식 선택 이유, NULL 유니크 처리 방식, 대상 인원 쿼리 재사용 여부, 설정 키 목록 기록.

## Potential Merge Conflicts

마이그레이션 디렉터리(다른 스키마 작업과 번호 충돌 — 푸시 전 최신 pull), 그룹 삭제/탈퇴 서비스, `application.properties`.
