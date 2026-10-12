# 계약 반영 1차 (로그인 / 그룹 / 미션 수행) 구현 계획

> **For agentic workers:** 이 문서는 **마스터 계획**이다. 단계마다 착수 직전에 `superpowers:writing-plans`로 세부(bite-sized) 계획을 따로 쓴 뒤 실행한다. 한 번에 6단계를 쓰면 코드와 어긋난다 (`CONTRACT_WORK_PLAN.md` "진행 방식").

**Goal:** 가입 → 그룹 만들기·참여 → 오늘 미션 확인 → 제출 → 결과(피드) 확인이 실제 서버로 동작한다.

**Architecture:** 단계(1 공통 → 2 Auth → 3 Group → 4 미션 → 6 제출·피드)마다 `설계 문서 → 백엔드(테스트·Postman) → Android·iOS` 한 묶음. 백엔드가 앱보다 한 단계 앞서간다. 기능 패키지는 기존 `controller/service/dto/domain/dao` 5개 구조를 지킨다.

**Tech Stack:** Spring Boot 4.1.1 / Java 25 / MyBatis / MariaDB / Flyway(V1부터 재작성) · Android(Kotlin, Compose) · iOS(SwiftUI)

**Spec:** `docs/BACKEND/CONTRACT_DECISIONS.md` §0 §1 §2 §3 §6, 범위 정의 `docs/BACKEND/CONTRACT_WORK_PLAN.md` "1차 범위"

## Global Constraints

- 시간대 Asia/Seoul 고정, 응답 시각은 오프셋 포함 ISO-8601 (`2026-09-28T09:00:00+09:00`), 날짜 `yyyy-MM-dd`, 슬롯 시각 `HH:mm`
- 로그인 식별자는 이메일(trim + 소문자 저장). 비밀번호 8~100자 + 영문·숫자, 닉네임 trim 후 2~10자, 그룹 이름 trim 후 1~16자
- 그룹 정원: COUPLE 2명, 그 외 6명. 초대 코드 6자리, 문자 집합 `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`
- 하루 미션 1개 (`hawe.mission.max-daily-count=1`), MVP 틀 8종: `PHOTO_NOW PHOTO_GALLERY PHOTO_COLOR VOICE_LINE DRAW_SKETCH DRAW_A_TO_B TEXT_ACROSTIC TEXT_FILL_BLANK`
- 음성 ≤ 30초·파일 ≤ 5MB, 이미지 ≤ 10MB
- 서버에 없는 기능(콕·스티커·댓글·달력 도장·활동 요약)은 앱에서 **숨기거나 "준비 중"**. 목업 데이터와 서버 데이터를 섞지 않는다
- 3단계와 4단계는 동시에 진행하지 않는다 (`GroupService`·`GroupMapper` 충돌)
- 커밋은 사용자가 한다 (CLAUDE.md 워크플로). 테스트 보강은 Codex 단계

## 현재 코드 상태 (조사 결과)

| 영역 | 있음 | 없음 / 바뀔 것 |
|---|---|---|
| Auth | `register/login/refresh`, `loginId` 기준, `email` nullable | email 기준 전환, 약관 저장, 검증, `GET /me` |
| Group | 목록·생성·상세·설정·멤버·JWT 초대·나가기(그룹장 불가) | 6자리 코드, 미리보기, 정원, `startDate`, 후임자/삭제 나가기, 이름 검증 |
| Mission | 조회(`CURDATE()` 기준)·틀 목록. **생성 코드 전무** | 스케줄러, 틀 선택, 첫 미션 즉시 발행, 현재 미션 기준, `number/closesAt/members` |
| Submission | 테이블만 (V3: `media_url`, `answer_data` 등, `uk(mission,user)`) | 컨트롤러·서비스·매퍼·파일 저장소 전부 |
| 앱(Android/iOS) | UI + 메모리 `DataStore`(목업) | **네트워크 계층·토큰 저장소 없음**, 서버 모델 매핑 |
| 설정 | `max-daily-count=5`, TZ 미설정 | 1로 변경, TZ 고정 |

## Review Focus

