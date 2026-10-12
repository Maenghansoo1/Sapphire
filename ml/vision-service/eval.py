"""사진-주제 적합도 평가. ml/eval-data/topic-fit/<사람>/*.csv (파일,주제,색,메모) 를 모두 읽는다.

  python eval.py                                  # 모델을 이 프로세스에서 직접 읽어 평가
  python eval.py --url http://localhost:8001      # 떠 있는 비전 서비스 API 로 평가 (지연 시간이 실제와 같음)

사진마다 "맞음" 짝(이름표의 주제·색)과 같은 수의 "안 맞음" 짝(관련 없는 주제·색)을 만든다.
결과: <data>/_results/{pairs.csv, scores.csv, curve_*.csv} (사진 이름 포함, 깃 제외) + EVAL.md (집계만, 깃 포함).
"""
import argparse
import csv
import io
import random
import statistics
import time
from datetime import date
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
DEFAULT_DATA = HERE.parent / "eval-data" / "topic-fit"
COLORS = ("빨강", "주황", "노랑", "초록", "파랑", "보라", "분홍", "흰색", "검정", "갈색", "회색")
COLOR_NEIGHBORS = {
    "빨강": {"주황", "분홍"}, "주황": {"빨강", "노랑", "갈색"}, "노랑": {"주황"}, "초록": set(), "파랑": {"보라"},
    "보라": {"파랑", "분홍"}, "분홍": {"빨강", "보라"}, "흰색": {"회색"}, "검정": {"회색"}, "갈색": {"주황"},
    "회색": {"흰색", "검정"},
}
# "안 맞음" 주제 후보: 서로 뚜렷이 다른 구체적인 주제만. (동물·운동 기구 같은 넓은 말은 실제로 맞을 수 있어 쓰지 않는다)
NEGATIVE_POOL = (
    "컵", "커피", "책", "노트북", "휴대폰", "펜", "밥", "라면", "빵", "과일", "안경", "열쇠", "신발", "가방", "우산", "옷",
    "인형", "침대", "부엌", "하늘", "바다", "산", "노을", "나무", "꽃", "자동차", "자전거", "고양이", "강아지", "의자",
    "케이크", "햄버거", "피아노", "헤드폰", "화장실", "불꽃놀이", "초밥", "시계",
)
# 한 사진에 같이 찍히기 쉬운 주제 묶음. 사진 주제가 묶음에 하나라도 있으면 묶음 전체를 "안 맞음" 후보에서 뺀다.
RELATED = (
    {"하늘", "바다", "산", "노을", "창밖 풍경", "구름", "숲", "나무", "강", "물", "풀", "꽃", "튤립", "건물", "길", "도로"},
    {"커피", "컵", "음료", "음료수", "디저트", "케이크", "빵", "카페", "접시"},
    {"라면", "라멘", "면", "밥", "초밥", "도시락", "샐러드", "채소", "과일", "햄버거", "음식", "접시"},
    {"시계", "손목시계", "스마트워치"},
    {"침대", "방", "내 방", "호텔 방", "화장실", "부엌", "싱크대", "거실", "의자"},
    {"노트북", "책상 위", "마우스", "펜", "문구", "샤프심", "책", "휴대폰", "헤드폰", "컵"},
    {"고양이", "강아지", "동물", "너구리", "사슴", "새", "갈매기", "개구리", "곤충", "사마귀", "거북이", "인형"},
    {"꽃", "튤립", "화분", "나무", "풀", "숲"},
    {"옷", "옷 가게", "신발", "가방", "모자"},
)


def read_labels(root):
    """사람 폴더(_ 로 시작하는 폴더 제외)의 CSV 를 모두 읽어 사진 목록을 만든다."""
    photos, problems = [], []
    for folder in sorted(p for p in Path(root).iterdir() if p.is_dir() and not p.name.startswith("_")):
        for sheet in sorted(folder.glob("*.csv")):
            with sheet.open(encoding="utf-8-sig", newline="") as file:
                for line, row in enumerate(csv.DictReader(file), start=2):
                    name = (row.get("파일") or "").strip()
                    topics = [t.strip() for t in (row.get("주제") or "").split("|") if t.strip()]
                    color = (row.get("색") or "").strip()
                    where = f"{sheet.relative_to(root)}:{line}"
                    if not name:
                        continue
                    if not (folder / name).exists():
                        problems.append(f"{where} 파일 없음: {name}")
                        continue
                    if color and color not in COLORS:
                        problems.append(f"{where} 알 수 없는 색 '{color}' (무시)")
                        color = ""
                    if not topics and not color:
                        problems.append(f"{where} 주제·색 모두 비어 있음: {name}")
                        continue
                    photos.append({"person": folder.name, "file": name, "path": folder / name,
                                   "topics": topics, "color": color, "note": (row.get("메모") or "").strip()})
    return photos, problems


