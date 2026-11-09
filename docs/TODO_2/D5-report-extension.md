# D5. 멀티 암드 밴딧 — B9 실험 리포트 확장 (정책 층화 + 역확률 가중)

> 범위: 백엔드 리포트. **선행: D4**(`missions.template_policy`, `selection_prob` 기록). 근거: `AI_FEATURES.md` §4.7, `docs/BACKEND/EXPERIMENT_TOPIC.md`. 빌드/테스트: `./mvnw test`.

## Goal

기존 `GET /internal/experiments/topic` 리포트를 건드리지 않고, **틀 선택 정책(RANDOM vs BANDIT)별 참여율 비교**를 낼 수 있는 내부 리포트를 추가한다. 가능하면 역확률 가중(IPS)으로 선택 편향을 보정한 추정을 함께 낸다.

## Findings

- 기존 구성: `ExperimentReportController`(`GET /internal/experiments/topic`, 헤더 `X-Admin-Token`, 틀리거나 미설정이면 404), `ExperimentReportService`(닫힌 실험 미션만, 지표: 참여율·마감 전 제출 비율·미션당 반응+댓글·첫 제출 중앙값), `ExperimentStats`(Wilson, z 검정, t 구간, 부트스트랩, 부호 검정을 직접 구현), `dto/ExperimentReport.java`. 대상 인원/닫힘 정의는 `EXPERIMENT_TOPIC.md` §3.
- B9 가 비교하는 건 **주제 문장 출처(AI vs RANDOM)** 이고, 틀 분포는 두 군에서 같다는 전제다. D4 의 밴딧은 두 군에 같은 선택기를 쓰므로 전제 유지. 이제 **틀 선택 정책**이 두 번째 축이 된다.
- 정책 비교를 신뢰성 있게 하려면 (그룹, 날짜) 단위로 정책을 무작위 배정(B9 와 같은 방식)하는 것이 이상적이다. D4 의 기본 설계는 정책을 **전역 스위치**로 두므로, 일부 날만 `RANDOM`/`BANDIT` 으로 나누는 배정이 필요하면 이 단위에서 **배정 로직(해시 기반, `ai-ratio` 와 유사한 `bandit-ratio`)** 을 D4 의 정책 결정 지점에 추가하는 후속이 필요하다 → 열린 결정(아래). 이 단위의 기본 범위는 **이미 기록된 `template_policy`로 사후 비교**하는 것이며, 배정 없이 단순 비교는 교란(시기·그룹 특성)에 취약하다는 경고를 리포트에 붙인다.

## Key Implementation Points

1. 엔드포인트 `GET /internal/experiments/template-policy?from=&to=&format=json|md` — 인증/404 규칙은 기존 컨트롤러와 동일(`X-Admin-Token`, `hawe.experiment`/`HAWE_ADMIN_TOKEN` 재사용). 기간 366일 미만, 형식 오류 400.
2. 지표: 정책별(`RANDOM`/`BANDIT`) 참여율(제출 합 ÷ 대상 인원 합), 마감 전 제출 비율, 미션당 반응+댓글, **틀 점유율**, **그룹 안 비교**(같은 그룹이 두 정책을 모두 겪은 경우의 평균 참여율 차이, 기존 `ExperimentStats` 의 대응 비교/부트스트랩 재사용).
3. **IPS 추정(선택 단계)**: `selection_prob` 가 있는 미션에 대해 가중 평균 참여율 `Σ(participation / p) / Σ(1 / p)`(Hajek 추정) 을 정책별로 계산. `p` 가 너무 작은 미션은 가중치 상한(예: 20)으로 클램프하고 클램프된 비율을 경고로 표시. 표본이 작으면 `underpowered: true` + 경고(기존 B9 기준 재사용: 그룹 < 5 또는 정책별 미션 < 30).
4. 결과 JSON 필드: `random`, `bandit`(지표 묶음), `difference`, `paired`, `ips`, `templateShare`(정책별 틀 점유율), `underpowered`, `warnings`. `format=md` 는 발표용 Markdown(예시 문장은 테스트 데이터임을 명시).
5. **리포트에 사람·그룹을 알아볼 값(ID·닉네임·이름) 없음.** 그룹별 값은 익명 인덱스 또는 집계만.
6. 정책 값이 NULL 인 미션(밴딧 도입 전/강제 템플릿)은 제외하고 개수만 표시.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `.../mission/controller/ExperimentReportController.java` | 새 엔드포인트 추가(기존 엔드포인트 불변) |
| `.../mission/service/…` 새 `TemplatePolicyReportService` 또는 `ExperimentReportService` 확장 | 지표·층화·IPS |
| `.../mission/dto/TemplatePolicyReport.java` (신규) | 응답 모델 |
| `.../mission/dao` 실험 매퍼 XML | 정책별 닫힌 미션 조회 쿼리 |
| `docs/BACKEND/EXPERIMENT_TOPIC.md` 또는 새 `docs/BACKEND/EXPERIMENT_TEMPLATE_POLICY.md` | 가설·지표·한계 |
| 테스트 | 아래 |

## Constraints

- 기존 `GET /internal/experiments/topic` 응답/동작을 바꾸지 않는다.
- 관리자 토큰 보호 유지(404 규칙). 사용자 JWT 로는 열리지 않는다.
- 통계는 기존 `ExperimentStats` 재사용, 새 라이브러리 추가 금지.
- 표본이 작을 때 "경향" 이상 주장하지 않는다(경고 문구).
- 커밋/푸시 금지.

## Edge Cases

- 한 정책의 미션이 0건 → 해당 지표 `null`, 비교 생략, 경고.
- `selection_prob` NULL → IPS 에서 제외하고 개수 표시.
- 시기 교란(밴딧이 늦게 켜진 경우) → 같은 그룹 안 비교와 전후 기간 표기를 병기하고 경고.
- 날짜 경계/타임존: 기존 `SeoulTime` 기준.

## Tests Required

- 정책별 집계(수동 계산 값과 일치), 그룹 안 비교, Hajek 추정(작은 손계산 예시), 가중치 클램프 경고, underpowered 판정, 인증/404/400 규칙, 응답에 식별 값이 없음.
- 기존 B9 리포트 테스트 전부 불변.

## Validation Steps

`./mvnw test` 통과. 보고서에 정책 배정 방식(전역 스위치 vs 해시 배정)에 대한 현재 상태와 필요한 후속 결정을 기록.

## 열린 결정

- 정책을 (그룹, 날짜) 단위로 무작위 배정할지(`hawe.mission.template-policy-ratio`) — 인과 비교에는 필요. 기본 제안: 평가 기간에는 켠다. 필요하면 D4 후속 단위로 분리.

## Potential Merge Conflicts

`ExperimentReportController`, `ExperimentReportService`, 실험 매퍼 XML.