- 자정을 넘기는 미션(23:30 발행): 자정 뒤에도 "현재 미션"이며 제출·피드가 정상 (`CURDATE()` 잔존 쿼리가 없어야 함)
- 같은 이메일 대소문자·공백 차이로 중복 가입 (`Sky@Email.com ` vs `sky@email.com`)
- 정원 마지막 자리 동시 참여 2건 → 한 명만 성공, 다른 쪽 409 `GROUP_FULL`
- 스케줄러가 두 번 돌거나 서버 재시작 직후 같은 미션이 중복 생성되지 않음 (`uk_missions_group_date_slot` + 멱등 쿼리)
- 미제출자의 피드·상세 접근 423, 닫힌 미션 제출 409 `MISSION_CLOSED`, 같은 미션 중복 제출 409
- 그룹장이 후임자 없이 나가기(다른 멤버 있음) → 409 `SUCCESSOR_REQUIRED`, 데이터 변경 없음
- 앱: access 만료 중 동시 요청 여러 개 → refresh는 한 번만 (single-flight), 실패 시 로그아웃

## 단계별 작업

### Task 0 — 선행 결정과 준비 (코드 전)

**결정 완료 (2026-10-12, 사용자):**
- **DB 초기화 + 마이그레이션 V1부터 재작성.** 개발 DB에 중요한 데이터 없음. 단계마다 V6, V7…을 추가하지 않고 `db/migration`의 기존 V1~V5를 정리해 다시 쓴다. 각 단계는 해당 테이블 정의를 그 파일에서 직접 고친다(기존 DB는 매번 `DROP DATABASE hawe; CREATE DATABASE hawe` 후 재적용). 이로써 `login_id` 제거, email NOT NULL 등 ALTER·backfill 불필요
- **앱은 Android 먼저**, iOS는 그 뒤
- **파일 저장은 로컬 + 공개 정적 서빙(`/files/**`, UUID 파일명).** 서명 URL·S3는 나중

- [ ] 마이그레이션 구성(권장): `V1__schema.sql`(최종 스키마 전체: users[email·약관], groups[invite_code·start_date·relation_type·daily_mission_count], group_members, group_mission_slots, missions[template·params·slot_no·published_at·deadline_at], submissions[media_url·answer_data…], 나머지 2차 테이블은 기존 정의 유지), `V2__seed_characters.sql`(현 V4+V5의 최종 7종). V2~V3 ALTER 이력은 폐기
- [ ] **Flyway 체크섬 주의:** 이미 적용된 V1을 고치면 기존 DB에서 검증 실패 → 반드시 DB를 비우고 시작. 팀원(Codex/다른 개발자) 로컬 DB도 같이 초기화하도록 공지
- [ ] 마이그레이션을 건드리는 테스트(`*MapperTests`)는 JdbcTemplate로 `login_id`·`CURDATE()` 등을 쓰므로 스키마 변경 단계마다 같이 수정
- [ ] `CONTRACT_AUDIT/DECISIONS/WORK_PLAN` 문서 커밋(사용자)

### Task 1 — 공통 (§0)

**Files:** Modify `application.properties`, `HaweApplication.java`, `global/exception/CommonErrorCode.java`, `GlobalExceptionHandler.java`, 시각 필드가 있는 모든 DTO(`LocalDateTime`→`OffsetDateTime`), `docs/BACKEND/API_DESIGN.md` §0
**Produces:** `CommonErrorCode.NOT_FOUND(404)`, `METHOD_NOT_ALLOWED(405)`, `FILE_TOO_LARGE(413)`; 전역 TZ Asia/Seoul

- [ ] `HaweApplication.main` 시작 전 `TimeZone.setDefault(Asia/Seoul)`, `spring.jackson.time-zone`, JDBC URL `?connectionTimeZone=Asia/Seoul` (또는 `sessionVariables=time_zone='+09:00'`)
- [ ] 핸들러: `NoResourceFoundException`→404, `MethodArgumentTypeMismatchException`/`MissingServletRequestParameterException`→400 `VALIDATION_ERROR`, `HttpRequestMethodNotSupportedException`→405, `MaxUploadSizeExceededException`→413
- [ ] DTO 시각 `OffsetDateTime` (`atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime()` 한 곳의 헬퍼로)
- [ ] 테스트: `GlobalExceptionHandlerTests`(MockMvc로 404/400/405 JSON 형식 검증). 실행 `./mvnw test`
- [ ] `application.properties`: `hawe.mission.max-daily-count=1`

### Task 2 — Auth + `GET /me` (§1)

