"""그림 모델 평가 (M1 6단계) — 앱에서 그린 그림을 실제로 얼마나 맞히는지 잰다.

  python evaluate.py              # export/quickdraw_cnn.tflite + 팀원 그림 + 테스트셋(data/cache/test.npz, 있으면)
  python evaluate.py --smoke      # 연습 모델(runs/smoke/export) 로 흐름 확인 → runs/smoke/EVAL.md

1. 팀원 그림(collect.py 로 모은 ml/eval-data/quickdraw/<사람>/*.png): 앱과 같은 전처리(PNG → 잉크 규칙 → 28×28) 후
   top-1·top-3, 사람별·범주별 정확도, 헷갈린 쌍.
2. Quick, Draw! 테스트셋(있으면): 범주별 재현율·정밀도, 헷갈린 쌍, 보정(ECE). 1과의 차이 = 도메인 격차.
3. "잘 모르겠어요" 기준: 가장 높은 확률이 기준 미만이면 답하지 않는다. 맞힌 답 +1, 틀린 답 -1(범주 밖 그림에 답한 것
   포함), "잘 모르겠어요" 0 으로 셌을 때 점수가 가장 높은 기준을 고른다(동점이면 낮은 기준).
결과: EVAL.md(집계만, 깃에 올림) + ml/eval-data/quickdraw/_results/(그림별 결과·틀린 그림 모음, 깃 제외).
"""
import argparse
import csv
import json
import time
from collections import Counter
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from collect import FILE, OTHER
from download import file_stem
from export import accuracy, read_lines, tflite_predict
from rasterize import mask_from_image, preprocess

HERE = Path(__file__).resolve().parent
DEFAULT_DRAWINGS = HERE.parent / "eval-data" / "quickdraw"
THRESHOLDS = tuple(round(t, 2) for t in np.arange(0.05, 0.951, 0.05))
TABLE_THRESHOLDS = (0.2, 0.3, 0.35, 0.4, 0.5, 0.6, 0.7)
GAP_WARNING = 0.15  # 계획: 격차가 15%p 이상이면 전처리·증강을 고쳐 다시 학습


def load_drawings(folder, labels_en):
    """{person, file, label(범주 번호, 범주 밖은 -1), input(28×28)} 목록과 건너뛴 수."""
    index = {file_stem(name): i for i, name in enumerate(labels_en)}
    drawings, skipped = [], Counter()
    folder = Path(folder)
    people = sorted(p for p in folder.iterdir() if p.is_dir() and not p.name.startswith("_")) if folder.exists() else []
    for person in people:
        for path in sorted(person.glob("*.png")):
            match = FILE.match(path.name)
            if not match:
                continue
            stem = match["stem"]
            if stem != OTHER and stem not in index:
                skipped["모델에 없는 범주"] += 1
                continue
            image = preprocess(mask_from_image(path))
            if image is None:
                skipped["너무 비어 있음"] += 1
                continue
            drawings.append({"person": person.name, "file": path.name,
                             "label": -1 if stem == OTHER else index[stem], "input": image})
    return drawings, skipped


def confused_pairs(probabilities, labels, top=10):
    """(정답, 모델 답, 횟수, 정답 범주 중 비율) — 많이 틀린 순."""
    predicted = probabilities.argmax(axis=1)
    totals = Counter(labels.tolist())
    pairs = Counter((int(t), int(p)) for t, p in zip(labels, predicted) if t != p)
    return [{"true": t, "predicted": p, "count": c, "rate": c / totals[t]} for (t, p), c in pairs.most_common(top)]


def precision_per_class(probabilities, labels, num_classes):
    predicted = probabilities.argmax(axis=1)
    result = np.full(num_classes, np.nan)
    for c in range(num_classes):
        chosen = predicted == c
        if chosen.any():
            result[c] = (labels[chosen] == c).mean()
    return result


def calibration_error(probabilities, labels, bins=15):
    """ECE: 확률 구간마다 (말한 확신 - 실제 정답률) 의 차이를 그림 수로 가중 평균. 0 이면 확신이 정확하다."""
    confidence = probabilities.max(axis=1)
    right = probabilities.argmax(axis=1) == labels
    edges = np.linspace(0, 1, bins + 1)
    total = 0.0
    for low, high in zip(edges[:-1], edges[1:]):
        inside = (confidence > low) & (confidence <= high)
        if inside.any():
            total += inside.mean() * abs(right[inside].mean() - confidence[inside].mean())
    return float(total)


