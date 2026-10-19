# HA'WE AWS 배포 작업 정리 (다시 따라 하기용)

> 최종 정리: 2026-10-16
> 출품·실습용 배포. 서버 1대(EC2) + DB(RDS MySQL) + 파일(S3) + AI(운영 측 Bedrock 게이트웨이).
> 비밀번호·API 키는 이 문서에 적지 않는다. 모두 각자의 `.env`에만 둔다.

---

## 0. 전체 구성

```
앱 ──HTTP:8080──▶ EC2 (Amazon Linux 2023, Java 25, Spring Boot jar)
                    ├─ RDS MySQL (DB: hawe)            같은 보안 그룹, 3306
                    ├─ S3 버킷 sgu-sapphire (사진 파일, EC2 IAM Role SafeRole-sgu-sapphire)
                    └─ LLM 게이트웨이 (LiteLLM → Bedrock)  미션 문장 AI
```

| 항목 | 값 |
|---|---|
| 리전 | ap-northeast-2 (서울) |
| IAM 사용자 | sgu-sapphire-1 |
| 보안 그룹 | sgu-sapphire-1 (EC2·RDS 공용) |
| RDS 엔드포인트 | sgu-sapphire-db.cdqgkoym8aye.ap-northeast-2.rds.amazonaws.com |
| RDS 사용자 / DB | admin / hawe |
| LLM 게이트웨이 | https://52.78.77.90 (모델: bedrock-haiku) |
| EC2 접속 | PuTTY, 사용자 `ec2-user`, `.ppk` 키 |

**계정 제약 (운영 측 정책)**
- RDS는 MySQL 또는 PostgreSQL, 프리 티어만 생성 가능 (MariaDB 불가)
- 새 보안 그룹에 인바운드 규칙 추가 불가 (`AuthorizeSecurityGroupIngress` 명시적 거부) → 기존 `sgu-sapphire-1` 그룹을 사용
- 비용 관리(Budgets)는 운영 측에서 함

---

## 1. AWS 콘솔 설정 (최초 1회, 완료됨)

1. **EC2** 생성: Amazon Linux 2023, 보안 그룹 `sgu-sapphire-1`
2. **RDS** 생성: MySQL, 프리 티어, 보안 그룹 `sgu-sapphire-1`
3. **보안 그룹 `sgu-sapphire-1` 인바운드 규칙**

   | 유형 | 소스 | 용도 |
   |---|---|---|
   | SSH (22) | 내 IP | PuTTY 접속 |
   | 사용자 지정 TCP (8080) | 0.0.0.0/0 | 앱 → 서버 |
   | MYSQL/Aurora (3306) | **사용자 지정 → sgu-sapphire-1 (자기 자신)** | EC2 → RDS |

   - 아웃바운드는 기본값(모든 트래픽) 그대로 둔다.
   - 3306 소스를 Anywhere(0.0.0.0/0)로 하지 않는다.
4. **S3** 버킷 생성 (퍼블릭 액세스 모두 차단)

---

## 2. 내 PC 준비

### 2-1. `.env` (프로젝트 루트, git 제외 파일)

`.env.example`을 복사해 `.env`로 만들고 채운다. **따옴표 없이** 입력한다(따옴표도 값에 들어감).

```properties
#DB_URL=                      ← 로컬은 주석 그대로 (빈 값 DB_URL= 금지)
DB_USERNAME=hawe_db
DB_PASSWORD=<로컬 MariaDB 비밀번호>
JWT_SECRET=<openssl rand -base64 32 결과>
HAWE_MISSION_AI_ENABLED=true
LLM_BASE_URL=https://52.78.77.90
LLM_API_KEY=<받은 키, sk- 로 시작 25자>
LLM_MODEL=bedrock-haiku
```

> `.env.example`은 git에 올라간다. **실제 키를 절대 적지 않는다.**

### 2-2. 로컬 DB (MariaDB, HeidiSQL에서 root로 접속)

