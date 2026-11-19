import io

import pytest
from fastapi.testclient import TestClient
from PIL import Image

import app as service


class FakeScorer:
    def __init__(self):
        self.calls = []

    def score(self, image, topic, template):
        self.calls.append((image.size, topic, template))
        return {"fit": 0.8123456789, "cosine": 0.31}


@pytest.fixture
def client():
    scorer = FakeScorer()
    with TestClient(service.create_app(scorer)) as test_client:
        test_client.scorer = scorer
        yield test_client


def png(width=8, height=8):
    buffer = io.BytesIO()
    Image.new("RGB", (width, height), "blue").save(buffer, "PNG")
    return buffer.getvalue()


def post(client, image=None, **data):
    return client.post("/v1/topic-fit", files={"image": ("x.png", png() if image is None else image)}, data=data)


def test_healthz(client):
    body = client.get("/healthz").json()
    assert body["status"] == "ok" and body["model"] and body["version"]


def test_topic_fit_returns_scores(client):
    response = post(client, topic=" 창밖 풍경 ", template="PHOTO_NOW")
    assert response.status_code == 200
    body = response.json()
    assert body["fit"] == 0.812346 and body["cosine"] == 0.31
    assert set(body) == {"fit", "cosine", "model", "version"}
    assert client.scorer.calls == [((8, 8), "창밖 풍경", "PHOTO_NOW")]


def test_template_defaults_to_photo_now(client):
    assert post(client, topic="하늘").status_code == 200
    assert client.scorer.calls[0][2] == "PHOTO_NOW"


@pytest.mark.parametrize("data, code", [
    ({"topic": "   "}, "INVALID_TOPIC"),
    ({"topic": "가" * 61}, "INVALID_TOPIC"),
    ({"topic": "하늘", "template": "DRAW_SKETCH"}, "UNSUPPORTED_TEMPLATE"),
    ({}, "VALIDATION_ERROR"),
])
def test_bad_requests_are_400(client, data, code):
    response = post(client, **data)
    assert response.status_code == 400 and response.json()["code"] == code
    assert client.scorer.calls == []


def test_broken_image_is_400(client):
    response = post(client, image=b"broken", topic="하늘")
    assert response.status_code == 400 and response.json()["code"] == "INVALID_IMAGE"


def test_missing_image_is_400(client):
    response = client.post("/v1/topic-fit", data={"topic": "하늘"})
    assert response.status_code == 400


def test_too_large_is_413(client):
    response = post(client, image=b"0" * (service.MAX_IMAGE_BYTES + 1), topic="하늘")
    assert response.status_code == 413 and response.json()["code"] == "FILE_TOO_LARGE"


class FakeLoader:
    def __init__(self, data=None, error=None):
        self.data, self.error, self.calls = data, error, []

    def load(self, key, limit):
        self.calls.append((key, limit))
        if self.error:
            raise self.error
        return self.data


@pytest.fixture
def keyed_client():
    opened = []

    def make(loader, api_key=""):
        test_client = TestClient(service.create_app(FakeScorer(), image_loader=loader, api_key=api_key))
        test_client.__enter__()
        opened.append(test_client)
        return test_client

    yield make
    for test_client in opened:
        test_client.__exit__(None, None, None)


KEY = "submissions/3f2a9c1e-1111-2222-3333-444455556666.jpg"


def test_image_key_reads_from_loader(keyed_client):
    loader = FakeLoader(data=png())
    client = keyed_client(loader)
    response = client.post("/v1/topic-fit", data={"topic": "하늘", "imageKey": KEY})
    assert response.status_code == 200 and response.json()["fit"] == 0.812346
    assert loader.calls == [(KEY, service.MAX_IMAGE_BYTES)]


def test_image_and_key_together_is_400(keyed_client):
    client = keyed_client(FakeLoader(data=png()))
    response = client.post("/v1/topic-fit", files={"image": ("x.png", png())}, data={"topic": "하늘", "imageKey": KEY})
    assert response.status_code == 400 and response.json()["code"] == "VALIDATION_ERROR"


