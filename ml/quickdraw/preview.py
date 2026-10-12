"""받은 그림을 28×28 로 바꿔 한 장의 격자 이미지로 저장한다(눈으로 확인용).

  python preview.py --data data/smoke            # 범주마다 한 줄: 평가용 고정 설정 6장 | 학습용 무작위 설정 6장
"""
import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image

from rasterize import OUTPUT_SIZE, drawing_to_input

HERE = Path(__file__).resolve().parent


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", default=str(HERE / "data" / "smoke"))
    parser.add_argument("--split", default="train")
    parser.add_argument("--per-row", type=int, default=6)
    parser.add_argument("--zoom", type=int, default=4)
    parser.add_argument("--seed", type=int, default=0)
    parser.add_argument("--out", default=None)
    args = parser.parse_args(argv)

    data = Path(args.data)
    files = sorted((data / args.split).glob("*.ndjson"))
    if not files:
        raise SystemExit(f"{data / args.split} 에 .ndjson 이 없습니다. download.py 를 먼저 실행하세요.")
    rng = np.random.default_rng(args.seed)
    cell, gap = OUTPUT_SIZE, 2
    width = (args.per_row * 2) * (cell + gap) + gap * 4
    canvas = np.full((len(files) * (cell + gap) + gap, width), 0.25, dtype=np.float32)

    for row, path in enumerate(files):
        with path.open(encoding="utf-8") as file:
            drawings = [json.loads(next(file))["drawing"] for _ in range(args.per_row)]
        images = [drawing_to_input(d) for d in drawings] + [drawing_to_input(d, rng) for d in drawings]
        for col, image in enumerate(images):
            x = gap + col * (cell + gap) + (gap * 3 if col >= args.per_row else 0)
            y = gap + row * (cell + gap)
            canvas[y:y + cell, x:x + cell] = image
        print(f"{row}: {path.stem}")

    out = Path(args.out) if args.out else data / f"preview_{args.split}.png"
    picture = Image.fromarray((255 - canvas * 255).astype(np.uint8))
    picture.resize((picture.width * args.zoom, picture.height * args.zoom), Image.NEAREST).save(out)
    print(f"저장: {out}")


if __name__ == "__main__":
    main()
