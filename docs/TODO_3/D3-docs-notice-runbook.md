# D3. 문서 갱신, 개인정보 고지 초안, 운영 런북

> 범위: 문서만. **선행: D2**(실제 동작 확인 후 문서를 구현에 맞춘다). 근거: `BLOCKCHAIN_BADGES.md` §6·§13·§16.

## Goal

구현·검증이 끝난 내용을 기존 문서에 반영하고, **개인정보 처리방침 문구 초안**과 **키 교체·장애 대응 런북**을 만든다. 설계 초안(`BLOCKCHAIN_BADGES.md`)을 구현과 일치시킨다.

## Findings

- 갱신 대상 문서: `docs/BACKEND/BACKEND_DB.md`(테이블: `user_badges`, `badge_mint_jobs`, `user_chain_accounts`, `chain_burn_jobs`, 삭제 순서), `docs/BACKEND/API_DESIGN.md`/`MISSIONS_API.md` 형식의 API 절(`GET /api/me/badges`, `GET /api/badges/catalog`, `GET /badges/meta/{code}.json`, 내부 `/internal/badges/*`), `docs/FEATURE/PROJECT_OVERVIEW.md`(기능 소개·AI/체인 역할 원칙), `docs/FEATURE/BLOCKCHAIN_BADGES.md`(상태를 "설계 초안"에서 "구현됨"으로, 구현과 달라진 점 정정).
- 구현 중 달라질 가능성이 큰 곳: 사용자 주소 할당 방식(C2: 키 폐기 방식 vs HD 파생), 라이브러리 선택(C1), 알림 타입 처리(A4), 마이그레이션 방식, 상태 정규화 이름. 문서는 **실제 구현**을 따른다.
- 정직한 표현 규칙(BLOCKCHAIN_BADGES.md §2): 블록체인은 발급 기록의 변조 방지·이식 가능한 증명을 주고, **판정·발급 주체는 서버**라는 점을 숨기지 않는다. "탈중앙 보증"·"영구 보관" 같은 과장 금지.
- 처리방침/약관 문구 확정은 팀의 몫(초안만 제공). 스토어 정책 확인은 사용자 작업.

## Key Implementation Points

1. **데이터/API 문서 갱신**: 위 테이블·엔드포인트·상태 정규화(`RECORDING|ON_CHAIN|FAILED`)·에러·권한을 실제 코드와 대조해 작성. 응답 예시는 실제 응답에서 복사(주소·키 없음 확인).
2. **개인정보 고지 초안**(팀 확정 필요 표시):
   - 업적 달성 기록이 **공개 블록체인에 익명 주소와 함께** 남을 수 있다는 점(이름·이메일·그룹·제출 내용은 올라가지 않음).
   - 탈퇴 시 서버의 연결 정보가 삭제되고 배지 토큰이 소각되며, **과거 발급 기록은 체인에 남지만 개인을 식별할 수 없다**.
   - 테스트넷 사용 시 서비스 종료/초기화로 온체인 기록이 사라질 수 있음.
   - 사용자에게 지갑·비용·가상자산 거래를 요구하지 않으며 배지는 양도할 수 없고 금전적 가치가 없음.
3. **운영 런북** `docs/INFRA/BADGE_CHAIN_RUNBOOK.md`(기존 `DEPLOY_RUNBOOK.md` 스타일): 
   - 설정/환경변수 목록(이름만)과 켜기/끄기 절차(D2 단계표 링크).
   - **발급 키 교체**: 새 키 생성 → `rotate-minter`(B2) 로 역할 이동 → 서버 환경변수 교체 → 재기동 → 이전 키 회수 확인. 키 유출 시 즉시 역할 회수.
   - **잔액 부족 대응**(faucet/충전), RPC 제공자 장애·키 교체, 속도 제한.
   - **`DEAD` 잡 처리**: 원인별(revert/주소/컨트랙트 없음) 조치, 수동 재시도 방법.
   - **테스트넷 초기화/컨트랙트 교체**: 재배포(B2) → `contract-address`/`chain-id` 변경 → **재발급 모드를 명시적으로 켠 후** 기존 `CONFIRMED` 를 `EARNED` 로 되돌리는 절차(자동 대량 재발급 금지, C4).
   - **오발급 정정**: 원인 수정 → 대상 선정 → `burn` → 재판정. 소각도 온체인에 남는 점 안내.
   - 모니터링: `GET /internal/badges/chain-status` 확인 항목과 경고 로그 키워드.
   - 백업/복구: `user_badges`/`user_chain_accounts` 는 사용자↔주소 매핑이므로 **백업 보호 수준을 DB 와 동일하게** 취급.
4. **`BLOCKCHAIN_BADGES.md` 정리**: 상태·결정(§11)을 확정값으로 갱신, §6 주소 할당 방식을 구현 기준으로 정정, §15 순서에 완료 표시.
5. **앱 스토어 체크리스트**: 지갑·코인·거래 기능이 없는 "업적 표시"임을 심사 설명 초안, 외부 링크(탐색기) 사용 설명.

## Files / Functions to Change

`docs/BACKEND/BACKEND_DB.md`, `docs/BACKEND/API_DESIGN.md`(또는 `MISSIONS_API.md`), `docs/FEATURE/PROJECT_OVERVIEW.md`, `docs/FEATURE/BLOCKCHAIN_BADGES.md`, `docs/INFRA/BADGE_CHAIN_RUNBOOK.md`(신규), (필요 시) `.env.example` 주석.

## Constraints

- 코드 변경 없음. `CLAUDE.md`/`AGENTS.md` 수정 금지.
- 처리방침 최종 문구는 팀이 확정 — "초안" 표시.
- 문서에 실제 키·RPC URL·주소 비밀값을 쓰지 않는다(컨트랙트 공개 주소는 가능하나 환경별로 분리 기재).
- 커밋/푸시 금지.

## Edge Cases

- 구현이 설계 초안과 달라진 부분 → 문서가 구현을 따르고 달라진 이유를 한 줄 기록.
- 문서 간 설정 키 이름 불일치 → `application.properties`/`ChainProperties` 실제 키로 통일.
- 테스트넷 → 메인넷 이전 시 변경점은 "별도 결정"으로 남김.

## Tests Required / Validation Steps

문서 내 설정 키·엔드포인트·테이블/컬럼명이 코드와 일치하는지 대조(보고서에 대조 결과). 링크 유효성. 런북 절차 중 키 교체와 재배포 절차는 로컬 노드에서 한 번 리허설했는지 기록.

## Potential Merge Conflicts

`BACKEND_DB.md`, `PROJECT_OVERVIEW.md`(다른 문서 갱신과 줄 단위 충돌 — 추가 위주로).