def test_image_key_without_loader_is_400(client):
    response = client.post("/v1/topic-fit", data={"topic": "하늘", "imageKey": KEY})
    assert response.status_code == 400 and response.json()["code"] == "IMAGE_KEY_UNSUPPORTED"


@pytest.mark.parametrize("key", ["../etc/passwd", "submissions/a b.jpg", "Submissions/x.jpg", "x.jpg"])
def test_invalid_image_key_is_400(keyed_client, key):
    loader = FakeLoader(data=png())
    response = keyed_client(loader).post("/v1/topic-fit", data={"topic": "하늘", "imageKey": key})
    assert response.status_code == 400 and response.json()["code"] == "INVALID_IMAGE_KEY"
    assert loader.calls == []


def test_missing_object_is_404(keyed_client):
    response = keyed_client(FakeLoader(error=service.ImageNotFound())).post(
        "/v1/topic-fit", data={"topic": "하늘", "imageKey": KEY})
    assert response.status_code == 404 and response.json()["code"] == "IMAGE_NOT_FOUND"


def test_loader_failure_is_502(keyed_client):
    response = keyed_client(FakeLoader(error=RuntimeError("boom"))).post(
        "/v1/topic-fit", data={"topic": "하늘", "imageKey": KEY})
    assert response.status_code == 502 and response.json()["code"] == "IMAGE_LOAD_FAILED"


def test_too_large_object_is_413(keyed_client):
    loader = FakeLoader(data=b"0" * (service.MAX_IMAGE_BYTES + 1))
    response = keyed_client(loader).post("/v1/topic-fit", data={"topic": "하늘", "imageKey": KEY})
    assert response.status_code == 413 and response.json()["code"] == "FILE_TOO_LARGE"


def test_api_key_required_when_configured(keyed_client):
    client = keyed_client(FakeLoader(data=png()), api_key="secret")
    assert client.post("/v1/topic-fit", data={"topic": "하늘", "imageKey": KEY}).status_code == 401
    wrong = client.post("/v1/topic-fit", data={"topic": "하늘", "imageKey": KEY}, headers={"X-Hawe-Vision-Key": "nope"})
    assert wrong.status_code == 401 and wrong.json()["code"] == "UNAUTHORIZED"
    ok = client.post("/v1/topic-fit", data={"topic": "하늘", "imageKey": KEY}, headers={"X-Hawe-Vision-Key": "secret"})
    assert ok.status_code == 200


def test_healthz_is_open_with_api_key(keyed_client):
    client = keyed_client(FakeLoader(data=png()), api_key="secret")
    assert client.get("/healthz").status_code == 200


def test_event_reads_image_key(keyed_client):
    loader = FakeLoader(data=png())
    response = keyed_client(loader).post("/events", json={"imageKey": KEY, "topic": "하늘", "template": "PHOTO_COLOR"})
    assert response.status_code == 200 and response.json()["fit"] == 0.812346
    assert loader.calls == [(KEY, service.MAX_IMAGE_BYTES)]


def test_event_ignores_api_key(keyed_client):
    # Lambda 직접 호출은 IAM 이 막으므로 헤더가 없어도 된다.
    client = keyed_client(FakeLoader(data=png()), api_key="secret")
    assert client.post("/events", json={"imageKey": KEY, "topic": "하늘"}).status_code == 200


@pytest.mark.parametrize("body, code", [
    ({"topic": "하늘"}, "VALIDATION_ERROR"),
    ({"imageKey": KEY}, "VALIDATION_ERROR"),
    ({"imageKey": "../x", "topic": "하늘"}, "INVALID_IMAGE_KEY"),
    ({"imageKey": KEY, "topic": "하늘", "template": "DRAW_SKETCH"}, "UNSUPPORTED_TEMPLATE"),
])
def test_bad_events_are_400(keyed_client, body, code):
    response = keyed_client(FakeLoader(data=png())).post("/events", json=body)
    assert response.status_code == 400 and response.json()["code"] == code