**Files:** Modify `auth/dto/{RegisterRequest,LoginRequest,RegisterResponse}`, `AuthErrorCode`(`LOGIN_ID_TAKEN`→`EMAIL_TAKEN`), `AuthService`, `UserMapper(+XML)`, `User`; Modify `V1__schema.sql`(users: `login_id` 삭제, `email NOT NULL UNIQUE`, 약관 시각 컬럼 — V1 재작성 이후 해당 부분), `auth/dto/MeResponse`, `GET /api/me` (auth 패키지 `MeController` — 새 패키지는 만들지 않는다), Postman `HAWE_AUTH`, `API_DESIGN.md` §1, `BACKEND_DB.md` users
**Produces:** `MeResponse{id,email,nickname,characterKey,profileImageUrl(null),createdAt}` — 이후 모든 단계에서 앱이 "나"를 식별하는 데 사용

- [ ] 스키마(V1 재작성): `login_id` 없음, `email NOT NULL UNIQUE`, `terms_agreed_at`, `privacy_agreed_at` DATETIME NOT NULL
- [ ] DTO 검증: `@Email`, 비밀번호 `@Pattern`(영문·숫자 포함)+`@Size(8,100)`, 닉네임 trim 후 2~10자, `@AssertTrue agreeTerms/agreePrivacy`. 이메일은 서비스에서 `trim().toLowerCase()`
- [ ] 실패 테스트 먼저(`AuthServiceTests`): 대소문자 다른 중복→`EMAIL_TAKEN`, 약관 false→400, 로그인 이메일 정규화
- [ ] `GET /api/me`: `characterKey`는 `characters.svg_key` (null이면 null)
- [ ] `PATCH /me/password`는 **2차** — 만들지 않는다
- [ ] 완료 기준: `./mvnw test` 통과, Postman 갱신, `CONTRACT_DECISIONS` §1 "반영됨"(1차분 표기)

### Task 2A — 앱 네트워크 계층 + 인증 연결 (Android → iOS)

**Android Files:** Create `core/net/{ApiClient,TokenStore,AuthInterceptor/Authenticator,ApiError}.kt`; Modify `AppSession.kt`, `feature/auth/*`(A1 로그인, A3 가입 완료), `AuthNavigation.kt`; 제거 `accountForLogin`
- [ ] `TokenStore`: Android Keystore 기반(EncryptedSharedPreferences 대체 가능한 AndroidX Security 또는 Keystore+DataStore). 앱 시작 시 토큰이 있으면 `GET /me`로 세션 복구
- [ ] 401 처리: `TOKEN_EXPIRED`→refresh 1회(single-flight, Mutex)→재시도, `INVALID_TOKEN`/refresh 실패→토큰 삭제+A1
- [ ] 에러 본문 `{code,message}` 파싱 → 화면 토스트/필드 에러로 매핑 (네트워크 불가 별도 문구)
- [ ] 가입 흐름: `register`→`login`→(캐릭터면) `PATCH /me/character`. 사진 프로필 선택지는 1차에서 **숨김**(프로필 사진은 2차)
- [ ] 간편 로그인·비밀번호 찾기 → "준비 중" 토스트, 17-2 비밀번호 변경 → "준비 중"
- [ ] baseUrl은 `BuildConfig` 필드(debug=에뮬 `10.0.2.2:8080`)
- [ ] 단위 테스트: refresh single-flight, 401 분기(MockWebServer)
- iOS: 동일 구조 (`URLSession` 래퍼 + Keychain), `LoginView.logIn`, `SignupProfileView` 등

### Task 3 — Group (§2, 1차분)

**Files:** Modify `V1__schema.sql`(groups), `group/**`(DTO·Service·Mapper·XML), `JwtTokenProvider`·`JwtProperties`·`application.properties`(초대 토큰 제거), Postman `HAWE_GROUP`, `API_DESIGN.md` §2·§0-1, `BACKEND_DB.md`
**Produces:** `GroupSummaryResponse{…, members[], startDate}`(※`todayMission`은 Task 4에서 추가), `InviteCodeGenerator`, `GroupErrorCode.{INVITE_NOT_FOUND,GROUP_FULL,SUCCESSOR_REQUIRED,INVALID_SUCCESSOR}`

