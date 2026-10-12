"""PREPROCESS_SPEC.md 의 정답 예시(golden/*.json)를 만든다. 명세나 rasterize.preprocess 를 바꾸면 다시 실행한다."""
import json
from pathlib import Path

import numpy as np

from rasterize import preprocess, render_strokes

HERE = Path(__file__).resolve().parent
GOLDEN_DIR = HERE / "golden"

# Quick, Draw! simplified 의 고양이 한 장 (key_id 5201136883597312, CC BY 4.0)
CAT_STROKES = [
    [[130, 113, 99, 109, 76, 64, 55, 48, 48, 51, 59, 86, 133, 154, 170, 203, 214, 217, 215, 208, 186, 176, 162, 157, 132],
     [72, 40, 27, 79, 82, 88, 100, 120, 134, 152, 165, 184, 189, 186, 179, 152, 131, 114, 100, 89, 76, 0, 31, 65, 70]],
    [[76, 28, 7], [136, 128, 128]],
    [[76, 23, 0], [160, 164, 175]],
    [[87, 52, 37], [175, 191, 204]],
    [[174, 220, 246, 251], [134, 132, 136, 139]],
    [[175, 255], [147, 168]],
    [[171, 208, 215], [164, 198, 210]],
    [[130, 110, 108, 111, 130, 139, 139, 119], [129, 134, 137, 144, 148, 144, 136, 130]],
    [[107, 106], [96, 113]],
]


def blank(width, height):
    return np.zeros((height, width), dtype=bool)


def cases():
    empty = blank(32, 32)

    too_few = blank(32, 32)
    too_few[10, 10] = too_few[10, 11] = True

    minimum = blank(32, 32)
    minimum[5, 7] = minimum[5, 8] = minimum[6, 7] = True

    horizontal = blank(40, 30)
    horizontal[10:12, 5:35] = True

    vertical = blank(30, 40)
    vertical[3:39, 12] = True

    full = np.ones((10, 10), dtype=bool)

    diagonal = blank(37, 37)
    for i in range(37):
        diagonal[i, i] = True
        if i + 1 < 37:
            diagonal[i, i + 1] = True

    circle = blank(64, 48)
    yy, xx = np.mgrid[0:48, 0:64]
    distance = np.sqrt((xx - 45) ** 2 + (yy - 30) ** 2)
    circle[(distance >= 8) & (distance <= 9.5)] = True

    rect = blank(64, 48)
    rect[5, 3:61] = rect[40, 3:61] = True
    rect[5:41, 3] = rect[5:41, 60] = True

    return [
        ("01_empty", "잉크 없음 → 추론하지 않음", empty),
        ("02_too_few", "잉크 2칸 < MIN_INK(32×32)=3 → 추론하지 않음", too_few),
        ("03_minimum_ink", "잉크 3칸 = MIN_INK, 2×2 상자 → 20×20 으로 크게 늘어남", minimum),
        ("04_horizontal_line", "가로로 긴 선(30×2) → 비율 유지, 위아래 가운데", horizontal),
        ("05_vertical_line", "세로선 1px×36 → S=36, 정수가 아닌 비율(1.8)", vertical),
        ("06_full", "10×10 전부 잉크 → 20×20 전부 1", full),
        ("07_diagonal", "굵기 2 대각선, S=37 → 정수가 아닌 비율", diagonal),
        ("08_circle_off_center", "64×48 의 한쪽에 치우친 원 → 위치가 사라짐", circle),
        ("09_rect_outline", "58×36 사각형 테두리 → 줄어들며 비율 유지", rect),
        ("10_cat_drawing", "Quick, Draw! 고양이를 굵기 8 로 그린 실제 크기 마스크", render_strokes(CAT_STROKES, thickness=8)),
    ]


def to_json(name, description, mask):
    expected = preprocess(mask)
    height, width = mask.shape
    rows = ['    "' + "".join("1" if value else "0" for value in row) + '"' for row in mask]
    parts = [
        "{",
        f'  "name": {json.dumps(name)},',
        f'  "description": {json.dumps(description, ensure_ascii=False)},',
        f'  "width": {width},',
        f'  "height": {height},',
        '  "mask": [',
        ",\n".join(rows),
        "  ],",
    ]
    if expected is None:
        parts.append('  "expected": null')
    else:
        lines = ["    [" + ", ".join(f"{value:.6f}" for value in row) + "]" for row in expected]
        parts += ['  "expected": [', ",\n".join(lines), "  ]"]
    parts.append("}")
    return "\n".join(parts) + "\n"


def main():
    GOLDEN_DIR.mkdir(exist_ok=True)
    for name, description, mask in cases():
        (GOLDEN_DIR / f"{name}.json").write_text(to_json(name, description, mask), encoding="utf-8", newline="\n")
        print(f"{name}: {mask.shape[1]}x{mask.shape[0]}")


if __name__ == "__main__":
    main()
