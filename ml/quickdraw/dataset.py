"""download.py 결과(ndjson 획) → 28×28 uint8 배열 캐시(.npz). 매 epoch 다시 그리지 않도록 한 번만 변환한다.

학습(train)은 그림마다 무작위 설정(굵기·회전·늘이기)으로, 검증·테스트는 고정 설정으로 그린다.
그림마다 시드가 (seed, 범주 번호, 줄 번호) 로 정해지므로 작업자 수와 관계없이 결과가 같다.
"""
import json
import os
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

import numpy as np

from download import SPLITS, file_stem, load_categories
from rasterize import OUTPUT_SIZE, drawing_to_input


def present_classes(data_dir, categories):
    """train/val/test 파일이 모두 있는 범주만 categories.json 순서대로. 모델 출력 번호 = 이 목록의 위치."""
    data = Path(data_dir)
    return [item for item in categories
            if all((data / split / f"{file_stem(item['quickdraw'])}.ndjson").exists() for split in SPLITS)]


def _render_file(task):
    path, label, augment, seed = task
    with open(path, encoding="utf-8") as file:
        drawings = [json.loads(line)["drawing"] for line in file if line.strip()]
    images = np.zeros((len(drawings), OUTPUT_SIZE, OUTPUT_SIZE), dtype=np.uint8)
    for index, strokes in enumerate(drawings):
        rng = np.random.default_rng([seed, label, index]) if augment else None
        images[index] = np.round(drawing_to_input(strokes, rng) * 255).astype(np.uint8)
    return images, np.full(len(drawings), label, dtype=np.int32)


def build_split(data_dir, classes, split, seed=42, workers=None):
    data = Path(data_dir)
    tasks = [(str(data / split / f"{file_stem(item['quickdraw'])}.ndjson"), label, split == "train", seed)
             for label, item in enumerate(classes)]
    workers = workers or max(1, (os.cpu_count() or 2) - 1)
    if workers == 1:
        results = [_render_file(task) for task in tasks]
    else:
        with ProcessPoolExecutor(max_workers=workers) as pool:
            results = list(pool.map(_render_file, tasks))
    return np.concatenate([images for images, _ in results]), np.concatenate([labels for _, labels in results])


def load_or_build(data_dir, categories_path, cache_dir=None, seed=42, workers=None, rebuild=False, log=print):
    """{split: (images uint8 N×28×28, labels int32 N)} 와 범주 목록. 캐시가 같은 설정이면 다시 쓰지 않는다."""
    data = Path(data_dir)
    cache = Path(cache_dir) if cache_dir else data / "cache"
    classes = present_classes(data, load_categories(categories_path))
    if not classes:
        raise SystemExit(f"{data} 에 받은 범주가 없습니다. download.py 를 먼저 실행하세요.")
    names = [item["quickdraw"] for item in classes]
    meta_path = cache / "meta.json"
    meta = {"classes": names, "seed": seed, "output_size": OUTPUT_SIZE}
    reuse = not rebuild and meta_path.exists() and json.loads(meta_path.read_text(encoding="utf-8")) == meta
    splits = {}
    for split in SPLITS:
        path = cache / f"{split}.npz"
        if reuse and path.exists():
            with np.load(path) as stored:
                splits[split] = (stored["images"], stored["labels"])
            log(f"캐시 사용: {path} ({len(splits[split][1])}장)")
            continue
        images, labels = build_split(data, classes, split, seed, workers)
        cache.mkdir(parents=True, exist_ok=True)
        np.savez_compressed(path, images=images, labels=labels)
        splits[split] = (images, labels)
        log(f"변환 완료: {split} {len(labels)}장 -> {path}")
    meta_path.write_text(json.dumps(meta, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return splits, classes