def blocked_topics(topics):
    blocked = set(topics)
    for group in RELATED:
        if group & blocked:
            blocked |= group
    return blocked


def make_pairs(photos, seed=7):
    """사진마다 맞음 짝 n 개 + 안 맞음 짝 n 개. 사진별 시드라 사진을 더해도 기존 짝은 바뀌지 않는다."""
    pairs = []
    for photo in photos:
        rng = random.Random(f"{seed}:{photo['person']}/{photo['file']}")
        key = f"{photo['person']}/{photo['file']}"
        if photo["topics"]:
            candidates = [t for t in NEGATIVE_POOL if t not in blocked_topics(photo["topics"])]
            negatives = rng.sample(candidates, min(len(photo["topics"]), len(candidates)))
            pairs += [{"photo": key, "template": "PHOTO_NOW", "topic": t, "label": 1} for t in photo["topics"]]
            pairs += [{"photo": key, "template": "PHOTO_NOW", "topic": t, "label": 0} for t in negatives]
        if photo["color"]:
            far = [c for c in COLORS if c != photo["color"] and c not in COLOR_NEIGHBORS[photo["color"]]]
            pairs.append({"photo": key, "template": "PHOTO_COLOR", "topic": photo["color"], "label": 1})
            pairs.append({"photo": key, "template": "PHOTO_COLOR", "topic": rng.choice(far), "label": 0})
    return pairs


def roc_auc(scores, labels):
    """Mann-Whitney 방식 (동점은 평균 순위)."""
    scores, labels = np.asarray(scores, dtype=float), np.asarray(labels, dtype=int)
    positives, negatives = labels.sum(), len(labels) - labels.sum()
    if positives == 0 or negatives == 0:
        return None
    order = scores.argsort()
    ranks = np.empty(len(scores))
    sorted_scores = scores[order]
    start = 0
    while start < len(scores):
        end = start
        while end + 1 < len(scores) and sorted_scores[end + 1] == sorted_scores[start]:
            end += 1
        ranks[order[start:end + 1]] = (start + end) / 2 + 1
        start = end + 1
    return float((ranks[labels == 1].sum() - positives * (positives + 1) / 2) / (positives * negatives))


def threshold_curve(scores, labels, steps=1000):
    scores, labels = np.asarray(scores, dtype=float), np.asarray(labels, dtype=int)
    rows = []
    for i in range(steps + 1):
        threshold = i / steps
        predicted = scores >= threshold
        tp = int((predicted & (labels == 1)).sum())
        fp = int((predicted & (labels == 0)).sum())
        fn = int((~predicted & (labels == 1)).sum())
        tn = int((~predicted & (labels == 0)).sum())
        precision = tp / (tp + fp) if tp + fp else None
        recall = tp / (tp + fn) if tp + fn else None
        f1 = 2 * precision * recall / (precision + recall) if precision and recall else 0.0
        rows.append({"threshold": threshold, "tp": tp, "fp": fp, "fn": fn, "tn": tn, "precision": precision,
                     "recall": recall, "f1": f1, "accuracy": (tp + tn) / len(labels)})
    return rows


def pick_threshold(curve, min_precision):
    """정밀도가 min_precision 이상인 가장 낮은 임계값 (배지를 최대한 많이 주되 틀리게는 주지 않기)."""
    for row in curve:
        if row["precision"] is not None and row["precision"] >= min_precision and row["tp"] > 0:
            return row
    return None


class LocalClient:
    def __init__(self):
        import scoring

        self.scoring = scoring
        self.scorer = scoring.TopicFitScorer()
        self._vectors = {}

    def score(self, path, topic, template):
        started = time.perf_counter()
        if path not in self._vectors:
            self._vectors[path] = self.scorer.image_vector(self.scoring.decode_image(Path(path).read_bytes()))
        result = self.scorer.score_vector(self._vectors[path], topic, template)
        return result["fit"], (time.perf_counter() - started) * 1000


