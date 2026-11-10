# HA'WE AWS 배포 작업 정리 (다시 따라 하기용)

> 최종 정리: 2026-11-10 (컨테이너 배포로 전환, 이미지는 GHCR 로 주고받음. 이전 jar 직접 실행 방식은 §3-8 롤백에만 남김)
> 출품·실습용 배포. 서버 1대(EC2) + DB(RDS MySQL) + 파일(S3) + AI(운영 측 Bedrock 게이트웨이).
> 비밀번호·API 키는 이 문서에 적지 않는다. 모두 각자의 `.env`에만 둔다.

---

## 0. 전체 구성

```
앱 ──HTTP:8080──▶ EC2 (Amazon Linux 2023, Docker)
                    ├─ [컨테이너] api     Spring Boot (Java 25)   8080 공개
                    │     └─▶ [컨테이너] vision  FastAPI + ONNX (Python 3.12)  내부 전용 vision:8001
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
| EC2 배포 폴더 | `~/hawe` (`compose.prod.yaml`, `.env`, `models/`) |

| 컨테이너 파일 (저장소) | 용도 |
|---|---|
| `Dockerfile`, `.dockerignore` | api 이미지 (멀티 스테이지: JDK 25 로 빌드 → JRE 25 로 실행) |
| `ml/vision-service/Dockerfile`, `.dockerignore` | vision 이미지 (모델은 이미지에 넣지 않고 `/models` 볼륨) |
| `compose.yaml` | 로컬 개발: db(MariaDB) + vision + api |
| `compose.prod.yaml` | EC2: vision + api (DB 는 RDS) |

| 이미지 (GHCR, 비공개) | 태그 |
|---|---|
| `ghcr.io/maenghansoo1/hawe-api` | `latest` + 커밋 번호(예: `2fcad56`) |
| `ghcr.io/maenghansoo1/hawe-vision` | `latest` + 커밋 번호 |

GHCR 토큰(GitHub classic PAT, 90일 만료):

| 토큰 | 권한 | 쓰는 곳 |
|---|---|---|
| push 용 | `write:packages`, `read:packages`, `delete:packages` | 배포하는 사람의 PC 에서만 |
| pull 용 | `read:packages` 만 | EC2, 팀원 PC |

토큰은 git·`.env`·메신저에 올리지 않는다. 만료되면 새로 만들어 `docker login ghcr.io` 를 다시 한다.

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
   - vision(8001)은 열지 않는다. api 컨테이너만 내부에서 부른다.
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
> `.env` 는 `.dockerignore` 로 막혀 있어 이미지에 들어가지 않는다. 컨테이너에는 실행할 때 `env_file` 로 주입된다.

### 2-2. Docker Desktop

- Windows: Docker Desktop 설치. "WSL not installed" 가 뜨면 관리자 PowerShell 에서 `wsl --install` 후 재부팅.
- 확인: `docker --version`, `docker compose version`, `docker run --rm hello-world`
- vision 모델: `ml/vision-service/models/` 에 ONNX 파일 3개(`image_encoder.fp16.onnx`, `text_encoder.fp16.onnx`, `tokenizer.json`, 합계 429MB). git 에 없으므로 팀원에게 받거나 `export_onnx.py` 로 만든다.

### 2-3. 로컬 실행 (컨테이너)

```powershell
cd C:\Sapphire
docker compose up --build      # db + vision + api. 처음·코드 변경 후 --build
docker compose ps              # db·vision healthy, api Up
curl.exe http://localhost:8080/actuator/health   # {"status":"UP"}
docker compose down            # 정지 (DB·사진은 볼륨에 남음)
docker compose down -v         # 볼륨까지 삭제 = 로컬 DB 초기화
```

- 로컬 컨테이너 DB 는 PC 에 설치된 MariaDB 와 **별개**다. HeidiSQL: `localhost:3307`, 사용자 `hawe_db`, `.env` 의 `DB_PASSWORD`.
- vision 은 PC 에서 `localhost:8001` 로 접속되지 않는다(포트를 열지 않음). 정상이다.

### 2-4. (컨테이너 없이) 직접 빌드·테스트

JDK 25 필요. PC 기본 Java가 17이라 지정해야 한다. 테스트는 PC 의 로컬 MariaDB(아래 SQL)를 쓴다.

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"
.\mvnw.cmd clean package -DskipTests          # 결과: target\hawe-0.0.1-SNAPSHOT.jar
.\mvnw.cmd test                               # 전체 테스트 (로컬 DB 필요)
```

