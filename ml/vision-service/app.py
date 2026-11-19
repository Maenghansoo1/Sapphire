"""HA'WE 비전 서비스 — 사진이 오늘 주제에 맞는지 점수(fit)만 돌려준다. 판정·문구는 백엔드가 한다.

  uvicorn app:app --port 8001

사진은 파일(image)로 받거나, S3 키(imageKey)로 받아 HAWE_VISION_S3_BUCKET 에서 직접 읽는다(Lambda 배포용).
HAWE_VISION_API_KEY 가 있으면 /v1/topic-fit 요청에 같은 값의 X-Hawe-Vision-Key 헤더가 있어야 한다.

개인정보: 사진을 디스크에 저장하지 않고, 로그에 사진·주제·키를 남기지 않는다(요청 id·상태·지연만).
"""
import hmac
import logging
import os
import re
import time
import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, File, Form, Request, UploadFile
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel

import scoring

MAX_IMAGE_BYTES = 10 * 1024 * 1024
# 백엔드 S3FileStorage 가 만드는 키 모양(디렉터리/UUID.확장자)만 허용한다.
IMAGE_KEY_PATTERN = re.compile(r"[a-z0-9_-]+/[A-Za-z0-9-]+\.[a-z0-9]+")
API_KEY_HEADER = "X-Hawe-Vision-Key"
log = logging.getLogger("vision")


class ServiceError(Exception):
    def __init__(self, status, code, message):
        self.status, self.code, self.message = status, code, message


class ImageNotFound(Exception):
    pass


class TopicFitEvent(BaseModel):
    """/events 로 들어오는 Lambda 호출 내용."""
    imageKey: str
    topic: str
    template: str = "PHOTO_NOW"


class S3ImageLoader:
    """imageKey 로 S3 에서 사진을 읽는다. boto3 는 키 방식을 쓸 때만 불러온다(로컬 개발에는 필요 없음)."""

    def __init__(self, bucket):
        self.bucket = bucket
        self._client = None

    def load(self, key, limit):
        if self._client is None:
            import boto3
            self._client = boto3.client("s3")
        try:
            response = self._client.get_object(Bucket=self.bucket, Key=key)
        except self._client.exceptions.NoSuchKey:
            raise ImageNotFound()
        return response["Body"].read(limit + 1)


def default_image_loader():
    bucket = os.environ.get("HAWE_VISION_S3_BUCKET", "").strip()
    return S3ImageLoader(bucket) if bucket else None


def create_app(scorer=None, image_loader=None, api_key=None):
    holder = {"scorer": scorer}
    if api_key is None:
        api_key = os.environ.get("HAWE_VISION_API_KEY", "").strip()

    @asynccontextmanager
    async def lifespan(_app):
        if holder["scorer"] is None:
            started = time.perf_counter()
            holder["scorer"] = scoring.TopicFitScorer()
            log.info("model loaded in %.1fs (%s)", time.perf_counter() - started, holder["scorer"].backend)
        yield

    app = FastAPI(title="HA'WE vision service", version=scoring.VERSION, lifespan=lifespan)

    @app.exception_handler(ServiceError)
    async def service_error(_request, error: ServiceError):
        return JSONResponse(status_code=error.status, content={"code": error.code, "message": error.message})

    @app.exception_handler(RequestValidationError)
    async def validation_error(_request, _error):
        return JSONResponse(status_code=400, content={"code": "VALIDATION_ERROR", "message": "image 와 topic 이 필요합니다"})

    @app.middleware("http")
    async def access_log(request: Request, call_next):
        request_id = uuid.uuid4().hex[:8]
        started = time.perf_counter()
        response = await call_next(request)
        log.info("%s %s %s %d %.0fms", request_id, request.method, request.url.path, response.status_code,
                 (time.perf_counter() - started) * 1000)
        return response

    def version():
        return getattr(holder["scorer"], "version", scoring.VERSION)

    @app.get("/healthz")
    def healthz():
        return {"status": "ok", "model": scoring.MODEL_NAME, "version": version(),
                "backend": getattr(holder["scorer"], "backend", None)}

    @app.middleware("http")
    async def require_api_key(request: Request, call_next):
        # 입력 검증보다 먼저 막는다. /healthz 는 Lambda 준비 확인용이라 열어 둔다.
        if api_key and request.url.path.startswith("/v1/") \
                and not hmac.compare_digest(request.headers.get(API_KEY_HEADER, ""), api_key):
            return JSONResponse(status_code=401, content={"code": "UNAUTHORIZED", "message": "인증 헤더가 올바르지 않습니다"})
        return await call_next(request)

    def read_image(image, image_key):
        if (image is None) == (image_key is None):
            raise ServiceError(400, "VALIDATION_ERROR", "image 와 imageKey 중 하나만 보내야 합니다")
        if image is not None:
            return image.file.read(MAX_IMAGE_BYTES + 1)
        if image_loader is None:
            raise ServiceError(400, "IMAGE_KEY_UNSUPPORTED", "이 서버는 imageKey 를 지원하지 않습니다")
        if not IMAGE_KEY_PATTERN.fullmatch(image_key):
            raise ServiceError(400, "INVALID_IMAGE_KEY", "imageKey 형식이 올바르지 않습니다")
        try:
            return image_loader.load(image_key, MAX_IMAGE_BYTES)
        except ImageNotFound:
            raise ServiceError(404, "IMAGE_NOT_FOUND", "사진을 찾을 수 없습니다")
        except Exception as error:
            log.warning("image load failed (%s)", type(error).__name__)
            raise ServiceError(502, "IMAGE_LOAD_FAILED", "사진을 불러오지 못했습니다")

    @app.post("/v1/topic-fit")
    def topic_fit(topic: str = Form(...), template: str = Form("PHOTO_NOW"),
                  image: UploadFile | None = File(None), imageKey: str | None = Form(None)):
        return evaluate(topic, template, lambda: read_image(image, imageKey))

    @app.post("/events")
    def topic_fit_event(event: TopicFitEvent):
        """Lambda 를 SDK 로 직접 호출(Invoke)할 때 Lambda Web Adapter 가 호출 내용(JSON)을 여기로 넘긴다.
        Lambda 호출 권한(IAM)으로 보호되므로 API 키를 따로 보지 않는다. 사진은 imageKey 로만 받는다."""
        return evaluate(event.topic, event.template, lambda: read_image(None, event.imageKey))

    def evaluate(topic, template, load_image):
        topic = topic.strip()
        if not 1 <= len(topic) <= scoring.MAX_TOPIC_LENGTH:
            raise ServiceError(400, "INVALID_TOPIC", f"topic 은 1~{scoring.MAX_TOPIC_LENGTH}자여야 합니다")
        if template not in scoring.TEMPLATES:
            raise ServiceError(400, "UNSUPPORTED_TEMPLATE", f"template 은 {', '.join(scoring.TEMPLATES)} 중 하나여야 합니다")
        data = load_image()
        if len(data) > MAX_IMAGE_BYTES:
            raise ServiceError(413, "FILE_TOO_LARGE", "이미지는 10MB 이하여야 합니다")
        try:
            picture = scoring.decode_image(data)
        except ValueError:
            raise ServiceError(400, "INVALID_IMAGE", "이미지를 읽을 수 없습니다")
        result = holder["scorer"].score(picture, topic, template)
        return {"fit": round(result["fit"], 6), "cosine": round(result["cosine"], 6),
                "model": scoring.MODEL_NAME, "version": version()}

    return app


logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
app = create_app(image_loader=default_image_loader())
