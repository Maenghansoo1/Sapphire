"""학습한 모델 → 앱용 TFLite. 크기 줄이기 3가지(float32·float16·int8)를 테스트셋으로 비교해 하나를 고른다.

  python export.py --smoke                        # 이 PC: runs/smoke 로 연습 → runs/smoke/export/
  python export.py --run runs/full --data data    # 본 모델 → export/ (앱에 넣을 파일)

고르는 규칙: Keras 원본보다 top-1 이 1%p 넘게 떨어지지 않는 후보 중 가장 작은 것.
결과(--out): quickdraw_cnn.tflite, labels_ko.txt, labels_en.txt, golden_outputs.json, export.json, model_card.md.
후보 3개는 <run>/tflite/ 에 남는다(깃 제외). 세 후보 모두 입력·출력이 float32 라서 앱 코드는 어느 것을 골라도 같다.
"""
import argparse
import contextlib
import csv
import io
import json
import os
import platform
import shutil
import time
import warnings
from pathlib import Path

os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "2")

import numpy as np

HERE = Path(__file__).resolve().parent
VARIANTS = ("float32", "float16", "int8")
MAX_DROP = 0.01
CONFIDENCE_LEVELS = (0.2, 0.35, 0.5)
UNSURE_THRESHOLD = 0.35  # 임시. 6단계(앱으로 그린 그림)에서 다시 정한다
TARGETS = {"top1": 0.75, "top3": 0.90, "size_bytes": 1024 * 1024, "latency_ms": 30.0}


def representative_indices(labels, count=500, seed=42):
    """int8 보정에 쓸 그림 번호. 범주마다 같은 수(올림)를 시드로 고른다."""
    rng = np.random.default_rng(seed)
    classes = np.unique(labels)
    per_class = -(-count // len(classes))
    picked = []
    for label in classes:
        rows = np.flatnonzero(labels == label)
        picked.append(rng.choice(rows, size=min(per_class, len(rows)), replace=False))
    return np.sort(np.concatenate(picked))


def to_input(images):
    """uint8 N×28×28 → 모델 입력 float32 N×28×28×1 (0~1)."""
    return images.astype(np.float32)[..., None] / 255.0


def convert(model, variant, representative=None):
    import tensorflow as tf

    with contextlib.redirect_stdout(io.StringIO()):  # Keras 가 저장 과정의 텐서 목록을 길게 출력한다
        converter = tf.lite.TFLiteConverter.from_keras_model(model)
    if variant == "float16":
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.float16]
    elif variant == "int8":
        # 안쪽 계산은 모두 int8, 입력·출력만 float32 로 둔다
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.representative_dataset = lambda: ([image[None]] for image in representative)
        converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    elif variant != "float32":
        raise ValueError(f"알 수 없는 후보: {variant}")
    with contextlib.redirect_stdout(io.StringIO()), warnings.catch_warnings():
        warnings.simplefilter("ignore")  # int8: "입력 통계가 없음" 경고 — 입력이 float32 라 해당 없음
        return converter.convert()


def load_interpreter(blob, batch=1, threads=2):
    import tensorflow as tf

    with warnings.catch_warnings():
        warnings.simplefilter("ignore")  # tf.lite.Interpreter 지원 종료 예고 (TF 2.21 에서는 동작)
        interpreter = tf.lite.Interpreter(model_content=blob, num_threads=threads)
    if batch != 1:
        interpreter.resize_tensor_input(interpreter.get_input_details()[0]["index"], [batch, 28, 28, 1])
    interpreter.allocate_tensors()
    return interpreter


def tflite_predict(blob, inputs, batch=500):
    batch = min(batch, len(inputs))
    interpreter = load_interpreter(blob, batch)
    source, target = interpreter.get_input_details()[0]["index"], interpreter.get_output_details()[0]["index"]
    outputs = []
    for start in range(0, len(inputs), batch):
        chunk = inputs[start:start + batch]
        if len(chunk) < batch:  # 묶음 크기를 고정했으니 마지막 묶음은 0 으로 채운다
            chunk = np.concatenate([chunk, np.zeros((batch - len(chunk),) + chunk.shape[1:], chunk.dtype)])
        interpreter.set_tensor(source, chunk)
        interpreter.invoke()
        outputs.append(interpreter.get_tensor(target)[:len(inputs) - start].copy())
    return np.concatenate(outputs)