```sql
CREATE DATABASE IF NOT EXISTS hawe CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'hawe_db'@'localhost' IDENTIFIED BY '<DB_PASSWORD와 같은 값>';
ALTER USER 'hawe_db'@'localhost' IDENTIFIED BY '<DB_PASSWORD와 같은 값>';
GRANT ALL PRIVILEGES ON hawe.* TO 'hawe_db'@'localhost';
FLUSH PRIVILEGES;
```

### 2-3. 빌드 (JDK 25 필요, PC 기본 Java는 17이라 지정해야 함)

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"
.\mvnw.cmd clean package -DskipTests          # 결과: target\hawe-0.0.1-SNAPSHOT.jar
.\mvnw.cmd test                               # 전체 테스트 (로컬 DB 필요)
```

---

## 3. EC2 배포

### 3-1. 켜기 (항상 이 순서)

1. AWS 콘솔에서 **RDS 시작** → "사용 가능"까지 대기 (5~10분)
2. **EC2 시작** → 새 퍼블릭 IP 확인 (중지 후 켜면 IP가 바뀜)
3. PuTTY로 새 IP에 접속

### 3-2. 최초 1회: 프로그램 설치

```bash
sudo dnf install -y mariadb105                          # DB 접속 프로그램 (MySQL에도 접속됨)
sudo dnf install -y java-25-amazon-corretto-headless    # Java 25
```

### 3-3. jar 업로드 (내 PC PowerShell)

```powershell
pscp -i "C:\경로\키.ppk" C:\Sapphire\target\hawe-0.0.1-SNAPSHOT.jar ec2-user@<EC2 IP>:/home/ec2-user/
```
EC2에서 `ls -lh ~/hawe-0.0.1-SNAPSHOT.jar`로 크기·시간 확인.

### 3-4. EC2 `.env` (`~/.env`, 권한 600)

```properties
DB_URL=jdbc:mysql://sgu-sapphire-db.cdqgkoym8aye.ap-northeast-2.rds.amazonaws.com:3306/hawe?connectionTimeZone=Asia/Seoul&forceConnectionTimeZoneToSession=true
DB_USERNAME=admin
DB_PASSWORD=<RDS 암호>
JWT_SECRET=<openssl rand -base64 32 결과>
HAWE_MISSION_AI_ENABLED=true
LLM_BASE_URL=https://52.78.77.90
LLM_API_KEY=<받은 키>
LLM_MODEL=bedrock-haiku
STORAGE_TYPE=s3
S3_BUCKET=sgu-sapphire
```
S3 키는 넣지 않는다(EC2에 운영 측 IAM Role `SafeRole-sgu-sapphire`가 있음). 권한 확인: `aws sts get-caller-identity`, `aws s3 ls s3://sgu-sapphire`

수정·확인 명령:
```bash
nano ~/.env                    # 직접 편집: 저장 Ctrl+O → Enter, 나가기 Ctrl+X
chmod 600 ~/.env
grep -E '^(DB_URL|LLM_BASE_URL|LLM_MODEL|HAWE_MISSION_AI_ENABLED)=' ~/.env
grep '^LLM_API_KEY=' ~/.env | cut -d= -f2- | awk '{print "키 길이:", length($0)}'   # 25가 나와야 함
```

### 3-5. (V1 마이그레이션이 바뀐 경우만) RDS DB 초기화

팀원이 이미 적용된 `V1__initial_schema.sql`을 고치면 서버가 `checksum mismatch`로 시작하지 않는다. **데이터가 모두 지워진다.**
```bash
mysql -h sgu-sapphire-db.cdqgkoym8aye.ap-northeast-2.rds.amazonaws.com -u admin -p -e "DROP DATABASE hawe; CREATE DATABASE hawe CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```
> 팀 규칙 제안: 배포 후에는 V1을 고치지 말고 V3, V4 … 새 파일을 추가한다.

### 3-6. 서버 실행 / 재시작