class HttpClient:
    def __init__(self, url):
        import httpx

        self.client = httpx.Client(base_url=url, timeout=30)

    def score(self, path, topic, template):
        data = Path(path).read_bytes()
        if len(data) > 10 * 1024 * 1024:  # 앱 업로드 한도와 같게 줄여서 보낸다
            from PIL import Image

            with Image.open(io.BytesIO(data)) as image:
                image.thumbnail((2048, 2048))
                buffer = io.BytesIO()
                image.convert("RGB").save(buffer, "JPEG", quality=90)
                data = buffer.getvalue()
        started = time.perf_counter()
        response = self.client.post("/v1/topic-fit", files={"image": ("photo.jpg", data)},
                                    data={"topic": topic, "template": template})
        response.raise_for_status()
        return response.json()["fit"], (time.perf_counter() - started) * 1000


def write_csv(path, rows):
    if not rows:
        return
    with path.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)


def summarize(name, rows):
    scores = [row["fit"] for row in rows]
    labels = [row["label"] for row in rows]
    curve = threshold_curve(scores, labels)
    best = max(curve, key=lambda row: row["accuracy"])
    return {"name": name, "count": len(rows), "positives": sum(labels), "auc": roc_auc(scores, labels),
            "best": best, "perfect": pick_threshold(curve, 0.95), "good": pick_threshold(curve, 0.85), "curve": curve,
            "rows": rows}


def percent(value):
    return "-" if value is None else f"{value * 100:.1f}%"


def threshold_line(label, row):
    if row is None:
        return f"| {label} | 없음 | - | - | - |"
    return (f"| {label} | {row['threshold']:.3f} | {percent(row['precision'])} | {percent(row['recall'])} | "
            f"TP {row['tp']} · FP {row['fp']} · FN {row['fn']} · TN {row['tn']} |")


def mistakes(rows, threshold, limit=12):
    """임계값에서 틀린 짝을 주제 이름으로만 (사진 이름은 넣지 않는다)."""
    wrong_yes = sorted((r for r in rows if r["label"] == 0 and r["fit"] >= threshold), key=lambda r: -r["fit"])
    wrong_no = sorted((r for r in rows if r["label"] == 1 and r["fit"] < threshold), key=lambda r: r["fit"])
    describe = lambda items: ", ".join(f"{r['topic']} ({r['fit']:.3f})" for r in items[:limit]) or "없음"
    return [f"- 안 맞는데 맞다고 함 {len(wrong_yes)}건: {describe(wrong_yes)}",
            f"- 맞는데 놓침 {len(wrong_no)}건: {describe(wrong_no)}"]


