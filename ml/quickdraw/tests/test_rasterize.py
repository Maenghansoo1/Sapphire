import json
from pathlib import Path

import numpy as np
import pytest

import make_golden
import rasterize
from rasterize import preprocess

GOLDEN_DIR = Path(__file__).resolve().parent.parent / "golden"


def mask_with(width, height, *boxes):
    mask = np.zeros((height, width), dtype=bool)
    for y0, y1, x0, x1 in boxes:
        mask[y0:y1, x0:x1] = True
    return mask


def ink_box(image):
    rows = np.flatnonzero((image > 0).any(axis=1))
    cols = np.flatnonzero((image > 0).any(axis=0))
    return rows[0], rows[-1], cols[0], cols[-1]


def test_min_ink_rule():
    assert rasterize.min_ink(32, 32) == 3
    assert rasterize.min_ink(100, 100) == 5
    assert rasterize.min_ink(1000, 1000) == 500


def test_empty_and_too_few_ink_return_none():
    assert preprocess(np.zeros((32, 32), dtype=bool)) is None
    assert preprocess(mask_with(32, 32, (5, 6, 5, 7))) is None
    assert preprocess(mask_with(32, 32, (5, 6, 5, 8))) is not None


@pytest.mark.parametrize("src", [1, 7, 20, 37, 256])
def test_area_weight_rows_sum_to_one(src):
    weights = rasterize.area_weights(src, rasterize.BOX_SIZE)
    assert weights.shape == (rasterize.BOX_SIZE, src)
    assert np.allclose(weights.sum(axis=1), 1.0)
    assert (weights >= 0).all()


def test_full_mask_fills_center_box_and_keeps_margin():
    out = preprocess(np.ones((10, 10), dtype=bool))
    assert out.shape == (28, 28) and out.dtype == np.float32
    assert np.allclose(out[4:24, 4:24], 1.0)
    out[4:24, 4:24] = 0
    assert not out.any()


def test_values_in_range_and_peak_is_one():
    out = preprocess(mask_with(50, 50, (10, 11, 3, 47), (3, 40, 25, 26)))
    assert out.min() >= 0.0 and out.max() == pytest.approx(1.0)


def test_wide_shape_keeps_aspect_and_is_vertically_centered():
    out = preprocess(mask_with(40, 30, (10, 12, 5, 35)))
    top, bottom, left, right = ink_box(out)
    assert (left, right) == (4, 23)
    assert bottom - top <= 2
    assert abs((top - 4) - (23 - bottom)) <= 1


def test_position_does_not_matter():
    shape = [(0, 3, 0, 12), (3, 9, 5, 7)]
    first = preprocess(mask_with(40, 40, *[(y0 + 2, y1 + 2, x0 + 3, x1 + 3) for y0, y1, x0, x1 in shape]))
    second = preprocess(mask_with(40, 40, *[(y0 + 25, y1 + 25, x0 + 20, x1 + 20) for y0, y1, x0, x1 in shape]))
    assert np.array_equal(first, second)


def test_doubling_resolution_gives_same_result():
    # 가운데 놓을 때 남는 칸(S − bh)이 짝수여야 두 배로 키워도 위치가 정확히 같다
    small = mask_with(30, 30, (2, 4, 2, 24), (4, 20, 12, 15))
    large = np.kron(small, np.ones((2, 2), dtype=bool))
    assert np.allclose(preprocess(small), preprocess(large), atol=1e-6)


def test_ink_rule_for_rgba():
    pixels = np.array([[
        [255, 255, 255, 255],   # 흰 배경
        [0, 0, 0, 255],         # 검정 잉크
        [0xF2, 0xD7, 0x4E, 255],  # 팔레트 연노랑
        [0, 0, 0, 0],           # 투명
        [240, 240, 240, 255],   # 거의 흰색
        [0, 0, 0, 127],         # 반투명 미만
        [0, 0, 0, 128],         # 반투명 이상
    ]], dtype=np.uint8)
    assert rasterize.mask_from_rgba(pixels).tolist() == [[False, True, True, False, False, False, True]]


def test_render_strokes_thickness_and_single_point():
    stroke = [[[0, 255], [128, 128]]]
    thin = rasterize.render_strokes(stroke, thickness=3)
    thick = rasterize.render_strokes(stroke, thickness=20)
    assert thin.dtype == bool and thick.sum() > thin.sum() * 4
    dot = rasterize.render_strokes([[[50], [50]]], thickness=10)
    assert 50 < dot.sum() < 120
    assert not rasterize.render_strokes([]).any()


def test_random_render_is_reproducible_and_in_range():
    first = rasterize.random_render_params(np.random.default_rng(3))
    assert first == rasterize.random_render_params(np.random.default_rng(3))
    assert 3 <= first["thickness"] <= 20
    assert -10 <= first["angle"] <= 10
    assert 0.9 <= first["stretch_x"] <= 1.1 and 0.9 <= first["stretch_y"] <= 1.1
    image = rasterize.drawing_to_input(make_golden.CAT_STROKES, np.random.default_rng(3))
    assert image.shape == (28, 28) and image.max() == pytest.approx(1.0)


def test_golden_files_match_current_preprocess():
    files = sorted(GOLDEN_DIR.glob("*.json"))
    assert len(files) == 10
    for path in files:
        golden = json.loads(path.read_text(encoding="utf-8"))
        mask = np.array([[char == "1" for char in row] for row in golden["mask"]], dtype=bool)
        assert mask.shape == (golden["height"], golden["width"]), path.name
        result = preprocess(mask)
        if golden["expected"] is None:
            assert result is None, path.name
        else:
            assert result is not None, path.name
            assert np.abs(result - np.array(golden["expected"])).max() <= 1e-6, path.name
