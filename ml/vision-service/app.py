"""HA'WE 비전 서비스 — 사진이 오늘 주제에 맞는지 점수(fit)만 돌려준다. 판정·문구는 백엔드가 한다.

  uvicorn app:app --port 8001

개인정보: 사진을 디스크에 저장하지 않고, 로그에 사진·주제를 남기지 않는다(요청 id·상태·지연만).
"""
import logging
import time
import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, File, Form, Request, UploadFile
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

import scoring

MAX_IMAGE_BYTES = 10 * 1024 * 1024
log = logging.getLogger("vision")


class ServiceError(Exception):
    def __init__(self, status, code, message):
        self.status, self.code, self.message = status, code, message


def create_app(scorer=None):
    holder = {"scorer": scorer}

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

    @app.post("/v1/topic-fit")
    def topic_fit(image: UploadFile = File(...), topic: str = Form(...), template: str = Form("PHOTO_NOW")):
        topic = topic.strip()
        if not 1 <= len(topic) <= scoring.MAX_TOPIC_LENGTH:
            raise ServiceError(400, "INVALID_TOPIC", f"topic 은 1~{scoring.MAX_TOPIC_LENGTH}자여야 합니다")
        if template not in scoring.TEMPLATES:
            raise ServiceError(400, "UNSUPPORTED_TEMPLATE", f"template 은 {', '.join(scoring.TEMPLATES)} 중 하나여야 합니다")
        data = image.file.read(MAX_IMAGE_BYTES + 1)
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
app = create_app()