- [ ] 스키마(V1 재작성): `groups.invite_code CHAR(6) NOT NULL UNIQUE`, `groups.start_date DATE NULL`. (그룹 내 프로필 컬럼은 2차이므로 **넣지 않는다**)
- [ ] 서비스 테스트 먼저: 정원(COUPLE 2/그 외 6) 초과 `GROUP_FULL`, 이미 멤버 `ALREADY_MEMBER`, 없는 코드 `INVITE_NOT_FOUND`, 코드 대소문자 무시, `startDate`는 COUPLE만·미래 날짜 400, 이름 trim/공백 거부
- [ ] 생성: 코드 생성 시 충돌 재시도(최대 N회), `startDate` 저장
- [ ] `GET /api/invites/{code}` 미리보기, `POST /groups/join {code}`; 정원 보장은 `SELECT … FOR UPDATE`(그룹 행 잠금) 후 count 검사 → 동시 가입 경쟁 방지
- [ ] 목록 정렬 `joined_at` 오름차순, 응답에 `members`(userId·nickname·characterKey·profileImageUrl=null), `memberCount/maxMembers`
- [ ] 나가기: 일반 멤버 204 / 그룹장+타 멤버 `successorUserId` 필수(없으면 `SUCCESSOR_REQUIRED`) — 앱은 이 경우 **"준비 중" 안내** / 혼자면 그룹 삭제(삭제 방식 결정: 1차는 **실삭제**, 슬롯·미션·제출 cascade 정리를 서비스에서 명시)
- [ ] 제거: `POST /groups/{id}/invite`, 초대 JWT 코드, `OWNER_CANNOT_LEAVE`, `INVALID_INVITE_TOKEN`, `INVITE_EXPIRED`
- [ ] 초대 코드 재발급·설정 변경은 **2차**지만 기존 `PATCH settings`의 빈 이름 버그만 이번에 막는다
- [ ] 상세 응답: `inviteCode`, `inviteUrl`(`hawe.invite.base-url` 설정값), `startDate`, `myNotify`는 제외

**Android/iOS:** `Relationship`↔서버 코드 매핑, `DataStore.createGroup/lookUpInvite/join/leave`→API, 홈 목록·16 화면·멤버 목록 서버 연결, 나가기 확인 문구 변경(그룹장 케이스 "준비 중"), 중복 탭 방지. 그룹 프로필 편집(16-1)·재발급 버튼은 숨김/"준비 중".

### Task 4 — 미션 생성 + 현재 미션 (§3)  ※ Task 3 완료 후

**Files:** Create `mission/service/{MissionGenerator,PlaceholderMissionGenerator,MissionScheduler,MissionPublisher}.java`, Modify `MissionMapper(+XML)`, `MissionResponse`(+`number`,`closesAt`,`members`), `TodayMissionsResponse`, `MissionService`, `GroupMapper.findSummariesByUserId`(+`todayMission`), `GroupService.createGroup`(첫 미션 즉시 발행), `MissionErrorCode.MISSION_CLOSED`, `MISSIONS_API.md`, 필요하면 `V1__schema.sql`(missions 인덱스)
**Interfaces:** `interface MissionGenerator { GeneratedMission generate(Group g, MissionTemplate t, List<Member> members); }` — AI 연동은 이 구현체만 교체. `PlaceholderMissionGenerator`는 틀마다 고정 문장 + `params`.

- [ ] 규칙 단위 테스트 먼저(순수 함수 `MissionTiming`): `deadlineAt`(발행 후 처음 오는 마감 시각), 첫 미션 즉시 발행 조건, `closesAt`=다음 발행, `isLate` 판정, 23:30 발행→자정 후에도 현재 미션
- [ ] `MissionPublisher.publishIfDue(group, slot, now)`: 멱등(해당 그룹·슬롯의 마지막 미션 발행 시각 < 오늘 슬롯 발행 시각일 때만). 중복은 `uk_missions_group_date_slot` DuplicateKey 무시
- [ ] 틀 선택: MVP 8종 풀에서, 하루 1개이므로 직전 미션과 다른 `media` 우선
- [ ] `MissionScheduler`: `@Scheduled(cron="0 * * * * *", zone="Asia/Seoul")`가 발행 시각이 지난 그룹을 찾아 publish. `@EnableScheduling`
- [ ] 생성 직후: `createGroup` 트랜잭션 끝에서 오늘 발행 시각이 지났으면 즉시 발행(실패해도 그룹 생성은 롤백하지 않음 — 스케줄러가 다음 분에 재시도)
- [ ] "현재 미션" 쿼리로 교체: `CURDATE()` 사용처 전수 grep (`MissionMapper.findTodayByGroupId`, `GroupMapper`, `GroupMemberMapper` stamped_today, `PokeMapper.existsTodaySubmission`) — 콕은 2차이나 컴파일/동작 유지를 위해 같은 기준으로 맞춘다
- [ ] `content` 의 `[대상]` 치환, `number`(그룹 내 순번), `members[{userId,submitted}]`
- [ ] 오류: `MISSION_CLOSED`(제출 단계에서 사용)