로컬 MariaDB 준비 (HeidiSQL에서 root로 접속):
```sql
CREATE DATABASE IF NOT EXISTS hawe CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'hawe_db'@'localhost' IDENTIFIED BY '<DB_PASSWORD와 같은 값>';
ALTER USER 'hawe_db'@'localhost' IDENTIFIED BY '<DB_PASSWORD와 같은 값>';
GRANT ALL PRIVILEGES ON hawe.* TO 'hawe_db'@'localhost';
FLUSH PRIVILEGES;
```

---

## 3. EC2 배포 (컨테이너)

### 3-1. 켜기 (항상 이 순서)

1. AWS 콘솔에서 **RDS 시작** → "사용 가능"까지 대기 (5~10분)
2. **EC2 시작** → 새 퍼블릭 IP 확인 (중지 후 켜면 IP가 바뀜)
3. 컨테이너는 **자동으로 다시 뜬다**(`docker.service` enabled + `restart: unless-stopped`). 확인만 한다:
   ```bash
   cd ~/hawe && docker compose -f compose.prod.yaml ps
   curl -s localhost:8080/actuator/health; echo
   ```
   RDS 가 아직 안 켜져 있으면 api 가 재시작을 반복하다 RDS 가 켜지면 정상으로 올라온다.

### 3-2. 최초 1회: EC2 준비 (2026-11-09 완료됨)

**Docker 설치**
```bash
sudo dnf install -y docker
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user        # PuTTY 를 닫고 다시 접속해야 적용된다
```

**Compose 플러그인.** AL2023 저장소에 없다. GitHub 릴리스 다운로드가 504 로 실패한 적이 있어서(오류 페이지가 파일로 저장돼 `exec format error`), Docker Hub 이미지에서 꺼내 설치했다.
```bash
docker pull docker/compose-bin:v2.39.2
cid=$(docker create docker/compose-bin:v2.39.2 x)
docker cp $cid:/docker-compose /tmp/docker-compose && docker rm $cid
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo install -m 755 /tmp/docker-compose /usr/local/lib/docker/cli-plugins/docker-compose
rm /tmp/docker-compose && docker rmi docker/compose-bin:v2.39.2
docker compose version                  # Docker Compose version v2.39.2
```

**배포 폴더**
```bash
mkdir -p ~/hawe/models
cp ~/.env ~/hawe/.env && chmod 600 ~/hawe/.env     # 내용은 §3-4
```

**GHCR 로그인 (pull 용 토큰)**
```bash
docker login ghcr.io -u Maenghansoo1     # Password: 에 read:packages 토큰 붙여넣기(PuTTY 는 오른쪽 클릭)
```
`-p 토큰` 으로 쓰지 않는다(쉘 기록에 남음). 토큰은 `~/.docker/config.json`(권한 600)에 암호화 없이 저장되므로 EC2 에는 읽기 전용 토큰만 둔다.
모델과 compose 파일 업로드(내 PC PowerShell):
```powershell
$KEY = "C:\경로\키.ppk"; $EC2 = "ec2-user@<EC2 IP>"
pscp -i $KEY C:\Sapphire\compose.prod.yaml "${EC2}:/home/ec2-user/hawe/"
pscp -i $KEY C:\Sapphire\ml\vision-service\models\* "${EC2}:/home/ec2-user/hawe/models/"
```

**예전 systemd 서비스 끄기.** jar 방식 때 등록한 `hawe.service`(jar)·`hawe-vision.service`(uvicorn)가 부팅 때 같이 켜지면 jar 가 8080 충돌로 15초마다 재시작하며 CPU·메모리를 잡아먹는다(2026-11-10 실제 발생).
```bash
sudo systemctl disable --now hawe.service hawe-vision.service
systemctl is-enabled hawe.service hawe-vision.service   # disabled, disabled
```
서비스 파일은 롤백용으로 남겨 둔다(§3-8).

### 3-3. 배포할 때마다 (GHCR)

**① 내 PC — 커밋 → 빌드 → push (PowerShell)**

