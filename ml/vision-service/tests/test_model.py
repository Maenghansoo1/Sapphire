"""실제 모델로 하는 검사. 모델(약 1GB)을 받아야 해서 기본은 건너뛴다: HAWE_VISION_MODEL_TESTS=1 pytest"""
import os

import pytest
from PIL import Image

pytestmark = pytest.mark.skipif(os.environ.get("HAWE_VISION_MODEL_TESTS") != "1", reason="실제 모델 검사는 선택")


@pytest.fixture(scope="module")
def scorer():
    import scoring

    return scoring.TopicFitScorer()


def test_color_matches_beat_other_colors(scorer):
    for color, rgb in (("빨강", (220, 30, 30)), ("파랑", (30, 60, 220)), ("노랑", (240, 220, 40))):
        image = Image.new("RGB", (224, 224), "white")
        image.paste(Image.new("RGB", (140, 140), rgb), (42, 42))
        vector = scorer.image_vector(image)
        mine = scorer.score_vector(vector, color, "PHOTO_COLOR")["fit"]
        others = [scorer.score_vector(vector, other, "PHOTO_COLOR")["fit"] for other in ("빨강", "파랑", "노랑") if other != color]
        assert all(0.0 <= value <= 1.0 for value in [mine, *others])
        assert mine > max(others), color


def test_same_input_gives_same_score(scorer):
    image = Image.new("RGB", (224, 224), (30, 60, 220))
    assert scorer.score(image, "하늘", "PHOTO_NOW") == scorer.score(image, "하늘", "PHOTO_NOW")