def latency(blob, sample, runs=300, warmup=30, threads=2):
    """한 장씩 부를 때 걸린 시간(ms)의 p50·p95. 이 컴퓨터 CPU 기준 참고값이다."""
    interpreter = load_interpreter(blob, 1, threads)
    source, target = interpreter.get_input_details()[0]["index"], interpreter.get_output_details()[0]["index"]
    times = []
    for index in range(warmup + runs):
        started = time.perf_counter()
        interpreter.set_tensor(source, sample)
        interpreter.invoke()
        interpreter.get_tensor(target)
        if index >= warmup:
            times.append((time.perf_counter() - started) * 1000)
    return float(np.percentile(times, 50)), float(np.percentile(times, 95))


def accuracy(probabilities, labels, num_classes):
    """(top-1, top-3, 범주별 top-1 배열 — 그림이 없는 범주는 nan)."""
    top3 = np.argsort(-probabilities, axis=1)[:, :3]
    hit1 = top3[:, 0] == labels
    hit3 = (top3 == labels[:, None]).any(axis=1)
    per_class = np.array([hit1[labels == c].mean() if (labels == c).any() else np.nan for c in range(num_classes)])
    return float(hit1.mean()), float(hit3.mean()), per_class


def confidence_table(probabilities, labels, levels=CONFIDENCE_LEVELS):
    """가장 높은 확률이 기준 이상일 때만 답한다면: 답하는 비율과 답한 것의 정확도."""
    best = probabilities.max(axis=1)
    right = probabilities.argmax(axis=1) == labels
    rows = []
    for level in levels:
        answered = best >= level
        rows.append({"threshold": level, "answered": float(answered.mean()),
                     "accuracy": float(right[answered].mean()) if answered.any() else None})
    return rows


def choose(results, max_drop=MAX_DROP):
    """Keras 원본보다 top-1 이 max_drop 넘게 떨어지지 않는 후보 중 가장 작은 것."""
    allowed = [result for result in results if result["top1_drop"] <= max_drop]
    return min(allowed, key=lambda result: result["size_bytes"])["variant"] if allowed else "float32"


def golden_outputs(blob, golden_dir, labels_en, labels_ko):
    """golden/*.json 의 expected(28×28)를 넣었을 때의 출력. 앱이 모델을 제대로 부르는지 확인하는 데 쓴다."""
    items = []
    for path in sorted(Path(golden_dir).glob("*.json")):
        case = json.loads(path.read_text(encoding="utf-8"))
        if case["expected"] is None:
            continue
        probabilities = tflite_predict(blob, np.asarray(case["expected"], dtype=np.float32)[None, ..., None])[0]
        first, second = np.sort(probabilities)[::-1][:2]
        items.append({
            "name": case["name"],
            "margin": round(float(first - second), 6),
            "top3": [{"index": int(i), "en": labels_en[i], "ko": labels_ko[i], "probability": round(float(probabilities[i]), 6)}
                     for i in np.argsort(-probabilities)[:3]],
            "probabilities": [round(float(p), 6) for p in probabilities],
        })
    return items


def tensor_info(detail):
    return {"name": detail["name"], "shape": [int(v) for v in detail["shape"]], "dtype": np.dtype(detail["dtype"]).name}


def read_lines(path):
    return [line for line in Path(path).read_text(encoding="utf-8").splitlines() if line]


def percent(value):
    return "-" if value is None else f"{value * 100:.1f}%"


def size_text(size):
    return f"{size / 1024:.0f} KB"


def points(change, digits=2):
    return f"{change * 100 + 0.0:+.{digits}f}%p"  # + 0.0: -0.0 을 +0.00 으로


