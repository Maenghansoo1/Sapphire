"""ONNX 실행 방식 검사. 전처리·파일 고르기는 항상, 실제 모델 비교는 HAWE_VISION_MODEL_TESTS=1 일 때만."""
import importlib.util
import os

import numpy as np
import pytest
from PIL import Image

import scoring

MODEL_TESTS = os.environ.get("HAWE_VISION_MODEL_TESTS") == "1"


def test_clip_pixels_shape_and_normalization():
    pixels = scoring.clip_pixels(Image.new("RGB", (640, 480), (128, 128, 128)))
    assert pixels.shape == (1, 3, 224, 224) and pixels.dtype == np.float32
    expected = (128 / 255 - scoring.CLIP_MEAN) / scoring.CLIP_STD
    assert np.allclose(pixels[0, :, 112, 112], expected, atol=1e-5)


def test_clip_pixels_crops_the_center_of_the_long_side():
    # 왼쪽 반은 검정, 오른쪽 반은 흰색인 가로 사진 → 가운데를 자르면 양쪽이 반반 남는다
    image = Image.new("RGB", (448, 224), "black")
    image.paste(Image.new("RGB", (224, 224), "white"), (224, 0))
    pixels = scoring.clip_pixels(image)[0, 0]
    assert pixels[:, :100].mean() < 0 < pixels[:, 124:].mean()


def test_pick_variant_prefers_fp16(tmp_path):
    assert scoring.pick_variant(tmp_path, None) is None
    for name in ("image_encoder.onnx", "text_encoder.onnx", "tokenizer.json"):
        (tmp_path / name).write_bytes(b"")
    assert scoring.pick_variant(tmp_path, None) == ""
    for name in ("image_encoder.fp16.onnx", "text_encoder.fp16.onnx"):
        (tmp_path / name).write_bytes(b"")
    assert scoring.pick_variant(tmp_path, None) == "fp16"
    assert scoring.pick_variant(tmp_path, "") == ""


def test_onnx_backend_without_files_explains(tmp_path):
    with pytest.raises(FileNotFoundError, match="export_onnx.py"):
        scoring.load_encoders("onnx", model_dir=tmp_path, variant=None)


@pytest.mark.skipif(not MODEL_TESTS or scoring.pick_variant() is None or importlib.util.find_spec("sentence_transformers") is None,
                    reason="실제 모델 비교는 선택(ONNX 파일·PyTorch 필요)")
def test_onnx_matches_torch_scores():
    torch_scorer = scoring.TopicFitScorer(backend="torch")
    onnx_scorer = scoring.TopicFitScorer(backend="onnx")
    image = Image.new("RGB", (400, 300), "white")
    image.paste(Image.new("RGB", (200, 150), (30, 60, 220)), (100, 75))
    for topic, template in (("파랑", "PHOTO_COLOR"), ("하늘", "PHOTO_NOW"), ("냉장고 안 음식", "PHOTO_NOW")):
        expected = torch_scorer.score(image, topic, template)["fit"]
        actual = onnx_scorer.score(image, topic, template)["fit"]
        assert abs(expected - actual) < 1e-3, (topic, expected, actual)