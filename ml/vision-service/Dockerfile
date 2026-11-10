# ============================================================
# HA'WE vision 서비스 이미지
# 사진이 오늘 미션 주제에 맞는지 점수를 계산하는 FastAPI 서버
# 빌드: docker build -t hawe-vision .
# 실행: docker run --rm -p 8001:8001 -v "${PWD}\models:/models:ro" hawe-vision
# ============================================================

# [바탕 이미지]
# Python 3.12가 미리 설치된 가벼운 리눅스(Debian slim)에서 시작한다.
# → 내 PC에 Python이 없어도, 어느 컴퓨터에서든 같은 버전으로 실행된다.
FROM python:3.12-slim

# [환경변수]
# PYTHONDONTWRITEBYTECODE=1 : .pyc 캐시 파일을 만들지 않는다(이미지를 깔끔하게).
# PYTHONUNBUFFERED=1        : 로그를 모아두지 않고 바로 출력한다(docker logs에 즉시 보이게).
# HAWE_VISION_MODEL_DIR     : 모델 파일 위치. scoring.py가 이 값을 읽는다.
#                             모델은 이미지에 넣지 않고, 실행할 때 /models 폴더로 연결(볼륨)한다.
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    HAWE_VISION_MODEL_DIR=/models

# [실행 계정 만들기]
# 기본값인 root(관리자)로 실행하지 않으려고 일반 계정 app을 만든다.
# → 컨테이너가 공격당해도 할 수 있는 일이 줄어든다.
RUN groupadd --system app && useradd --system --gid app --home /app app

# [작업 폴더]
# 컨테이너 안의 /app 폴더로 이동한다. 아래 COPY·RUN·CMD는 모두 여기서 실행된다.
WORKDIR /app

# [패키지 설치] ← 순서가 중요하다
# 패키지 목록(requirements.txt)만 먼저 복사해서 설치한다.
# Docker는 줄마다 결과를 '층(layer)'으로 저장해 두고, 바뀐 게 없으면 다시 쓰는데(캐시),
# 이렇게 나눠 두면 코드(app.py)만 고쳤을 때 오래 걸리는 pip install을 건너뛴다.
COPY requirements.txt .
# --no-cache-dir : pip 다운로드 캐시를 이미지에 남기지 않는다(이미지 크기 절약).
RUN pip install --no-cache-dir -r requirements.txt

# [코드 복사]
# 자주 바뀌는 코드는 맨 뒤에 복사한다(위의 캐시를 최대한 살리기 위해).
COPY app.py scoring.py distractors.ko.txt ./

# [실행 계정 전환]
# 여기부터 컨테이너 안의 프로그램은 위에서 만든 app 계정으로 실행된다.
USER app

# [포트 표시]
# 이 컨테이너가 8001번 포트를 쓴다고 '문서처럼' 알려주는 줄이다.
# 실제로 PC와 연결하는 건 docker run -p 8001:8001 이 한다.
EXPOSE 8001

# [시작 명령]
# 컨테이너가 켜질 때 uvicorn으로 app.py의 app(FastAPI)을 띄운다.
# --host 0.0.0.0 : 모든 네트워크에서 요청을 받는다.
#                  localhost(127.0.0.1)로 두면 컨테이너 '안'에서만 접속돼서
#                  PC나 다른 컨테이너(api)에서 연결할 수 없다.
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8001"]