def mark(ok):
    return "✅" if ok else "❌"


def model_card(report, labels_en, labels_ko):
    chosen = next(v for v in report["variants"] if v["variant"] == report["chosen"])
    training, config, io, env = report["training"], report["training"]["config"], report["io"], report["environment"]
    per_class = training["train_count"] // len(labels_en), training["val_count"] // len(labels_en), \
        training["test_count"] // len(labels_en)
    lines = [
        "# 그림 인식 모델 설명서 — `quickdraw_cnn.tflite`",
        "",
        f"만든 날 {report['created']} · 학습 결과 `runs/{report['run']}` · 전처리 명세 버전 1(`../PREPROCESS_SPEC.md`).",
        "이 파일은 `export.py` 가 만든다. 손으로 고치지 말고 다시 만든다.",
        "",
        "## 무엇을 하는 모델인가",
        "",
        f"앱에서 그린 그림을 28×28 로 바꿔 넣으면 범주 {len(labels_en)}개 중 무엇인지 확률로 답한다. "
        f"직접 학습한 소형 CNN(파라미터 {training['parameters']:,}개)이고, 사전 학습된 모델은 쓰지 않았다. "
        "휴대폰 안에서만 실행한다(그림을 서버로 보내지 않는다).",
        "",
        "## 입력·출력 (앱과의 약속)",
        "",
        "| | 텐서 이름 | 모양 | 형식 | 뜻 |",
        "|---|---|---|---|---|",
        f"| 입력 | `{io['input']['name']}` | `{io['input']['shape']}` | {io['input']['dtype']} | "
        "0.0(빈 곳)~1.0(잉크). `PREPROCESS_SPEC.md` 대로 만든다 |",
        f"| 출력 | `{io['output']['name']}` | `{io['output']['shape']}` | {io['output']['dtype']} | "
        "범주별 확률(합 1). i 번째 값 = `labels_ko.txt`·`labels_en.txt` 의 i 번째 줄 |",
        "",
        f"크기 줄이기는 **{report['chosen']}** 를 골랐다. 어느 후보든 입력·출력은 float32 라서, 다시 골라도 앱 코드는 바뀌지 않는다.",
        "앱은 이름 대신 0번 입력·0번 출력으로 부르면 된다(텐서 이름은 변환기가 붙인 것).",
        "앱 테스트는 `golden_outputs.json` 을 쓴다: `../golden/<name>.json` 의 `expected` 를 넣었을 때 모든 확률의 차이가 0.02 이하이고, "
        "`margin`(1등과 2등 차이)이 0.05 이상인 예시는 1등 범주도 같은지 확인한다.",
        "",
        "## 정확도 (Quick, Draw! 테스트셋)",
        "",
        f"학습에 쓰지 않은 그림 {report['test_count']:,}장(범주마다 같은 수, 굵기 8px 로 그림). "
        f"int8 보정에는 학습 그림 {report['representative_count']}장(범주마다 같은 수)을 썼다.",
        "",
        "| 후보 | 크기 | top-1 | top-3 | 원본 대비 top-1 | 원본과 1등 일치 | 가장 많이 떨어진 범주 | 지연 p50 / p95 |",
        "|---|---|---|---|---|---|---|---|",
        f"| Keras 원본 | - | {percent(report['keras']['top1'])} | {percent(report['keras']['top3'])} | - | - | - | - |",
    ]
    for v in report["variants"]:
        name = f"**{v['variant']}** (선택)" if v["variant"] == report["chosen"] else v["variant"]
        lines.append(
            f"| {name} | {size_text(v['size_bytes'])} | {percent(v['top1'])} | {percent(v['top3'])} | "
            f"{points(-v['top1_drop'])} | {percent(v['agree_top1'])} | "
            f"{v['worst_class']['name']} ({points(-v['worst_class']['drop'], 1)}) | "
            f"{v['latency_ms']['p50']:.2f} / {v['latency_ms']['p95']:.2f} ms |")
    lines += [
        "",
        f"고르는 규칙: {report['rule']}.",
        f"지연은 {env['processor']} (CPU {env['cpu_count']}개), 스레드 {env['threads']}개로 한 장씩 부른 값이다. "
        "휴대폰에서 잰 값은 A8 에서 적는다.",
        "",
        "| 목표 | 기준 | 결과 |",
        "|---|---|---|",
        f"| top-1 | ≥ 75% | {percent(chosen['top1'])} {mark(chosen['top1'] >= TARGETS['top1'])} |",
        f"| top-3 | ≥ 90% | {percent(chosen['top3'])} {mark(chosen['top3'] >= TARGETS['top3'])} |",
        f"| 크기 | ≤ 1 MB | {size_text(chosen['size_bytes'])} {mark(chosen['size_bytes'] <= TARGETS['size_bytes'])} |",
        f"| 지연 | 휴대폰 ≤ 30 ms | 이 컴퓨터 {chosen['latency_ms']['p50']:.2f} ms · 휴대폰 미측정 |",
        "",
        "가장 못 맞힌 범주 5개: " + ", ".join(f"{c['ko']}({c['en']}) {percent(c['top1'])}" for c in report["worst_classes"]),
        "",
        "## \"잘 모르겠어요\" 기준",
        "",
        "가장 높은 확률이 기준보다 낮으면 앱은 답 대신 \"잘 모르겠어요\" 를 보여 준다. 테스트셋에서:",
        "",
        "| 기준 | 답하는 비율 | 답한 것의 정확도 |",
        "|---|---|---|",
    ]
    lines += [f"| {row['threshold']} | {percent(row['answered'])} | {percent(row['accuracy'])} |" for row in report["confidence"]]
    lines += [
        "",
        f"**지금 기준은 {UNSURE_THRESHOLD} (임시).** 6단계에서 팀원이 앱으로 그린 그림으로 다시 정한다.",
        "",
        "## 범주",
        "",
        "| 번호 | 한국어 | 영어 |",
        "|---|---|---|",
    ]
    lines += [f"| {i} | {ko} | {en} |" for i, (en, ko) in enumerate(zip(labels_en, labels_ko))]
    lines += [
        "",
        "## 학습",
        "",
        f"- 데이터: Quick, Draw! `simplified`, 범주마다 인식된(`recognized`) 그림 학습 {per_class[0]:,} / 검증 {per_class[1]:,} / "
        f"테스트 {per_class[2]:,}장 (중복 제거, 시드 {config.get('seed')}).",
        "- 학습 그림: 굵기 3~20px · 회전 ±10° · 가로세로 ±10% 를 그림마다 무작위로. 검증·테스트는 굵기 8px 고정.",
        "- 구조: Conv32 ×2 → MaxPool → Conv64 ×2 → MaxPool → Conv128 → GlobalAvgPool → Dropout 0.3 → Dense(softmax). "
        "Conv 마다 BatchNorm(momentum 0.9) + ReLU.",
        f"- 설정: epoch {training['epochs_run']} (최대 {config.get('epochs')}, 검증 정확도가 {config.get('patience')}번 안 오르면 멈춤), "
        f"batch {config.get('batch')}, AdamW(lr {config.get('lr')}, weight decay {config.get('weight_decay')}, 코사인 감소), "
        f"label smoothing {config.get('label_smoothing')}.",
        "- 버전: 학습 " + ", ".join(f"{k} {v}" for k, v in training["versions"].items())
        + f" · 내보내기 tensorflow {env['tensorflow']}, keras {env['keras']}.",
        "",
        "## 데이터 출처·라이선스",
        "",
        "Quick, Draw! Dataset by Google, [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) — "
        "<https://github.com/googlecreativelab/quickdraw-dataset>.",
        "앱 라이선스 화면(A8·A5)에 이 출처를 적는다. 구글이 이 모델을 만들거나 보증한 것이 아니다.",
        "",
        "## 한계",
        "",
        "- 학습 그림은 Quick, Draw! 사용자들이 20초 안에 그린 낙서다. 앱에서 천천히 그린 그림은 다를 수 있다 → 6단계에서 앱 그림으로 따로 잰다.",
        "- 범주 밖 그림·글자·여러 물체도 범주 중 하나로 답한다 → 위 \"잘 모르겠어요\" 기준으로 거른다.",
        "- iOS 용 Core ML 모델은 아직 없다(Windows 에서는 만들 수 없음 — Core ML 미검증).",
        "",
    ]
    return "\n".join(lines)