```bash
pkill -f hawe-0.0.1-SNAPSHOT.jar; sleep 3
cd ~ && nohup java -jar hawe-0.0.1-SNAPSHOT.jar > app.log 2>&1 &
tail -f app.log                # "Started HaweApplication" 확인 후 Ctrl+C (서버는 계속 실행)
```
`.env`를 고친 뒤에는 반드시 재시작해야 반영된다.

### 3-7. 확인

```bash
curl -s localhost:8080/actuator/health; echo      # {"status":"UP"}
```
브라우저: `http://<EC2 IP>:8080/actuator/health`, `http://<EC2 IP>:8080/swagger-ui/index.html`

**AI 미션 생성 확인** (발행 시각 00:00 그룹을 만들어 바로 발행시킴)
```bash
E="test$(date +%s)@example.com"
curl -s -X POST localhost:8080/api/auth/register -H 'content-type: application/json' -d "{\"email\":\"$E\",\"password\":\"test1234a\",\"nickname\":\"tester\",\"agreeTerms\":true,\"agreePrivacy\":true}"; echo
T=$(curl -s -X POST localhost:8080/api/auth/login -H 'content-type: application/json' -d "{\"email\":\"$E\",\"password\":\"test1234a\"}" | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
G=$(curl -s -X POST localhost:8080/api/groups -H "Authorization: Bearer $T" -H 'content-type: application/json' -d '{"name":"aitest","relationType":"FRIEND","dailyMissionCount":1,"missionSlots":[{"slotNo":1,"publishTime":"00:00","deadlineTime":"23:00"}]}' | sed -E 's/.*"id":([0-9]+).*/\1/;q')
sleep 10
mysql -h sgu-sapphire-db.cdqgkoym8aye.ap-northeast-2.rds.amazonaws.com -u admin -p hawe -e "SELECT id, generation_source, content FROM missions ORDER BY id DESC LIMIT 1;"
```
`generation_source`가 **AI**면 성공, **AI_FALLBACK**이면 AI 호출 실패(§5 참고).

### 3-8. 끄기 (비용 절약)

EC2 중지 → RDS 중지. RDS는 7일 후 자동으로 다시 켜지므로 필요하면 다시 중지한다.
EC2를 다시 켜면 서버는 꺼진 상태이므로 §3-6으로 다시 실행한다.

---

## 4. 코드 변경 이력 (이번 작업에서 바꾼 것)

| 작업 | 파일 | 상태 |
|---|---|---|
| MySQL 드라이버 추가, DB 주소를 `DB_URL`로 분리 (드라이버는 URL로 자동 선택) | pom.xml, application.properties, .env.example | 커밋됨 (상세: `RDS_MYSQL_CONFIG.md`) |
| 미션 AI를 게이트웨이로 연결 (`LLM_BASE_URL`·`LLM_API_KEY`·`LLM_MODEL`, 없으면 `ANTHROPIC_API_KEY`) | application.properties, MissionAiPropertiesTests | 커밋됨 |
| `.env.example`에 들어간 실제 API 키 삭제 | .env.example | 커밋됨 |
| S3 파일 저장소 (`STORAGE_TYPE=s3`일 때 S3, 기본은 로컬 `./uploads`) | S3FileStorage, StorageProperties, LocalFileStorage, WebMvcConfig, application.properties, pom.xml(AWS SDK 2.55.16), 테스트 | EC2 연결·업로드 확인 완료 (2026-10-16) |

**AI 키 동작**: 서버 시작 시 `LLM_API_KEY`가 있으면 게이트웨이, 없으면 `ANTHROPIC_API_KEY`를 사용. AI 호출이 실패하면 다른 곳으로 넘어가지 않고 미리 적어 둔 예비 문장을 쓴다(`generation_source=AI_FALLBACK`).

**S3 동작**: 버킷은 비공개. DB에는 파일 경로만 저장하고, 응답할 때 1시간 유효한 presigned URL을 만든다. 자격 증명은 `S3_ACCESS_KEY`/`S3_SECRET_KEY`가 있으면 그것, 없으면 EC2 IAM Role.

---

## 5. 문제 해결