push 용 토큰으로 한 번 로그인해 두면 다시 하지 않아도 된다(`docker login ghcr.io -u Maenghansoo1`).
```powershell
cd C:\Sapphire
git status                                   # 커밋 안 된 변경이 없어야 한다(태그 = 커밋 번호)
$TAG = git rev-parse --short HEAD; $env:TAG = $TAG
docker compose build api vision              # ghcr.io/maenghansoo1/hawe-*:<커밋번호>
docker compose push api vision               # 바뀐 층만 올라간다(보통 api 의 jar 층 수십 MB)
foreach ($n in "hawe-api","hawe-vision") {   # 같은 이미지에 latest 이름표도 붙인다
  docker tag "ghcr.io/maenghansoo1/${n}:$TAG" "ghcr.io/maenghansoo1/${n}:latest"
  docker push "ghcr.io/maenghansoo1/${n}:latest"
}
Remove-Item Env:TAG
```
- 확인: `docker manifest inspect ghcr.io/maenghansoo1/hawe-api:$TAG` 가 오류 없이 나오면 올라간 것.
- push 가 중간에 끊기면 층만 올라가고 태그가 안 생길 수 있다(`manifest unknown`). 같은 push 를 다시 하면 층은 `Layer already exists` 로 건너뛰고 몇 초 만에 끝난다.

**② EC2 — pull → 교체**
```bash
cd ~/hawe
docker compose -f compose.prod.yaml pull     # latest 를 받는다(없는 층만)
docker compose -f compose.prod.yaml up -d    # 바뀐 서비스의 컨테이너만 다시 만든다(api 는 20~30초 끊김)
docker compose -f compose.prod.yaml ps
docker image prune -f                        # 이름표 없는 예전 이미지 정리(디스크 8GB 중 80% 이상 사용 중)
```
- `compose.prod.yaml` 을 고쳤으면 먼저 올린다: `pscp -i $KEY C:\Sapphire\compose.prod.yaml "${EC2}:/home/ec2-user/hawe/"`
- 한쪽만 바꾸려면 서비스 이름을 붙인다: `pull api` / `up -d api`.
- GHCR 무료 비공개 용량이 작다. 오래된 커밋 번호 태그는 GitHub → Packages → 패키지 → Package settings 에서 지운다(롤백용으로 최근 2~3개는 남긴다).

### 3-4. EC2 `.env` (`~/hawe/.env`, 권한 600)

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
- S3 키는 넣지 않는다(EC2에 운영 측 IAM Role `SafeRole-sgu-sapphire`가 있고, 컨테이너 안에서도 받아진다. 확인: §3-6).
- `HAWE_VISION_ENABLED`·`HAWE_VISION_BASE_URL` 은 `compose.prod.yaml` 이 넣는다(`http://vision:8001`).
- **`.env` 를 고친 뒤에는 `docker compose -f compose.prod.yaml up -d --force-recreate api`.** `restart` 로는 `env_file` 이 다시 읽히지 않는다.

확인 명령:
```bash
chmod 600 ~/hawe/.env
grep -E '^(DB_URL|LLM_BASE_URL|LLM_MODEL|HAWE_MISSION_AI_ENABLED|STORAGE_TYPE)=' ~/hawe/.env
grep '^LLM_API_KEY=' ~/hawe/.env | cut -d= -f2- | awk '{print "키 길이:", length($0)}'   # 25가 나와야 함
```

### 3-5. (V1 마이그레이션이 바뀐 경우만) RDS DB 초기화

팀원이 이미 적용된 `V1__initial_schema.sql`을 고치면 서버가 `checksum mismatch`로 시작하지 않는다. **데이터가 모두 지워진다.**
```bash
mysql -h sgu-sapphire-db.cdqgkoym8aye.ap-northeast-2.rds.amazonaws.com -u admin -p -e "DROP DATABASE hawe; CREATE DATABASE hawe CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```
> 팀 규칙 제안: 배포 후에는 V1을 고치지 말고 V3, V4 … 새 파일을 추가한다.

### 3-6. 확인·운영 명령

