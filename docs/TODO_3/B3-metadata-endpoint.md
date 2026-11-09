# B3. `tokenURI` 메타데이터·이미지 제공 (서버)

> 범위: 백엔드. **선행: A1**(카탈로그). B1 의 `tokenURI = baseURI + badgeCode + ".json"` 규칙을 서버가 제공한다. 근거: `BLOCKCHAIN_BADGES.md` §5·결정 6. 빌드/테스트: `./mvnw test`.

## Goal

탐색기·지갑이 배지 이름과 이미지를 보여줄 수 있도록 **배지 종류별 정적 메타데이터 JSON 과 이미지**를 공개 경로로 제공한다. 개인정보는 없다.

## Findings

- 메타데이터는 **배지 종류별**이다(사용자별 아님) → 카탈로그(A1 `BadgeCatalog`)에서 생성. 토큰 ID/주소/사용자를 포함하지 않는다.
- 이 경로는 `/api` 밖의 **공개 경로**여야 한다(탐색기/지갑은 인증 토큰을 못 보낸다). `SecurityConfig` 에서 해당 경로만 `permitAll` 처리가 필요한지 확인(구현 시 `global/config` 의 보안 설정 파일 위치 확인).
- 이미지: 앱의 캐릭터/도장과 같은 SVG 계열 에셋을 서버 정적 경로에서 서빙(IPFS 는 1차 제외, 결정 6). 디자인(Figma) 확정 전에는 임시 이미지 + `임시` 표기. 정적 리소스 위치는 `src/main/resources/static/` 또는 기존 `/files` 서빙 방식(`FileStorage`) 중 기존 관례를 확인해 선택.
- 접근 가능한 공개 URL 이 필요하다: 로컬 개발 환경(`localhost`)의 URL 은 탐색기가 읽을 수 없다 → 테스트넷에서 이미지를 보려면 배포된 서버(EC2) 도메인이 필요. `BASE_URI`(B2)는 설정 `hawe.chain.metadata-base-url` 로 분리.

## Key Implementation Points

1. `GET /badges/meta/{code}.json` → ERC-721 메타데이터 표준: `{ "name", "description", "image", "external_url"(선택), "attributes": [{"trait_type":"tier","value":"RARE"}, ...] }`. `{code}` 가 카탈로그에 없으면 404. 응답 헤더 `Cache-Control: public, max-age=3600`, `Content-Type: application/json`.
2. `GET /badges/img/{imageKey}.svg|png` → 정적 이미지(카탈로그의 `imageKey` 와 매핑, 없으면 404). 임시 이미지는 간단한 SVG(등급별 색) 로 생성하거나 정적 파일 추가.
3. `image` URL 은 `hawe.chain.metadata-base-url` 로 절대 URL 생성(예: `https://api.example.com/badges/img/first-submission.svg`).
4. 한국어/영어 이름: 메타데이터 `name`/`description` 은 한국어(앱과 동일) — 국제 탐색기에서 영어도 필요하면 후속(1차 한국어만).
5. 보안: 응답에 사용자·그룹·토큰 정보 없음, 디렉터리 순회 방지(코드/imageKey 화이트리스트 매칭만).

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `.../badge/controller/BadgeMetadataController.java` (신규) | 메타데이터·이미지 엔드포인트 |
| `.../badge/service/BadgeMetadataService.java` (신규) | 카탈로그 → 메타데이터 JSON |
| `src/main/resources/static/badges/img/*` (또는 기존 정적 서빙 위치) | 임시 이미지 |
| 보안 설정(`global/config/…Security…`) | `/badges/**` 공개 허용(필요 시) |
| `.../global/config/ChainProperties.java` | `metadataBaseUrl` 추가 |
| `application.properties` | 기본값(로컬) |
| 테스트 | 아래 |

## Constraints

- 사용자별/토큰별 정보를 메타데이터에 넣지 않는다(개인정보 금지).
- 허용 경로 외의 파일을 읽지 못하게 화이트리스트만 사용.
- 과장 문구 금지(설명에 "블록체인 영구 보증" 같은 표현 금지).
- 커밋/푸시 금지.

## Edge Cases

- 카탈로그에 없는 코드/이미지 키 → 404(예외 스택 노출 금지).
- 코드 대소문자/확장자 변형(`.JSON`) → 404 로 단순화.
- 카탈로그 변경 시 이전 토큰의 `tokenURI` 가 깨지지 않게 코드는 **삭제하지 않고 폐기 표시**만(카탈로그 규칙: 코드 재사용 금지) — 문서화.
- 캐시: 이미지/메타데이터 변경 시 CDN/브라우저 캐시 지연 → 짧은 max-age.

## Tests Required

- 정상 코드 메타데이터 필드 검증(name/description/image/attributes), 미등록 코드 404, 이미지 경로 순회 시도 거부, 인증 없이 접근 가능(공개 경로 설정 검증), 응답에 사용자 정보 없음.

## Validation Steps

`./mvnw test` 통과. 수동: 로컬 서버에서 두 URL 을 브라우저로 확인. 테스트넷 연동(D2)에서 탐색기가 이미지를 표시하는지는 D2 검증 항목.

## Potential Merge Conflicts

보안 설정 파일, 정적 리소스 디렉터리, `ChainProperties`(A1/C1 과 같은 파일 — 순차).
