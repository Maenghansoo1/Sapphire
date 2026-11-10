# syntax=docker/dockerfile:1
# ============================================================
# HA'WE API 서버 이미지 (Spring Boot, Java 25)
# 빌드: docker build -t hawe-api .        (C:\Sapphire 에서)
# 멀티 스테이지: 1) JDK로 jar를 만들고  2) JRE와 jar만 남긴다
# ============================================================

# ------------------------------------------------------------
# 1단계: 빌드용 (JDK 25 + Maven) — 결과물(jar)만 다음 단계로 넘기고 버려진다
# ------------------------------------------------------------
# eclipse-temurin:25-jdk = Java 25 개발도구가 깔린 리눅스.
# → 내 PC의 Java 버전(17)과 상관없이 항상 Java 25로 빌드된다. JAVA_HOME을 바꿀 필요가 없다.
# AS build : 이 단계에 'build'라는 이름을 붙인다(아래에서 jar를 꺼낼 때 쓴다).
FROM eclipse-temurin:25-jdk AS build

# mvnw는 처음 실행할 때 Maven을 zip으로 받아 압축을 푸는데, 그때 unzip이 필요하다.
# 설치 후 패키지 목록(apt lists)을 지워 층 크기를 줄인다.
RUN apt-get update && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

# [의존성 먼저] ← vision의 requirements.txt와 같은 캐시 전략
# pom.xml(라이브러리 목록)과 Maven 실행 파일만 먼저 복사해서 라이브러리를 내려받는다.
# pom.xml이 그대로면 소스만 고쳤을 때 이 오래 걸리는 단계를 건너뛴다.
COPY .mvn .mvn
COPY mvnw pom.xml ./
# Windows에서 복사하면 실행 권한이 빠질 수 있어 mvnw를 실행 가능하게 만든다.
RUN chmod +x mvnw
# --mount=type=cache : 받은 라이브러리(~/.m2)를 빌드 사이에 재사용하는 캐시 폴더. 이미지에는 안 남는다.
# dependency:go-offline : pom.xml의 라이브러리를 미리 모두 받아 둔다.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

# [소스 복사 후 jar 만들기]
COPY src src
# -DskipTests : 테스트는 로컬 DB가 필요해서 이미지 빌드에서는 건너뛴다(테스트는 따로 실행).
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests

# ------------------------------------------------------------
# 2단계: 실행용 (JRE 25) — 최종 이미지는 이것뿐이다
# ------------------------------------------------------------
# JRE = Java '실행'만 하는 환경. JDK·Maven·소스 코드는 들어가지 않는다.
# → 이미지가 작아지고, 컨테이너 안에 공격에 쓰일 도구가 줄어든다.
FROM eclipse-temurin:25-jre

# GHCR 패키지를 GitHub 저장소와 연결한다.
# → 저장소에 접근 권한이 있는 팀원이 이 이미지도 pull 할 수 있게 권한을 이어받는다(G-5).
LABEL org.opencontainers.image.source=https://github.com/Maenghansoo1/Sapphire


# root가 아닌 일반 계정 app으로 실행하기 위해 계정을 만든다(vision과 같은 이유).
RUN groupadd --system app && useradd --system --gid app --home /app app
WORKDIR /app

# 1단계(build)에서 만든 jar만 꺼내 온다. 이것이 멀티 스테이지의 핵심이다.
COPY --from=build /workspace/target/hawe-0.0.1-SNAPSHOT.jar app.jar

# STORAGE_TYPE=local 일 때 사진을 저장하는 폴더. app 계정이 쓸 수 있게 소유자를 바꾼다.
RUN mkdir uploads && chown app:app uploads
USER app

# TZ : 컨테이너 시간대를 한국으로(로그 시각을 읽기 쉽게).
# MaxRAMPercentage=70 : 컨테이너에 준 메모리의 70%까지만 Java 힙으로 쓴다(EC2 2GB에서 중요).
# ExitOnOutOfMemoryError : 메모리가 부족하면 어정쩡하게 살아 있지 말고 종료 → 재시작되게 한다.
ENV TZ=Asia/Seoul \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"

# 이 컨테이너가 8080을 쓴다는 표시(실제 연결은 docker run -p).
EXPOSE 8080

# 컨테이너가 켜지면 jar를 실행한다.
# .env는 이미지에 없다 → 비밀값은 docker run --env-file 로 실행할 때 넣는다.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