**Android/iOS:** `MissionType`↔`media`, 05·홈 서버 연결, 마감/지각 표시, 미션 없는 빈 상태(`nextPublishAt`), 앱 내 미션 생성 제거(`refreshTodayMissions/makeMission/TopicPool`), 글 틀별 제한(`TEXT_FILL_BLANK` ≤100자, `TEXT_ACROSTIC` 3줄), 그림 `DRAW_SKETCH` `params.seconds` 타이머(못 넣으면 풀에서 제외). 콕 버튼 숨김.

### Task 6 — 제출·피드 + 파일 저장소 (§6)

**Files:** Create `submission/{controller,service,dto,domain,dao}/*`, `global/storage/FileStorage.java`+`LocalFileStorage`, `WebMvcConfig`(`/files/**`), `SubmissionErrorCode`(`ALREADY_SUBMITTED, FEED_LOCKED(423), UNSUPPORTED_FILE_TYPE(415), MISSION_CLOSED` 재사용), Postman `HAWE_SUBMISSION`, `MISSIONS_API.md` §3·§4
**Produces:** `FeedItemResponse{id,author(프로필 객체),template,mediaUrl,mediaType,text/answers,duration,waveform,commentCount:0,reactions:[],submittedAt,isLate}`, `POST /api/missions/{id}/submissions`, `GET /api/missions/{id}/submissions`, `GET /api/submissions/{id}`

- [ ] `application.properties`: `spring.servlet.multipart.max-file-size=10MB`, `hawe.storage.local-dir`
- [ ] 서비스 테스트 먼저: 템플릿별 필수 필드(사진/그림=file, 글=text 규칙, 음성=file+duration≤30+waveform≤64개 0~1), 중복 `ALREADY_SUBMITTED`, 닫힌 미션 `MISSION_CLOSED`, 마감 뒤 `isLate=true`, 비멤버 403, 미제출자 피드 423
- [ ] 저장 시 확장자·MIME 검증(415), UUID 파일명, 트랜잭션 롤백 시 파일 정리
- [ ] 음성 `duration`/`waveform`은 `answer_data` JSON에 저장
- [ ] `reactions`/`commentCount`는 빈 값 고정 (2차에서 채움 — 응답 모양은 §6-2 그대로 유지해 앱 모델이 안 바뀌게)
- [ ] 완료 후 해당 미션의 `members[].submitted`/`submittedByMe`/그룹 목록 `submittedCount` 가 갱신되는지 통합 테스트

**Android/iOS:** `DataStore.submit/result`→multipart, `SubmissionContent.Voice` 길이·파형 전송, 결과 화면 서버 데이터·`isLate` 표시, 423/409 처리(05로 복귀·새로고침), 스티커·댓글·달력 도장·활동 요약 UI는 숨김.

## 실행 순서와 병렬성

```
Task0 ─ Task1 ─ Task2 ─ Task3 ─ Task4 ─ Task6          (백엔드, 직렬)
                  └ 2A(앱 네트워크·인증) ─ 3A ─ 4A ─ 6A   (앱, 백엔드보다 한 단계 뒤)
```

- 병렬 가능: 백엔드 Task N+1 ↔ 앱 Task N 연결
- 병렬 금지: Task 3 ↔ Task 4 (같은 `GroupService/GroupMapper`)
- Task 6은 Task 4의 `deadlineAt`/현재 미션 규칙에 의존, Task 2A의 네트워크 계층 재사용

## 단계 완료 기준 (공통)

1. 백엔드 `./mvnw test` 통과 + Postman 컬렉션 갱신
2. Android(→ iOS) 해당 화면이 실제 서버로 동작, 목업 `DataStore` 경로 제거
3. 설계 문서 해당 절 수정, `CONTRACT_DECISIONS.md` 절 상태를 "반영됨(1차)"로 표기, 2차 미룬 항목 명시

## 리스크

- **일정**: Task 4(생성기+스케줄러)와 Task 6(파일·multipart)이 신규 구현이라 가장 크다. 시간이 모자라면 Task 6의 음성 파형·글 틀별 검증을 마지막에 붙인다
- **앱 이중 작업**: Android·iOS 두 벌. 부족하면 iOS를 2차 초입으로 미루는 것이 현실적 (사용자 결정 필요)
- **데모 데이터**: `DemoSeed`는 서버 연결 후 제거해 목업과 섞이지 않게 한다