def unsure_curve(probabilities, labels, thresholds=THRESHOLDS):
    """기준마다: 범주 안 그림 중 답한 비율·답한 것의 정확도, 범주 밖 그림(label -1) 중 답한 비율, 전체 정밀도,
    점수((맞힌 답 - 틀린 답) / 그림 수)."""
    best = probabilities.max(axis=1)
    inside = labels >= 0
    right = (probabilities.argmax(axis=1) == labels) & inside
    rows = []
    for threshold in thresholds:
        answered = best >= threshold
        answered_inside, answered_outside = (answered & inside).sum(), (answered & ~inside).sum()
        rows.append({
            "threshold": float(threshold),
            "answered": float(answered_inside / inside.sum()) if inside.any() else None,
            "accuracy": float(right[answered].sum() / answered_inside) if answered_inside else None,
            "other_answered": float(answered_outside / (~inside).sum()) if (~inside).any() else None,
            "precision": float(right[answered].sum() / answered.sum()) if answered.any() else None,
            "score": float((2 * right[answered].sum() - answered.sum()) / len(labels)),
        })
    return rows


def pick_unsure_threshold(curve):
    """점수가 가장 높은 기준 (동점이면 낮은 기준). 곡선이 비었으면 None."""
    return max(curve, key=lambda row: (round(row["score"], 9), -row["threshold"])) if curve else None