def report(summaries, photos, people, latencies, mode, problems):
    lines = [
        "# 사진-주제 적합도 평가 결과",
        "",
        f"평가일: {date.today().isoformat()} · 모델: `clip-ViT-B-32` + `clip-ViT-B-32-multilingual-v1` · 방식: {mode}",
        f"데이터: 사진 {len(photos)}장 (팀원 {people}명, 직접 찍은 사진) · 짝 {sum(s['count'] for s in summaries.values())}개"
        f" (맞음:안 맞음 = 1:1). 사진·이름표는 저장소에 올리지 않는다(`ml/eval-data/`).",
        "",
        "## 요약",
        "",
        "| 미션 틀 | 짝 수 | ROC-AUC | 최고 정확도 (임계값) |",
        "|---|---|---|---|",
    ]
    for summary in summaries.values():
        best = summary["best"]
        lines.append(f"| {summary['name']} | {summary['count']} | "
                     f"{'-' if summary['auc'] is None else f'{summary['auc']:.3f}'} | "
                     f"{percent(best['accuracy'])} ({best['threshold']:.3f}) |")
    lines += ["", "## 추천 임계값 (백엔드 `hawe.vision.*`)", "",
              "정밀도(배지를 준 사진 중 실제로 맞는 비율)를 먼저 지키고, 그 안에서 가장 낮은 임계값을 고른다.", ""]
    for summary in summaries.values():
        lines += [f"### {summary['name']}", "", "| 기준 | 임계값 | 정밀도 | 재현율 | 혼동행렬 |", "|---|---|---|---|---|",
                  threshold_line("찰떡상 (정밀도 ≥ 95%)", summary["perfect"]),
                  threshold_line("칭찬 한 줄 (정밀도 ≥ 85%)", summary["good"]), ""]
        if summary["good"] is not None:
            lines += [f"칭찬 한 줄 임계값({summary['good']['threshold']:.3f})에서 틀린 경우 (주제와 점수):", ""]
            lines += mistakes(summary["rows"], summary["good"]["threshold"]) + [""]
    if latencies:
        lines += ["## 지연 시간", "",
                  f"한 번 호출 p50 {statistics.median(latencies):.0f} ms · p95 {np.percentile(latencies, 95):.0f} ms"
                  f" ({mode}, CPU)", ""]
    lines += ["## 점수 읽는 법", "",
              "`fit` 은 주제 1개 + 대조 주제(일상 72개 또는 다른 색 10개) 중 이 주제일 확률이다. 아무 관련 없을 때의 기준값은"
              " 일상 약 0.014(1/73), 색 약 0.09(1/11). 그래서 미션 틀마다 임계값이 다르다.", "",
              "## 한계", "",
              "- 평가 사진이 적다. 사진이 늘면 다시 돌려 임계값을 고친다.",
              "- \"안 맞음\" 짝은 관련 없는 주제를 골라 만들어 실제보다 쉬울 수 있다(비슷한 주제끼리의 구분은 덜 측정됨).",
              "- 한 사진에 여러 주제가 있으면 부주제(예: 꽃 사진의 하늘)는 점수가 낮다. 대조 목록의 다른 주제와 확률을 나누기 때문.",
              "- 드문 단어(예: 샤프심, 바셀린)는 다국어 텍스트 모델이 약하다. 미션 주제는 흔한 말로 만드는 것이 좋다(B8).",
              "- 색은 주인공 물건 기준 이름표다. 배경에 다른 색 물건이 크게 있으면 그 색으로도 점수가 오른다.", ""]
    if problems:
        lines += ["## 이름표 경고", ""] + [f"- {problem}" for problem in problems] + [""]
    return "\n".join(lines)


def main(argv=None):
    parser = argparse.ArgumentParser(description="사진-주제 적합도 평가")
    parser.add_argument("--data", default=str(DEFAULT_DATA))
    parser.add_argument("--url", default=None, help="비전 서비스 주소. 없으면 모델을 직접 읽는다")
    parser.add_argument("--seed", type=int, default=7)
    parser.add_argument("--report", default=str(HERE / "EVAL.md"))
    args = parser.parse_args(argv)

    root = Path(args.data)
    photos, problems = read_labels(root)
    for problem in problems:
        print("경고:", problem)
    if not photos:
        raise SystemExit(f"{root} 에 이름표가 있는 사진이 없습니다.")
    pairs = make_pairs(photos, args.seed)
    results = root / "_results"
    results.mkdir(exist_ok=True)
    write_csv(results / "pairs.csv", pairs)

    client = HttpClient(args.url) if args.url else LocalClient()
    mode = f"API ({args.url})" if args.url else "모델 직접 호출"
    paths = {f"{p['person']}/{p['file']}": p["path"] for p in photos}
    rows, latencies = [], []
    for index, pair in enumerate(pairs, start=1):
        fit, elapsed = client.score(paths[pair["photo"]], pair["topic"], pair["template"])
        latencies.append(elapsed)
        rows.append({**pair, "fit": round(fit, 6)})
        if index % 25 == 0 or index == len(pairs):
            print(f"{index}/{len(pairs)}")
    write_csv(results / "scores.csv", rows)

    summaries = {}
    for template in ("PHOTO_NOW", "PHOTO_COLOR"):
        chosen = [row for row in rows if row["template"] == template]
        if chosen:
            summaries[template] = summarize(template, chosen)
            write_csv(results / f"curve_{template}.csv", summaries[template]["curve"])
    people = len({p["person"] for p in photos})
    Path(args.report).write_text(report(summaries, photos, people, latencies, mode, problems), encoding="utf-8")
    for summary in summaries.values():
        perfect = summary["perfect"]
        print(f"{summary['name']}: 짝 {summary['count']}, AUC {summary['auc']:.3f}, "
              f"최고 정확도 {percent(summary['best']['accuracy'])} @ {summary['best']['threshold']:.3f}, "
              f"찰떡상 임계값 {'없음' if perfect is None else f'{perfect['threshold']:.3f}'}")
    print(f"보고서: {args.report}, 자세한 결과: {results}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