def main(argv=None):
    parser = argparse.ArgumentParser(description="학습한 모델 → 앱용 TFLite (크기 줄이기 비교)")
    parser.add_argument("--run", default=str(HERE / "runs" / "full"), help="train.py 결과 폴더")
    parser.add_argument("--data", default=str(HERE / "data"), help="학습에 쓴 데이터 폴더 (<data>/cache/*.npz 를 읽는다)")
    parser.add_argument("--cache", default=None, help="변환 캐시 폴더 (기본: <data>/cache)")
    parser.add_argument("--out", default=None, help="앱용 파일 폴더 (기본: export/, --smoke 는 <run>/export)")
    parser.add_argument("--golden", default=str(HERE / "golden"))
    parser.add_argument("--max-drop", type=float, default=MAX_DROP, help="허용하는 top-1 하락 (0.01 = 1%%p)")
    parser.add_argument("--representative", type=int, default=500, help="int8 보정 그림 수")
    parser.add_argument("--latency-runs", type=int, default=300)
    parser.add_argument("--threads", type=int, default=2)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--smoke", action="store_true", help="runs/smoke + data/smoke 로 연습")
    args = parser.parse_args(argv)
    if args.smoke:
        args.run, args.data = str(HERE / "runs" / "smoke"), str(HERE / "data" / "smoke")
    run = Path(args.run)
    cache = Path(args.cache) if args.cache else Path(args.data) / "cache"
    out = Path(args.out) if args.out else (run / "export" if args.smoke else HERE / "export")

    if not any(Path(args.golden).glob("*.json")):
        raise SystemExit(f"{args.golden} 에 골든 예시(*.json)가 없습니다. golden/ 폴더도 함께 올리세요.")
    labels_en, labels_ko = read_lines(run / "labels_en.txt"), read_lines(run / "labels_ko.txt")
    meta = json.loads((cache / "meta.json").read_text(encoding="utf-8"))
    if meta["classes"] != labels_en:
        raise SystemExit(f"{cache} 의 범주 순서가 {run} 과 다릅니다. 이 모델을 학습할 때 쓴 데이터를 지정하세요.")
    with np.load(cache / "test.npz") as stored:
        test_images, test_labels = stored["images"], stored["labels"]
    with np.load(cache / "train.npz") as stored:
        train_labels = stored["labels"]
        representative = to_input(stored["images"][representative_indices(train_labels, args.representative, args.seed)])

    import keras
    import tensorflow as tf

    model = keras.models.load_model(run / "model.keras")
    num_classes = len(labels_en)
    if model.output_shape[-1] != num_classes:
        raise SystemExit(f"모델 출력 {model.output_shape[-1]}개와 라벨 {num_classes}개가 다릅니다.")
    inputs = to_input(test_images)
    reference = model.predict(inputs, batch_size=1024, verbose=0)
    keras_top1, keras_top3, keras_per_class = accuracy(reference, test_labels, num_classes)
    print(f"Keras 원본: top-1 {keras_top1:.4f}, top-3 {keras_top3:.4f} (테스트 {len(test_labels):,}장)")

    candidates = run / "tflite"
    candidates.mkdir(exist_ok=True)
    results, blobs, predictions = [], {}, {}
    for variant in VARIANTS:
        blob = convert(model, variant, representative)
        (candidates / f"{variant}.tflite").write_bytes(blob)
        predicted = tflite_predict(blob, inputs)
        top1, top3, per_class = accuracy(predicted, test_labels, num_classes)
        drops = keras_per_class - per_class
        worst = int(np.nanargmax(drops))
        p50, p95 = latency(blob, inputs[:1], args.latency_runs, threads=args.threads)
        results.append({
            "variant": variant,
            "size_bytes": len(blob),
            "top1": top1,
            "top3": top3,
            "top1_drop": keras_top1 - top1,
            "agree_top1": float((predicted.argmax(axis=1) == reference.argmax(axis=1)).mean()),
            "max_abs_diff": float(np.abs(predicted - reference).max()),
            "worst_class": {"name": labels_en[worst], "drop": float(drops[worst])},
            "latency_ms": {"p50": p50, "p95": p95},
        })
        blobs[variant], predictions[variant] = blob, predicted
        print(f"{variant:8s} {size_text(len(blob)):>8s}  top-1 {top1:.4f}  top-3 {top3:.4f}  "
              f"원본 대비 {points(top1 - keras_top1)}  지연 p50 {p50:.2f}ms")

    chosen = choose(results, args.max_drop)
    _, _, chosen_per_class = accuracy(predictions[chosen], test_labels, num_classes)
    worst_classes = [{"en": labels_en[i], "ko": labels_ko[i], "top1": float(chosen_per_class[i])}
                     for i in np.argsort(chosen_per_class)[:5]]
    interpreter = load_interpreter(blobs[chosen])
    metrics = json.loads((run / "metrics.json").read_text(encoding="utf-8"))
    config = json.loads((run / "config.json").read_text(encoding="utf-8"))
    history = run / "history.csv"
    epochs_run = sum(1 for _ in csv.DictReader(history.open(encoding="utf-8"))) if history.exists() else None
    report = {
        "created": time.strftime("%Y-%m-%d"),
        "run": run.name,
        "chosen": chosen,
        "rule": f"Keras 원본보다 top-1 이 {args.max_drop * 100:g}%p 넘게 떨어지지 않는 후보 중 가장 작은 것",
        "test_count": int(len(test_labels)),
        "representative_count": int(len(representative)),
        "keras": {"top1": keras_top1, "top3": keras_top3},
        "variants": results,
        "io": {"input": tensor_info(interpreter.get_input_details()[0]),
               "output": tensor_info(interpreter.get_output_details()[0])},
        "confidence": confidence_table(predictions[chosen], test_labels),
        "unsure_threshold": UNSURE_THRESHOLD,
        "worst_classes": worst_classes,
        "training": {
            "parameters": metrics["parameters"],
            "train_count": metrics["train_count"],
            "val_count": metrics["val_count"],
            "test_count": metrics["test_count"],
            "epochs_run": epochs_run,
            "versions": metrics["versions"],
            "config": {key: config.get(key) for key in
                       ("epochs", "batch", "lr", "weight_decay", "label_smoothing", "patience", "seed")},
        },
        "environment": {"tensorflow": tf.__version__, "keras": keras.__version__, "numpy": np.__version__,
                        "processor": platform.processor() or platform.machine(), "platform": platform.platform(),
                        "cpu_count": os.cpu_count(), "threads": args.threads},
    }

    out.mkdir(parents=True, exist_ok=True)
    (out / "quickdraw_cnn.tflite").write_bytes(blobs[chosen])
    shutil.copyfile(run / "labels_en.txt", out / "labels_en.txt")
    shutil.copyfile(run / "labels_ko.txt", out / "labels_ko.txt")
    golden = {"model": "quickdraw_cnn.tflite", "variant": chosen,
              "check": "golden/<name>.json 의 expected 를 넣으면 모든 확률의 차이가 0.02 이하, "
                       "margin(1등과 2등 차이)이 0.05 이상인 예시는 1등 범주도 같다",
              "items": golden_outputs(blobs[chosen], args.golden, labels_en, labels_ko)}
    (out / "golden_outputs.json").write_text(json.dumps(golden, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (out / "export.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (out / "model_card.md").write_text(model_card(report, labels_en, labels_ko), encoding="utf-8")
    print(f"선택: {chosen} ({size_text(len(blobs[chosen]))}) -> {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