def mistakes_sheet(drawings, probabilities, labels_en, path, limit=60):
    """틀린 그림의 28×28 입력을 3배로 키워 '정답 > 모델 답' 과 함께 모은 그림 (전처리 확인용)."""
    wrong = [(d, p) for d, p in zip(drawings, probabilities) if d["label"] >= 0 and p.argmax() != d["label"]][:limit]
    if not wrong:
        return False
    tile, caption, columns = 84, 24, 6
    rows = -(-len(wrong) // columns)
    sheet = Image.new("L", (columns * (tile + 8), rows * (tile + caption + 8)), 255)
    draw = ImageDraw.Draw(sheet)
    for i, (drawing, probability) in enumerate(wrong):
        x, y = (i % columns) * (tile + 8), (i // columns) * (tile + caption + 8)
        image = Image.fromarray(np.uint8(255 - drawing["input"] * 255)).resize((tile, tile), Image.NEAREST)
        sheet.paste(image, (x, y))
        draw.rectangle((x, y, x + tile - 1, y + tile - 1), outline=180)
        draw.text((x, y + tile + 1), labels_en[drawing["label"]][:14], fill=0)
        draw.text((x, y + tile + 11), "> " + labels_en[int(probability.argmax())][:12], fill=90)
    sheet.save(path)
    return True


def percent(value):
    return "-" if value is None or (isinstance(value, float) and np.isnan(value)) else f"{value * 100:.1f}%"


def write_report(path, report, labels_en, labels_ko):
    app, test = report["app"], report["test"]

    def name(i):
        return f"{labels_ko[i]}({labels_en[i]})"

    lines = [
        "# 그림 모델 평가 결과",
        "",
        f"평가일: {report['created']} · 모델: `{report['model']}` (범주 {len(labels_en)}개)",
        "그림·사람별 파일은 저장소에 올리지 않는다(`ml/eval-data/`). 이 문서는 집계만 적는다.",
        "",
        "## 요약",
        "",
        "| 데이터 | 그림 수 | top-1 | top-3 | ECE |",
        "|---|---|---|---|---|",
    ]
    if test:
        lines.append(f"| Quick, Draw! 테스트셋 | {test['count']:,} | {percent(test['top1'])} | {percent(test['top3'])} | "
                     f"{test['ece']:.3f} |")
    if app["count"]:
        lines.append(f"| 팀원이 앱 그림판으로 그린 그림 | {app['count']} | {percent(app['top1'])} | {percent(app['top3'])} | "
                     f"{app['ece']:.3f} |")
    else:
        lines.append("| 팀원 그림 | 0 | - | - | - |")
    lines.append("")
    if test and app["count"]:
        gap = test["top1"] - app["top1"]
        verdict = (f"**{GAP_WARNING * 100:.0f}%p 이상 — 전처리·학습 그림 다양화(증강)를 고쳐 다시 학습한다.**" if gap >= GAP_WARNING
                   else f"{GAP_WARNING * 100:.0f}%p 미만 — 지금 전처리·증강으로 충분하다.")
        lines += [f"도메인 격차(테스트셋 top-1 − 앱 그림 top-1): **{gap * 100:+.1f}%p**. {verdict}", ""]
    skipped = report["skipped"]
    if skipped or app["other_count"]:
        notes = [f"범주 밖 그림 {app['other_count']}장(아래 \"잘 모르겠어요\" 에만 씀)"] + [f"{k} {v}장" for k, v in skipped.items()]
        lines += ["정확도에서 뺀 그림: " + ", ".join(notes) + ".", ""]

    if app["count"]:
        lines += ["## 사람별 (앱 그림)", "", "| 그린 사람 | 그림 수 | top-1 | top-3 |", "|---|---|---|---|"]
        lines += [f"| {p['person']} | {p['count']} | {percent(p['top1'])} | {percent(p['top3'])} |" for p in app["people"]]
        lines.append("")

    lines += ["## \"잘 모르겠어요\" 기준", ""]
    unsure = report["unsure"]
    if unsure["curve"]:
        lines += [
            "가장 높은 확률이 기준보다 낮으면 앱은 답 대신 \"잘 모르겠어요\" 를 보여 준다. 앱 그림 기준:",
            "",
            "| 기준 | 범주 안 그림 중 답함 | 답한 것의 정확도 | 범주 밖 그림 중 답함(틀린 답) | 답한 것 중 맞음 | 점수 |",
            "|---|---|---|---|---|---|",
        ]
        picked = unsure["picked"]
        shown = set(TABLE_THRESHOLDS) | {round(picked["threshold"], 2)}
        lines += [f"| {row['threshold']:.2f}{' (추천)' if row is picked else ''} | {percent(row['answered'])} | "
                  f"{percent(row['accuracy'])} | {percent(row['other_answered'])} | {percent(row['precision'])} | "
                  f"{row['score']:.3f} |"
                  for row in unsure["curve"] if round(row["threshold"], 2) in shown]
        lines += ["", "점수 = (맞힌 답 − 틀린 답) ÷ 그림 수. \"잘 모르겠어요\" 는 0 으로, 범주 밖 그림에 답한 것은 틀린 답으로 센다.",
                  f"추천 기준: **{picked['threshold']:.2f}** — 범주 안 그림의 {percent(picked['answered'])} 에 답하고, "
                  f"답한 것 중 {percent(picked['precision'])} 가 맞는다. 범주 밖 그림 중 {percent(picked['other_answered'])} 에 답한다.",
                  "앱(A8)과 모델 설명서에 이 값을 적는다. 그림이 늘면 다시 잰다.", ""]
    else:
        lines += ["팀원 그림이 없어 정하지 못했다(임시 0.35).", ""]

    if app["count"]:
        lines += ["## 헷갈린 쌍 (앱 그림)", ""]
        lines += [f"- {name(p['true'])} → {name(p['predicted'])}: {p['count']}장 ({percent(p['rate'])})" for p in app["pairs"]] or ["- 없음"]
        lines.append("")
    if test:
        lines += ["## 헷갈린 쌍 (테스트셋 상위 10)", ""]
        lines += [f"- {name(p['true'])} → {name(p['predicted'])}: {p['count']}장 ({percent(p['rate'])})" for p in test["pairs"]]
        lines.append("")

    lines += ["## 범주별", "", "앱 그림 정확도가 낮은 순. 범주마다 그림이 몇 장뿐이라 앱 그림 수치는 흔들림이 크다.", "",
              "| 범주 | 테스트셋 재현율 | 테스트셋 정밀도 | 앱 그림 수 | 앱 그림 top-1 |", "|---|---|---|---|---|"]
    rows = []
    for i in range(len(labels_en)):
        count = app["per_class_count"][i]
        rows.append((app["per_class_top1"][i] if count else 2.0, test["per_class_recall"][i] if test else 0.0, i))
    for _, _, i in sorted(rows):
        recall = test["per_class_recall"][i] if test else None
        precision = test["per_class_precision"][i] if test else None
        count = app["per_class_count"][i]
        lines.append(f"| {name(i)} | {percent(recall)} | {percent(precision)} | {count} | "
                     f"{percent(app['per_class_top1'][i]) if count else '-'} |")
    lines += ["", "## 한계", "",
              "- 팀원 몇 명이 그린 그림이라 실제 사용자 그림과 다를 수 있다. 범주마다 그림이 몇 장뿐이다.",
              "- 그림판은 앱과 같은 방식(굵기·색·지우개·2배 PNG)으로 만들었지만 실제 앱 화면이 아니라 휴대폰 브라우저에서 그렸다.",
              "- 범주 밖 그림은 30개 단어로만 쟀다. 낙서·글자 등 다른 입력은 재지 않았다.", ""]
    Path(path).write_text("\n".join(lines), encoding="utf-8")


def main(argv=None):
    parser = argparse.ArgumentParser(description="그림 모델 평가 (앱 그림 + 테스트셋)")
    parser.add_argument("--model", default=str(HERE / "export" / "quickdraw_cnn.tflite"))
    parser.add_argument("--drawings", default=str(DEFAULT_DRAWINGS))
    parser.add_argument("--data", default=str(HERE / "data"), help="<data>/cache/test.npz 가 있으면 테스트셋도 잰다")
    parser.add_argument("--out", default=None, help="보고서 경로 (기본: EVAL.md, --smoke 는 runs/smoke/EVAL.md)")
    parser.add_argument("--results", default=None, help="그림별 결과 폴더 (기본: <drawings>/_results)")
    parser.add_argument("--smoke", action="store_true", help="연습 모델과 data/smoke 로")
    args = parser.parse_args(argv)
    if args.smoke:
        args.model = str(HERE / "runs" / "smoke" / "export" / "quickdraw_cnn.tflite")
        args.data = str(HERE / "data" / "smoke")
        args.out = args.out or str(HERE / "runs" / "smoke" / "EVAL.md")
    model = Path(args.model)
    out = Path(args.out) if args.out else HERE / "EVAL.md"
    results = Path(args.results) if args.results else Path(args.drawings) / "_results"
    if not model.exists():
        raise SystemExit(f"{model} 이 없습니다. export.py 를 먼저 실행하세요.")
    blob = model.read_bytes()
    labels_en, labels_ko = read_lines(model.parent / "labels_en.txt"), read_lines(model.parent / "labels_ko.txt")
    num_classes = len(labels_en)

    test = None
    cache = Path(args.data) / "cache"
    if (cache / "test.npz").exists():
        meta = json.loads((cache / "meta.json").read_text(encoding="utf-8"))
        if meta["classes"] != labels_en:
            raise SystemExit(f"{cache} 의 범주 순서가 모델 라벨과 다릅니다.")
        with np.load(cache / "test.npz") as stored:
            test_images, test_labels = stored["images"], stored["labels"]
        probabilities = tflite_predict(blob, test_images.astype(np.float32)[..., None] / 255.0)
        top1, top3, recall = accuracy(probabilities, test_labels, num_classes)
        test = {"count": int(len(test_labels)), "top1": top1, "top3": top3, "ece": calibration_error(probabilities, test_labels),
                "per_class_recall": recall.tolist(),
                "per_class_precision": precision_per_class(probabilities, test_labels, num_classes).tolist(),
                "pairs": confused_pairs(probabilities, test_labels)}
        print(f"테스트셋 {test['count']:,}장: top-1 {top1:.4f}, top-3 {top3:.4f}")

    drawings, skipped = load_drawings(args.drawings, labels_en)
    labels = np.array([d["label"] for d in drawings], dtype=np.int64)
    probabilities = (tflite_predict(blob, np.stack([d["input"] for d in drawings])[..., None]) if drawings
                     else np.zeros((0, num_classes), dtype=np.float32))
    inside = labels >= 0
    app = {"count": int(inside.sum()), "other_count": int((~inside).sum()), "people": [],
           "per_class_count": np.bincount(labels[inside], minlength=num_classes).tolist(),
           "per_class_top1": [None] * num_classes}
    if inside.any():
        top1, top3, per_class = accuracy(probabilities[inside], labels[inside], num_classes)
        app.update(top1=top1, top3=top3, ece=calibration_error(probabilities[inside], labels[inside]),
                   per_class_top1=[None if np.isnan(v) else float(v) for v in per_class],
                   pairs=confused_pairs(probabilities[inside], labels[inside]))
        for person in sorted({d["person"] for d in drawings}):
            rows = inside & np.array([d["person"] == person for d in drawings])
            if rows.any():
                p1, p3, _ = accuracy(probabilities[rows], labels[rows], num_classes)
                app["people"].append({"person": person, "count": int(rows.sum()), "top1": p1, "top3": p3})
        print(f"앱 그림 {app['count']}장: top-1 {top1:.4f}, top-3 {top3:.4f} (범주 밖 {app['other_count']}장)")
    else:
        print(f"{args.drawings} 에 평가할 그림이 없습니다. collect.py 로 먼저 모으세요.")
    curve = unsure_curve(probabilities, labels) if inside.any() else []
    report = {"created": time.strftime("%Y-%m-%d"), "model": model.name, "skipped": dict(skipped), "test": test, "app": app,
              "unsure": {"curve": curve, "picked": pick_unsure_threshold(curve)}}
    write_report(out, report, labels_en, labels_ko)

    if drawings:
        results.mkdir(parents=True, exist_ok=True)
        with (results / "predictions.csv").open("w", encoding="utf-8-sig", newline="") as file:
            writer = csv.writer(file)
            writer.writerow(["사람", "파일", "정답", "1등", "확률1", "2등", "확률2", "3등", "확률3", "맞음"])
            for drawing, probability in zip(drawings, probabilities):
                order = np.argsort(-probability)[:3]
                truth = labels_en[drawing["label"]] if drawing["label"] >= 0 else OTHER
                writer.writerow([drawing["person"], drawing["file"], truth]
                                + [v for i in order for v in (labels_en[i], f"{probability[i]:.4f}")]
                                + [int(order[0] == drawing["label"])])
        mistakes_sheet(drawings, probabilities, labels_en, results / "mistakes.png")
    print(f"보고서: {out}" + (f" · 그림별 결과: {results}" if drawings else ""))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
