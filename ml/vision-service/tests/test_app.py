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
