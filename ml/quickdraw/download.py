"""Quick, Draw! simplified 데이터를 범주별로 받아 train/val/test 로 나눠 저장한다.

표준 라이브러리만 쓴다. 예:
  python download.py                                   # 50개 범주 전부, 범주당 30,000장 (Colab)
  python download.py --only "cat,face" --per-class 500 --max-lines 1500 --out data/smoke   # 작은 테스트
"""
import argparse
import http.client
import json
import random
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
BASE_URL = "https://storage.googleapis.com/quickdraw_dataset/full/simplified/"
LICENSE = "Quick, Draw! Dataset by Google, CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/)"
SPLITS = ("train", "val", "test")


def load_categories(path):
    items = json.loads(Path(path).read_text(encoding="utf-8"))
    if [item["id"] for item in items] != list(range(len(items))):
        raise ValueError("categories.json 의 id 는 0부터 빠짐없이 순서대로여야 합니다")
    names = [item["quickdraw"] for item in items]
    if len(set(names)) != len(names):
        raise ValueError("categories.json 에 중복된 범주가 있습니다")
    return items


def source_url(name):
    return BASE_URL + urllib.parse.quote(name) + ".ndjson"


def file_stem(name):
    return name.replace(" ", "_")


def parse_line(line):
    """인식된(recognized) 그림만 {key_id, drawing(JSON 문자열)} 으로 돌려준다. 깨진 줄은 None."""
    try:
        row = json.loads(line)
    except json.JSONDecodeError:
        return None
    if not isinstance(row, dict) or row.get("recognized") is not True:
        return None
    drawing = row.get("drawing")
    if not isinstance(drawing, list) or not drawing:
        return None
    return {"key_id": str(row.get("key_id") or ""), "drawing": json.dumps(drawing, separators=(",", ":"))}


def dedupe(samples):
    """같은 key_id 또는 획이 완전히 같은 그림은 하나만 남긴다."""
    seen_keys, seen_drawings, result = set(), set(), []
    for sample in samples:
        key = sample["key_id"]
        if (key and key in seen_keys) or sample["drawing"] in seen_drawings:
            continue
        if key:
            seen_keys.add(key)
        seen_drawings.add(sample["drawing"])
        result.append(sample)
    return result


def select_and_split(samples, per_class, seed):
    """시드로 섞어 per_class 개를 고르고 8:1:1 로 나눈다. 받은 순서와 무관하게 같은 시드면 같은 결과."""
    ordered = sorted(samples, key=lambda sample: (sample["key_id"], sample["drawing"]))
    random.Random(seed).shuffle(ordered)
    chosen = ordered[:per_class]
    n_train = int(len(chosen) * 0.8)
    n_val = int(len(chosen) * 0.1)
    return {
        "train": chosen[:n_train],
        "val": chosen[n_train:n_train + n_val],
        "test": chosen[n_train + n_val:],
    }


def fetch_samples(url, max_lines=None, retries=3):
    """파일을 받으며 한 줄씩 거른다. max_lines 를 주면 앞에서 그만큼만 읽고 끊는다."""
    for attempt in range(1, retries + 1):
        try:
            lines_read, samples = 0, []
            with urllib.request.urlopen(url, timeout=60) as response:
                for raw in response:
                    lines_read += 1
                    sample = parse_line(raw.decode("utf-8"))
                    if sample is not None:
                        samples.append(sample)
                    if max_lines and lines_read >= max_lines:
                        break
            return lines_read, samples
        except (urllib.error.URLError, http.client.IncompleteRead, ConnectionError, TimeoutError) as error:
            if attempt == retries:
                raise
            print(f"  다시 시도 {attempt}/{retries - 1}: {error}")
            time.sleep(2 * attempt)


def write_split(path, samples):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as file:
        for sample in samples:
            file.write('{"key_id":%s,"drawing":%s}\n' % (json.dumps(sample["key_id"]), sample["drawing"]))


def pick_categories(categories, only, limit):
    if only:
        wanted = [name.strip() for name in only.split(",") if name.strip()]
        known = {item["quickdraw"] for item in categories}
        unknown = [name for name in wanted if name not in known]
        if unknown:
            raise SystemExit(f"categories.json 에 없는 범주: {', '.join(unknown)}")
        return [item for item in categories if item["quickdraw"] in wanted]
    return categories[:limit] if limit else categories


def main(argv=None):
    parser = argparse.ArgumentParser(description="Quick, Draw! simplified 데이터 받기")
    parser.add_argument("--categories", default=str(HERE / "categories.json"))
    parser.add_argument("--out", default=str(HERE / "data"))
    parser.add_argument("--per-class", type=int, default=30000, help="범주당 고를 그림 수 (8:1:1 로 나눔)")
    parser.add_argument("--max-lines", type=int, default=None, help="파일 앞에서 이만큼만 읽음 (작은 테스트용)")
    parser.add_argument("--only", default=None, help='받을 범주만 쉼표로 (예: "cat,hot air balloon")')
    parser.add_argument("--limit-classes", type=int, default=None, help="앞에서 N개 범주만")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--force", action="store_true", help="같은 설정으로 받은 범주도 다시 받기")
    args = parser.parse_args(argv)

    categories = load_categories(args.categories)
    targets = pick_categories(categories, args.only, args.limit_classes)
    out = Path(args.out)
    manifest_path = out / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8")) if manifest_path.exists() else {}
    entries = manifest.get("categories", {})
    settings = {"per_class": args.per_class, "max_lines": args.max_lines, "seed": args.seed}

    for index, item in enumerate(targets, start=1):
        name, stem = item["quickdraw"], file_stem(item["quickdraw"])
        previous = entries.get(name)
        files_exist = all((out / split / f"{stem}.ndjson").exists() for split in SPLITS)
        if not args.force and previous and previous.get("settings") == settings and files_exist:
            print(f"[{index}/{len(targets)}] {name}: 이미 받음, 건너뜀")
            continue

        started = time.time()
        url = source_url(name)
        lines_read, samples = fetch_samples(url, args.max_lines)
        unique = dedupe(samples)
        splits = select_and_split(unique, args.per_class, f"{args.seed}:{name}")
        for split in SPLITS:
            write_split(out / split / f"{stem}.ndjson", splits[split])

        counts = {split: len(splits[split]) for split in SPLITS}
        entries[name] = {"id": item["id"], "url": url, "settings": settings, "lines_read": lines_read,
                         "recognized": len(samples), "unique": len(unique), **counts}
        manifest = {"source": BASE_URL, "license": LICENSE,
                    "updated_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
                    "categories": entries}
        out.mkdir(parents=True, exist_ok=True)
        manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

        total = sum(counts.values())
        note = "" if total >= args.per_class else f"  (요청 {args.per_class}장보다 적음)"
        print(f"[{index}/{len(targets)}] {name}: 읽은 줄 {lines_read}, 인식됨 {len(samples)}, "
              f"중복 제거 후 {len(unique)} -> train {counts['train']} / val {counts['val']} / test {counts['test']}"
              f"  {time.time() - started:.1f}s{note}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