```bash
cd ~/hawe
docker compose -f compose.prod.yaml ps                       # vision (healthy), api Up
curl -s localhost:8080/actuator/health; echo                 # {"status":"UP"}
docker compose -f compose.prod.yaml logs -f --tail 100 api   # 로그 (Ctrl+C 는 보기만 멈춤)
docker stats --no-stream                                     # 메모리: api /800MiB, vision /1GiB
docker inspect -f '{{.Name}} restarts={{.RestartCount}} oom={{.State.OOMKilled}}' hawe-api-1 hawe-vision-1
```
api → vision 연결:
```bash
docker compose -f compose.prod.yaml exec -T api timeout 5 bash -c '</dev/tcp/vision/8001' && echo OK
```
컨테이너 안에서 S3 권한(IMDS) 이 받아지는지 (200 이면 정상):
```bash
docker run --rm curlimages/curl -s -m 3 -o /dev/null -w '%{http_code}\n' -X PUT \
  http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60'
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

### 3-7. 메모리 (t3.small 2GB)

| 컨테이너 | 한도 | 측정 (2026-11-10) |
|---|---|---|
| api | 800MB (Java 힙 70% ≈ 560MB) | 약 250~270MB |
| vision | 1GB | 약 880~910MB, 최고 1024MB (사진 판정 5회, OOM 없음, 요청당 0.14초) |

- vision(ONNX Runtime)은 한도가 크면 그만큼 메모리를 미리 잡는다(750MB 한도에서는 550~720MB 사용). 한도를 올려도 여유가 비례해서 늘지 않는다.
- `restarts`/`oom=true` 가 생기면 ONNX 세션 메모리 설정(arena 끄기, 스레드 수 제한)을 조정하거나 스왑을 추가한다.
- 이전 한도(vision 750m, api 1g) 파일은 EC2 `~/hawe/compose.prod.yaml.bak-750m`.

### 3-8. 롤백

**이전 버전으로** (GHCR 에 남아 있는 커밋 번호 태그)
```bash
cd ~/hawe
TAG=2fcad56 docker compose -f compose.prod.yaml up -d        # 그 커밋의 이미지를 받아 실행
TAG=2fcad56 docker compose -f compose.prod.yaml up -d api    # api 만
```
태그 목록: GitHub → Packages → `hawe-api` → 버전 목록. 문제가 고쳐지면 다시 `docker compose -f compose.prod.yaml up -d`(latest).

**예전 jar 직접 실행 방식으로** (컨테이너 자체에 문제가 있을 때)
```bash
cd ~/hawe && docker compose -f compose.prod.yaml down        # 8080 을 먼저 비운다
sudo systemctl enable --now hawe.service hawe-vision.service  # ~/hawe-0.0.1-SNAPSHOT.jar, ~/vision-service
```
`hawe.service` 는 작업 폴더(홈)의 `~/.env` 를 읽는다(`~/hawe/.env` 가 아님). `~/hawe/.env` 를 고쳤다면 `~/.env` 에도 반영한다.

### 3-9. 끄기 (비용 절약)

EC2 중지 → RDS 중지. RDS는 7일 후 자동으로 다시 켜지므로 필요하면 다시 중지한다.
EC2를 다시 켜면 컨테이너는 자동으로 다시 뜬다(§3-1).

---

## 4. 코드 변경 이력

| 작업 | 파일 | 상태 |
|---|---|---|
| MySQL 드라이버 추가, DB 주소를 `DB_URL`로 분리 (드라이버는 URL로 자동 선택) | pom.xml, application.properties, .env.example | 커밋됨 (상세: `RDS_MYSQL_CONFIG.md`) |
| 미션 AI를 게이트웨이로 연결 (`LLM_BASE_URL`·`LLM_API_KEY`·`LLM_MODEL`, 없으면 `ANTHROPIC_API_KEY`) | application.properties, MissionAiPropertiesTests | 커밋됨 |
| `.env.example`에 들어간 실제 API 키 삭제 | .env.example | 커밋됨 |
| S3 파일 저장소 (`STORAGE_TYPE=s3`일 때 S3, 기본은 로컬 `./uploads`) | S3FileStorage, StorageProperties, LocalFileStorage, WebMvcConfig, application.properties, pom.xml(AWS SDK 2.55.16), 테스트 | EC2 연결·업로드 확인 완료 (2026-10-16) |
| 컨테이너 배포 (api·vision 이미지, 로컬/운영 Compose) | Dockerfile, .dockerignore, ml/vision-service/Dockerfile·.dockerignore, compose.yaml, compose.prod.yaml | EC2 배포·재부팅 자동 시작 확인 (2026-11-10). 애플리케이션 코드는 바꾸지 않음 |
| 이미지 레지스트리 GHCR (비공개) | Dockerfile 2개(`org.opencontainers.image.source` 라벨), compose.yaml·compose.prod.yaml(`image: ghcr.io/maenghansoo1/...:${TAG:-latest}`) | EC2 가 GHCR 에서 pull 해 실행 확인 (2026-11-10) |

**AI 키 동작**: 서버 시작 시 `LLM_API_KEY`가 있으면 게이트웨이, 없으면 `ANTHROPIC_API_KEY`를 사용. AI 호출이 실패하면 다른 곳으로 넘어가지 않고 미리 적어 둔 예비 문장을 쓴다(`generation_source=AI_FALLBACK`).

**S3 동작**: 버킷은 비공개. DB에는 파일 경로만 저장하고, 응답할 때 1시간 유효한 presigned URL을 만든다. 자격 증명은 `S3_ACCESS_KEY`/`S3_SECRET_KEY`가 있으면 그것, 없으면 EC2 IAM Role.

**미션 프롬프트**: `src/main/resources/prompts/` 의 팀원 프롬프트 2개가 jar 에 포함돼 이미지에 들어간다(로그: `Mission AI prompt: [classpath:prompts/...]`).

---

## 5. 문제 해결

| 증상 | 원인 | 해결 |
|---|---|---|
| `release version 25 not supported` (직접 빌드) | PC 기본 Java가 17 | §2-4처럼 `JAVA_HOME`을 JDK 25로 지정. 컨테이너 빌드는 무관 |
| `Access denied for user 'hawe_db'` | 로컬 DB에 계정이 없거나 비밀번호 불일치 | §2-4 SQL 실행. 로컬 컨테이너 DB 면 `docker compose down -v` 후 다시 up |
| `GSS-API authentication exception` (테스트) | 로컬 `.env`의 `DB_PASSWORD`가 비어 있음 | 비밀번호 입력 |
| `Migration checksum mismatch` | 팀원이 적용된 V1을 수정 | 해당 DB 초기화 (RDS: §3-5, 로컬: HeidiSQL에서 DROP/CREATE 후 GRANT, 컨테이너 DB: `docker compose down -v`) |
| `AuthorizeSecurityGroupIngress ... explicit deny` | 새 보안 그룹 규칙 추가 금지 정책 | 기존 `sgu-sapphire-1`에 규칙 추가, 안 되면 관리자 요청 |
| RDS 접속 시 아무 반응 없이 멈춤 | 3306 규칙 없음 / 다른 보안 그룹 | §1-3 규칙 확인 |
| `Plugin caching_sha2_password could not be loaded` | MariaDB 클라이언트 호환 문제 | `sudo dnf install -y https://dev.mysql.com/get/mysql84-community-release-el9-1.noarch.rpm && sudo dnf install -y mysql-community-client` |
| `generation_source = AI_FALLBACK`, 로그에 `HTTP 401` | EC2 `.env`의 `LLM_API_KEY`가 틀림 (키 길이 확인) | `~/hawe/.env` 수정 후 `docker compose -f compose.prod.yaml up -d --force-recreate api` |
| 게이트웨이 주소 오류 | `https://https://...` 중복 | `LLM_BASE_URL=https://52.78.77.90` |
| 미션이 안 나옴 (`missions: []`) | 기본 발행 시각 09:00 전 | 정상. 확인용은 발행 시각 00:00 그룹으로 (§3-6) |
| PuTTY `Software caused connection abort` | 유휴 연결 끊김 | 재접속 |
| 게이트웨이 상태 직접 확인 | — | `K=$(grep '^LLM_API_KEY=' ~/hawe/.env \| cut -d= -f2-); curl -s -o /dev/null -w "HTTP %{http_code}\n" -H "Authorization: Bearer $K" https://52.78.77.90/v1/models` (200 정상, 401 키 오류) |
| Docker Desktop "WSL not installed" | WSL 미설치 | 관리자 PowerShell `wsl --install` 후 재부팅 |
| `permission denied ... /var/run/docker.sock` (EC2) | docker 그룹이 아직 적용 안 됨 | PuTTY 를 닫고 다시 접속. `groups` 에 docker 가 있어야 함 |
| `docker: unknown shorthand flag: 'f'`, `Invalid Plugins: compose ... exec format error` | Compose 플러그인 파일이 다운로드 오류 페이지(GitHub 504) | §3-2 의 Docker Hub(`docker/compose-bin`) 방식으로 다시 설치 |
| 재부팅 후 부하가 높고 `hawe.service` 가 계속 재시작 | 예전 systemd 서비스가 켜져 8080 충돌 | `sudo systemctl disable --now hawe.service hawe-vision.service` (§3-2) |
| `port is already allocated` (8080) | 예전 jar 나 IDE 로 켠 서버가 8080 사용 | 예전 서비스 끄기 / IDE 서버 종료 |
| api 가 vision 을 못 부름, 사진 판정 없음 | vision 이 healthy 가 아니거나 모델 없음 | `logs vision` 확인. `~/hawe/models` 에 ONNX 3개가 있어야 함 |
| 사진 업로드만 실패 (S3 권한 오류) | 컨테이너가 IMDS 에서 자격 증명을 못 받음(hop limit 1) | §3-6 IMDS 확인. 200 이 아니면 hop limit 을 2로 (`aws ec2 modify-instance-metadata-options --http-put-response-hop-limit 2`, 권한 없으면 운영 측 요청) |
| push 했는데 Packages 에 안 보임, `manifest unknown` | push 가 층만 올리고 끊김(태그 등록 전) | 같은 `docker push` 를 다시 실행 (§3-3 ①) |
| `denied`, `unauthorized` (push·pull) | 로그인 안 함 / 토큰 만료 / 토큰 권한 부족 / 패키지 접근 권한 없음 | 토큰 다시 만들어 `docker login ghcr.io`. 팀원은 패키지 Manage access 에 Read 로 초대돼 있어야 함 |
| pull 후에도 예전 버전이 돎 | `compose.prod.yaml` 의 `image:` 가 예전 이름이거나 `up -d` 를 안 함 | `grep image: compose.prod.yaml` 로 두 줄 모두 `ghcr.io/...` 인지 확인 후 `up -d` |
| `.env` 를 고쳤는데 반영이 안 됨 | `restart` 는 `env_file` 을 다시 읽지 않음 | `up -d --force-recreate api` |

