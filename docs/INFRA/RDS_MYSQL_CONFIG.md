# DB 접속 설정 변경: AWS RDS(MySQL) 대응

> 작성일: 2026-10-13
> 대상: 백엔드를 로컬에서 실행하거나 EC2에 배포하는 팀원

## 1. 왜 바꿨나

- 운영 DB(AWS RDS)는 계정 정책상 **MySQL**로만 만들 수 있다(MariaDB 불가).
- 기존 설정은 DB 주소가 `localhost` MariaDB로 고정되어 있어 EC2에서 RDS에 접속할 수 없었다.
- 목표: **로컬은 지금처럼 MariaDB**, **배포 환경은 RDS MySQL**을 코드 수정 없이 `.env` 설정만으로 고른다.

## 2. 무엇을 바꿨나

| 파일 | 변경 내용 |
|---|---|
| `pom.xml` | MySQL 드라이버 `mysql-connector-j` 추가. MariaDB 드라이버는 그대로 유지 |
| `application.properties` | `spring.datasource.url`을 `${DB_URL:기존 로컬 주소}`로 변경, `driver-class-name` 삭제 |
| `.env.example` | `DB_URL`(주석 상태), `LLM_BASE_URL`, `LLM_API_KEY` 항목 추가 |

자바 코드(서비스, 매퍼, 마이그레이션 SQL)는 바꾸지 않았다.

### 동작 방식
- `DB_URL`이 없으면 기본값(기존 주소 `jdbc:mariadb://localhost:3306/hawe?...`)을 그대로 쓴다.
- JDBC 드라이버는 URL 앞부분을 보고 Spring이 자동으로 고른다.
  - `jdbc:mariadb://...` → MariaDB 드라이버
  - `jdbc:mysql://...` → MySQL 드라이버

| 환경 | `.env`의 `DB_URL` | 접속 대상 |
|---|---|---|
| 로컬 PC | 주석 처리 (없음) | localhost MariaDB (기존과 동일) |
| EC2 | `jdbc:mysql://<RDS 엔드포인트>:3306/hawe?...` | RDS MySQL |

## 3. 팀원이 할 일

### 로컬 개발
- **아무것도 안 해도 된다.** 기존 `.env` 그대로 실행하면 된다.
- 단, `.env`에 **`DB_URL=` 처럼 빈 값으로 두지 말 것.** 빈 문자열이 들어가 기본 주소 대신 빈 주소로 접속하다가 실패한다. 로컬에서는 줄을 지우거나 `#`으로 주석 처리한다.
- 빌드는 **JDK 25**가 필요하다. 기본 Java가 17/21이면 아래처럼 지정한다.
  ```powershell
  $env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"   # 본인 JDK 25 경로
  .\mvnw.cmd clean package -DskipTests
  ```

### EC2 배포
EC2의 `~/.env`에 아래 값을 넣는다(`<...>`는 실제 값).
```properties
DB_URL=jdbc:mysql://<RDS 엔드포인트>:3306/hawe?connectionTimeZone=Asia/Seoul&forceConnectionTimeZoneToSession=true
DB_USERNAME=<RDS 사용자>
DB_PASSWORD=<RDS 암호>
JWT_SECRET=<openssl rand -base64 32 결과>
LLM_BASE_URL=<게이트웨이 주소>
LLM_API_KEY=<키>
```
- `connectionTimeZone=Asia/Seoul&forceConnectionTimeZoneToSession=true`: DB 세션 시간을 한국 시간으로 맞춘다(로컬 주소의 `time_zone='+09:00'`과 같은 역할). 미션 발행·마감·지각 판정이 한국 시간 기준이라 필요하다.
- `.env`는 `.gitignore` 대상이다. 키·암호가 들어가므로 절대 커밋하지 않는다.

## 4. 현재 배포 상태 (2026-10-13)

- EC2(Amazon Linux 2023, Corretto 25)에서 jar를 `nohup`으로 실행, `/actuator/health` → `UP` 확인
- RDS MySQL `hawe` DB에 Flyway 마이그레이션(V1, V2) 적용 완료
- EC2와 RDS는 같은 보안 그룹을 쓰고, 그 그룹에 3306(소스: 자기 자신) 규칙으로 연결
- 아직 남은 것: 탄력적 IP, systemd 자동 실행, S3 파일 저장 연결(현재는 EC2 디스크 `./uploads`), LLM 미션 생성 코드, SNS 푸시
