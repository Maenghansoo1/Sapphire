"""소형 CNN 학습. 결과는 runs/<이름>/ 에 저장한다 (model.keras, metrics.json, history.csv, labels_*.txt, config.json).

  python train.py --smoke                         # 이 PC: data/smoke 로 15 epoch (전체 흐름 확인)
  python train.py --data data --name full         # Colab: 50개 범주 전체
"""
import argparse
import json
import os
import time
from pathlib import Path

os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "2")

import numpy as np

from dataset import load_or_build

HERE = Path(__file__).resolve().parent


def build_model(num_classes, input_size=28):
    """Conv(32)×2 → Pool → Conv(64)×2 → Pool → Conv(128) → GAP → Dropout → Dense(softmax)."""
    import keras
    from keras import layers

    def conv(x, filters):
        x = layers.Conv2D(filters, 3, padding="same", use_bias=False)(x)
        # 0.99(기본)는 이동 평균 초깃값(분산 1)이 늦게 빠져 데이터가 적을 때 추론이 망가진다. 0.9 = PyTorch 기본과 같음
        x = layers.BatchNormalization(momentum=0.9)(x)
        return layers.ReLU()(x)

    inputs = keras.Input(shape=(input_size, input_size, 1), name="image")
    x = conv(conv(inputs, 32), 32)
    x = layers.MaxPooling2D()(x)
    x = conv(conv(x, 64), 64)
    x = layers.MaxPooling2D()(x)
    x = conv(x, 128)
    x = layers.GlobalAveragePooling2D()(x)
    x = layers.Dropout(0.3)(x)
    outputs = layers.Dense(num_classes, activation="softmax", name="probabilities")(x)
    return keras.Model(inputs, outputs, name="quickdraw_cnn")


def to_dataset(images, labels, num_classes, batch, shuffle, seed):
    import tensorflow as tf

    # uint8 그대로 두고 배치마다 0~1 실수·원-핫으로 바꾼다 (150만 장을 한꺼번에 float 로 만들면 메모리가 부족)
    ds = tf.data.Dataset.from_tensor_slices((images[..., None], labels))
    if shuffle:
        ds = ds.shuffle(min(len(labels), 200_000), seed=seed, reshuffle_each_iteration=True)
    ds = ds.batch(batch).map(lambda x, y: (tf.cast(x, tf.float32) / 255.0, tf.one_hot(y, num_classes)),
                             num_parallel_calls=tf.data.AUTOTUNE)
    return ds.prefetch(tf.data.AUTOTUNE)