---

## 6. 남은 일

| 순서 | 할 일 | 메모 |
|---|---|---|
| 1 | **LLM API 키 재발급 요청** | 한때 `.env.example`로 GitHub(비공개 레포)에 올라감. 새 키 받으면 PC·EC2 `.env`만 교체 후 api 재생성 |
| 2 | ~~S3를 EC2에 연결~~ | 완료. EC2에 운영 측 IAM Role이 있어 액세스 키 불필요. 컨테이너에서도 확인(2026-11-09) |
| 3 | 로컬 DB 초기화 | 팀원 V1 수정으로 로컬 테스트도 `checksum mismatch` → HeidiSQL에서 DROP/CREATE + GRANT |
| 4 | ~~미션 프롬프트 파일 EC2에 올리기~~ | 해결. 프롬프트가 jar(이미지)에 포함됨 |
| 5 | ~~systemd 등록~~ | 컨테이너(`restart: unless-stopped`)로 대체. 재부팅 자동 시작 확인(2026-11-10) |
| 6 | 앱(iOS·Android)에 서버 주소 연결 | `http://<EC2 IP>:8080`. HTTP라 iOS ATS 예외 / Android cleartext 허용 필요 |
| 7 | 탄력적 IP | IP 고정 (권한 확인 필요). 지금은 EC2 를 켤 때마다 IP 가 바뀜 |
| 8 | SNS 푸시 | Firebase 프로젝트 먼저 필요 |
| 9 | 사진 제출 전체 흐름 확인 | 앱에서 사진 미션 제출 → S3 저장 → vision 판정 → 별이 점수 댓글 |
| 10 | 예전 배포 파일 정리 | 컨테이너가 며칠 안정적이면 EC2 `~/vision-service`, `~/hawe-0.0.1-SNAPSHOT.jar`, systemd 서비스 파일 삭제 (롤백 수단이 사라짐) |
| 11 | 자동 배포(CI/CD) | GitHub Actions 로 테스트 → 이미지 빌드 → GHCR push(§3-3 ① 대체) → EC2 pull. 레지스트리(GHCR)는 준비됨 |
| 12 | GHCR 토큰 만료 관리 | push·pull 토큰 모두 90일. 만료 전에 새로 만들어 PC·EC2 에서 `docker login ghcr.io` |

> `docs/INFRA/AWS_SERVICES.md`는 초기 초안(MariaDB·Bedrock IAM 기준)이라 실제 구성과 다르다. 이 문서를 기준으로 본다.
