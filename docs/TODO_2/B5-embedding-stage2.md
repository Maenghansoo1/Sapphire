# B5. AI 미션 검색 — (선택) 임베딩 2단계

> **선택 단위.** B4 평가에서 질의 확장만으로 부족할 때만 진행한다. Qdrant 는 도입하지 않는다(`CLAUDE.md` 의 MVP 제외 방침). 근거: `AI_FEATURES.md` §2.2·§2.4.

## Goal

미션 문장을 임베딩으로 미리 계산해 DB 에 저장하고, 질의 임베딩과의 코사인 유사도로 의미 검색을 보강한다(하이브리드: 1단계 매칭 점수 + 의미 유사도).

## Findings

- 색인 대상은 미션 문장(서버·AI 생성)뿐이라 사용자 콘텐츠가 외부로 나가지 않는다. **질의**도 임베딩 시 외부로 나가므로 B2 와 같은 고지가 필요하다.
- 규모(수백~수천 문장)에서는 벡터 DB 없이 DB 컬럼 + 앱 메모리 코사인으로 충분하다.
- 임베딩 제공자는 아직 정해지지 않았다: 외부 API(예: Voyage/Bedrock/OpenAI 계열) 또는 로컬 모델. 선택은 이 단위의 첫 결정 → **구현 전에 사용자 확인 필요**(비용·키·한국어 품질).
- 비동기 계산은 `AsyncConfig`(`src/main/java/com/sapphire/hawe/global/config/AsyncConfig.java`)의 실행기 방식을 쓰거나 `IMPROVEMENTS.md` §1(공통 비동기 방식) 결정을 따른다.

## Key Implementation Points

1. 테이블 `mission_embeddings(mission_id PK FK missions, model VARCHAR, dim INT, vector BLOB 또는 JSON, created_at)` — 새 마이그레이션 파일(`V*`, 최신 번호 확인) 또는 팀의 V1 직접 수정 방식.
2. 색인: 미션 발행 커밋 후 비동기로 임베딩 계산·저장(실패해도 발행 영향 없음, 재시도 1회), 기존 미션 백필 잡(천천히, 호출 제한 준수).
3. 질의: 질의 임베딩 계산(캐시 사용) → 권한 필터를 통과한 후보들과 코사인 계산 → 1단계 점수와 합산(가중치는 평가로 결정, 초안 0.6 lexical + 0.4 semantic) → 정렬.
4. 모델/차원이 바뀌면 재색인 가능하도록 `model` 컬럼으로 구분.
5. 그룹 삭제 시 `mission_embeddings` 를 미션보다 먼저 삭제(기존 삭제 순서 규칙).

## Files / Functions to Change

`search/service/EmbeddingClient`(인터페이스 + 구현), `MissionEmbeddingIndexer`, 매퍼 XML, 마이그레이션, 그룹 삭제/미션 삭제 서비스(삭제 순서), `application.properties`, `.env.example`.

## Constraints

- Qdrant/외부 벡터 DB 도입 금지. 사용자 제출물 임베딩 금지.
- 임베딩 호출 키/본문 로그 금지. 호출 제한·캐시 필수.
- 권한 필터를 우회하지 않는다(후보는 항상 B1 의 권한 쿼리를 통과한 것).
- 커밋/푸시 금지.

## Edge Cases

- 임베딩 서비스 장애 → 1단계 결과로 폴백.
- 벡터 차원 불일치/모델 혼재 → 같은 `model` 끼리만 비교.
- 백필 중 신규 미션 → 멱등 upsert.

## Tests Required

코사인 계산 단위 테스트, 폴백, 권한 필터 불변(B1 권한 테스트 재실행), 삭제 순서, 백필 멱등.

## Validation Steps

`./mvnw test` + B4 평가 세트로 1단계 대비 개선 수치 비교. 개선이 없으면 이 단위는 취소하고 이유를 기록.

## Potential Merge Conflicts

마이그레이션 파일, `AsyncConfig`, 그룹/계정 삭제 서비스.