def per_class_accuracy(model, images, labels, classes):
    probabilities = model.predict(images.astype(np.float32)[..., None] / 255.0, batch_size=1024, verbose=0)
    top3 = np.argsort(-probabilities, axis=1)[:, :3]
    result = {}
    for label, item in enumerate(classes):
        rows = labels == label
        result[item["quickdraw"]] = {
            "top1": float((top3[rows, 0] == label).mean()) if rows.any() else None,
            "top3": float((top3[rows] == label).any(axis=1).mean()) if rows.any() else None,
            "count": int(rows.sum()),
        }
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description="Quick, Draw! 소형 CNN 학습")
    parser.add_argument("--data", default=str(HERE / "data"))
    parser.add_argument("--categories", default=str(HERE / "categories.json"))
    parser.add_argument("--cache", default=None, help="변환 캐시 폴더 (기본: <data>/cache)")
    parser.add_argument("--runs", default=str(HERE / "runs"))
    parser.add_argument("--name", default=None, help="결과 폴더 이름 (기본: 시각)")
    parser.add_argument("--epochs", type=int, default=None, help="기본 30 (--smoke 는 15)")
    parser.add_argument("--batch", type=int, default=256)
    parser.add_argument("--lr", type=float, default=2e-3)
    parser.add_argument("--weight-decay", type=float, default=1e-4)
    parser.add_argument("--label-smoothing", type=float, default=0.05)
    parser.add_argument("--patience", type=int, default=4, help="검증 정확도가 이만큼 안 오르면 멈춤")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--workers", type=int, default=None, help="그림 변환 프로세스 수")
    parser.add_argument("--rebuild-cache", action="store_true")
    parser.add_argument("--smoke", action="store_true", help="data/smoke 로 짧게 (흐름 확인용)")
    args = parser.parse_args(argv)
    if args.smoke:
        args.data = str(HERE / "data" / "smoke")
        args.name = args.name or "smoke"
    args.epochs = args.epochs or (15 if args.smoke else 30)

    import keras
    import tensorflow as tf

    keras.utils.set_random_seed(args.seed)
    started = time.time()
    splits, classes = load_or_build(args.data, args.categories, args.cache, args.seed, args.workers, args.rebuild_cache)
    prepared = time.time()
    num_classes = len(classes)
    run = Path(args.runs) / (args.name or time.strftime("%Y%m%d-%H%M%S"))
    run.mkdir(parents=True, exist_ok=True)
    (run / "config.json").write_text(json.dumps(vars(args), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (run / "labels_en.txt").write_text("".join(item["quickdraw"] + "\n" for item in classes), encoding="utf-8")
    (run / "labels_ko.txt").write_text("".join(item["ko"] + "\n" for item in classes), encoding="utf-8")

    train_images, train_labels = splits["train"]
    steps = max(1, int(np.ceil(len(train_labels) / args.batch))) * args.epochs
    model = build_model(num_classes)
    model.compile(
        optimizer=keras.optimizers.AdamW(
            learning_rate=keras.optimizers.schedules.CosineDecay(args.lr, decay_steps=steps),
            weight_decay=args.weight_decay),
        loss=keras.losses.CategoricalCrossentropy(label_smoothing=args.label_smoothing),
        metrics=[keras.metrics.CategoricalAccuracy(name="accuracy"),
                 keras.metrics.TopKCategoricalAccuracy(k=3, name="top3")])
    print(f"범주 {num_classes}개, 학습 {len(train_labels)}장, 파라미터 {model.count_params():,}개")

    model.fit(
        to_dataset(train_images, train_labels, num_classes, args.batch, True, args.seed),
        validation_data=to_dataset(*splits["val"], num_classes, args.batch, False, args.seed),
        epochs=args.epochs,
        shuffle=False,  # 섞기는 to_dataset 이 한다
        callbacks=[
            keras.callbacks.EarlyStopping(monitor="val_accuracy", patience=args.patience, restore_best_weights=True),
            keras.callbacks.ModelCheckpoint(str(run / "best.keras"), monitor="val_accuracy", save_best_only=True),
            keras.callbacks.CSVLogger(str(run / "history.csv")),
        ],
        verbose=2,
    )
    trained = time.time()

    test_images, test_labels = splits["test"]
    _, test_top1, test_top3 = model.evaluate(
        to_dataset(test_images, test_labels, num_classes, args.batch, False, args.seed), verbose=0)
    model.save(run / "model.keras")
    metrics = {
        "classes": num_classes,
        "train_count": int(len(train_labels)),
        "val_count": int(len(splits["val"][1])),
        "test_count": int(len(test_labels)),
        "parameters": int(model.count_params()),
        "test_top1": float(test_top1),
        "test_top3": float(test_top3),
        "per_class": per_class_accuracy(model, test_images, test_labels, classes),
        "seconds": {"prepare": round(prepared - started, 1), "train": round(trained - prepared, 1)},
        "versions": {"tensorflow": tf.__version__, "keras": keras.__version__, "numpy": np.__version__},
    }
    (run / "metrics.json").write_text(json.dumps(metrics, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"테스트 top-1 {test_top1:.3f}, top-3 {test_top3:.3f}  "
          f"(준비 {metrics['seconds']['prepare']}s, 학습 {metrics['seconds']['train']}s) -> {run}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
