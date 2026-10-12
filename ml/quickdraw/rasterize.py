"""획 좌표 → 잉크 마스크 → 28×28 모델 입력. 규칙은 PREPROCESS_SPEC.md 와 1:1 로 맞춘다."""
import math

import numpy as np
from PIL import Image, ImageDraw

OUTPUT_SIZE = 28
BOX_SIZE = 20
MARGIN = (OUTPUT_SIZE - BOX_SIZE) // 2
INK_MIN_ALPHA = 128
INK_MAX_CHANNEL = 235

THICKNESS_RANGE = (3, 20)
ROTATION_DEGREES = 10.0
STRETCH = 0.1
EVAL_THICKNESS = 8


def min_ink(width, height):
    return max(3, (width * height + 1999) // 2000)


def area_weights(src, dst):
    """결과 칸 i 가 원본 구간 [i*src/dst, (i+1)*src/dst) 와 겹치는 비율. 각 행의 합 = 1."""
    scale = src / dst
    weights = np.zeros((dst, src), dtype=np.float64)
    for i in range(dst):
        start, end = i * src / dst, (i + 1) * src / dst
        k = int(math.floor(start))
        while k < src and k < end:
            overlap = min(end, k + 1) - max(start, k)
            if overlap > 0:
                weights[i, k] = overlap / scale
            k += 1
    return weights


def preprocess(mask):
    """잉크 마스크(h×w, 참/거짓) → 28×28 float32. 너무 비었으면 None."""
    ink = np.asarray(mask, dtype=bool)
    height, width = ink.shape
    if int(ink.sum()) < min_ink(width, height):
        return None
    rows = np.flatnonzero(ink.any(axis=1))
    cols = np.flatnonzero(ink.any(axis=0))
    crop = ink[rows[0]:rows[-1] + 1, cols[0]:cols[-1] + 1].astype(np.float64)
    bh, bw = crop.shape
    side = max(bh, bw)
    square = np.zeros((side, side), dtype=np.float64)
    oy, ox = (side - bh) // 2, (side - bw) // 2
    square[oy:oy + bh, ox:ox + bw] = crop
    weights = area_weights(side, BOX_SIZE)
    small = weights @ square @ weights.T
    peak = small.max()
    if peak > 0:
        small = small / peak
    out = np.zeros((OUTPUT_SIZE, OUTPUT_SIZE), dtype=np.float32)
    out[MARGIN:MARGIN + BOX_SIZE, MARGIN:MARGIN + BOX_SIZE] = small
    return out


def mask_from_rgba(rgba):
    """RGBA 배열(h×w×4, 0~255) → 잉크 마스크. 투명 배경 검정 잉크와 흰 배경 색 잉크 모두 같은 규칙."""
    pixels = np.asarray(rgba)
    return (pixels[..., 3] >= INK_MIN_ALPHA) & (pixels[..., :3].min(axis=-1) < INK_MAX_CHANNEL)


def mask_from_image(path):
    with Image.open(path) as image:
        return mask_from_rgba(np.asarray(image.convert("RGBA")))


def render_strokes(strokes, thickness=EVAL_THICKNESS, angle=0.0, stretch_x=1.0, stretch_y=1.0):
    """Quick, Draw! 획([[x...], [y...]] 목록)을 둥근 끝·이음 선으로 그린 잉크 마스크."""
    radians = math.radians(angle)
    cos, sin = math.cos(radians), math.sin(radians)
    lines = []
    for stroke in strokes:
        xs = np.asarray(stroke[0], dtype=np.float64) - 127.5
        ys = np.asarray(stroke[1], dtype=np.float64) - 127.5
        if xs.size == 0:
            continue
        xs, ys = xs * stretch_x, ys * stretch_y
        lines.append(np.stack([xs * cos - ys * sin, xs * sin + ys * cos], axis=1))
    if not lines:
        return np.zeros((1, 1), dtype=bool)
    every = np.concatenate(lines)
    pad = thickness + 2
    shift = pad - every.min(axis=0)
    size = np.ceil(every.max(axis=0) + shift + pad).astype(int)
    canvas = Image.new("L", (int(size[0]), int(size[1])), 0)
    draw = ImageDraw.Draw(canvas)
    radius = thickness / 2
    for line in lines:
        points = [(float(x), float(y)) for x, y in line + shift]
        if len(points) > 1:
            draw.line(points, fill=255, width=thickness, joint="curve")
        for x, y in points:
            draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=255)
    return np.asarray(canvas) > 127


def random_render_params(rng):
    """학습용 무작위 그리기 설정 (굵기·회전·가로세로 늘이기)."""
    return {
        "thickness": int(rng.integers(THICKNESS_RANGE[0], THICKNESS_RANGE[1] + 1)),
        "angle": float(rng.uniform(-ROTATION_DEGREES, ROTATION_DEGREES)),
        "stretch_x": float(rng.uniform(1 - STRETCH, 1 + STRETCH)),
        "stretch_y": float(rng.uniform(1 - STRETCH, 1 + STRETCH)),
    }


def drawing_to_input(strokes, rng=None):
    """학습 그림 한 장 → 28×28. rng 를 주면 무작위 설정, 없으면 평가용 고정 설정."""
    params = random_render_params(rng) if rng is not None else {"thickness": EVAL_THICKNESS}
    result = preprocess(render_strokes(strokes, **params))
    return result if result is not None else np.zeros((OUTPUT_SIZE, OUTPUT_SIZE), dtype=np.float32)