| 증상 | 원인 | 해결 |
|---|---|---|
| `release version 25 not supported` (빌드) | PC 기본 Java가 17 | §2-3처럼 `JAVA_HOME`을 JDK 25로 지정 |
| `Access denied for user 'hawe_db'` | 로컬 DB에 계정이 없거나 비밀번호 불일치 | §2-2 실행 |
| `GSS-API authentication exception` (테스트) | 로컬 `.env`의 `DB_PASSWORD`가 비어 있음 | 비밀번호 입력 |
| `Migration checksum mismatch` | 팀원이 적용된 V1을 수정 | 해당 DB 초기화 (RDS: §3-5, 로컬: HeidiSQL에서 DROP/CREATE 후 GRANT) |
| `AuthorizeSecurityGroupIngress ... explicit deny` | 새 보안 그룹 규칙 추가 금지 정책 | 기존 `sgu-sapphire-1`에 규칙 추가, 안 되면 관리자 요청 |
| RDS 접속 시 아무 반응 없이 멈춤 | 3306 규칙 없음 / 다른 보안 그룹 | §1-3 규칙 확인 |
| `Plugin caching_sha2_password could not be loaded` | MariaDB 클라이언트 호환 문제 | `sudo dnf install -y https://dev.mysql.com/get/mysql84-community-release-el9-1.noarch.rpm && sudo dnf install -y mysql-community-client` |
| `generation_source = AI_FALLBACK`, 로그에 `HTTP 401` | EC2 `.env`의 `LLM_API_KEY`가 틀림 (키 길이 확인) | `sed -i '/^LLM_API_KEY=/d' ~/.env` 후 `echo 'LLM_API_KEY=<키>' >> ~/.env`, 재시작 |
| 게이트웨이 주소 오류 | `https://https://...` 중복 | `LLM_BASE_URL=https://52.78.77.90` |
| 미션이 안 나옴 (`missions: []`) | 기본 발행 시각 09:00 전 | 정상. 확인용은 발행 시각 00:00 그룹으로 (§3-7) |
| PuTTY `Software caused connection abort` | 유휴 연결 끊김 | 재접속 |
| 게이트웨이 상태 직접 확인 | — | `K=$(grep '^LLM_API_KEY=' ~/.env \| cut -d= -f2-); curl -s -o /dev/null -w "HTTP %{http_code}\n" -H "Authorization: Bearer $K" https://52.78.77.90/v1/models` (200 정상, 401 키 오류) |

---

## 6. 남은 일

| 순서 | 할 일 | 메모 |
|---|---|---|
| 1 | **LLM API 키 재발급 요청** | 한때 `.env.example`로 GitHub(비공개 레포)에 올라감. 새 키 받으면 PC·EC2 `.env`만 교체 후 재시작 |
| 2 | ~~S3를 EC2에 연결~~ | 완료. EC2에 운영 측 IAM Role이 있어 액세스 키 불필요 |
| 3 | 로컬 DB 초기화 | 팀원 V1 수정으로 로컬 테스트도 `checksum mismatch` → HeidiSQL에서 DROP/CREATE + GRANT |
| 4 | 미션 프롬프트 파일 EC2에 올리기 | 지금은 기본 프롬프트(`mission-ai/default-prompt.txt`)로 동작. 팀원 프롬프트 2개를 EC2에 올리고 `hawe.mission.ai.prompt-paths`를 `file:` 경로로 지정 |
| 5 | systemd 등록 | EC2 재부팅 시 서버 자동 실행 |
| 6 | 앱(iOS·Cordova)에 서버 주소 연결 | `http://<EC2 IP>:8080`. HTTP라 iOS ATS 예외 / Android cleartext 허용 필요 |
| 7 | 탄력적 IP | IP 고정 (권한 확인 필요) |
| 8 | SNS 푸시 | Firebase 프로젝트 먼저 필요 |

> `docs/INFRA/AWS_SERVICES.md`는 초기 초안(MariaDB·Bedrock IAM 기준)이라 실제 구성과 다르다. 이 문서를 기준으로 본다.
