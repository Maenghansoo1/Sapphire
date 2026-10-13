import json

import numpy as np
import pytest

import export


def test_representative_is_balanced_and_repeatable():
    labels = np.repeat([0, 1, 2], [40, 7, 40])
    picked = export.representative_indices(labels, count=30, seed=1)
    assert np.bincount(labels[picked]).tolist() == [10, 7, 10]  # 그림이 모자란 범주는 있는 만큼
    assert np.all(np.diff(picked) > 0)
    assert picked.tolist() == export.representative_indices(labels, count=30, seed=1).tolist()
    assert picked.tolist() != export.representative_indices(labels, count=30, seed=2).tolist()


def test_choose_smallest_within_allowed_drop():
    results = [{"variant": "float32", "size_bytes": 500, "top1_drop": 0.0},
               {"variant": "float16", "size_bytes": 250, "top1_drop": 0.004},
               {"variant": "int8", "size_bytes": 130, "top1_drop": 0.02}]
    assert export.choose(results) == "float16"
    assert export.choose(results, max_drop=0.03) == "int8"
    assert export.choose([{"variant": "int8", "size_bytes": 1, "top1_drop": 0.5}]) == "float32"


def test_accuracy_and_confidence_table():
    probabilities = np.array([[0.7, 0.2, 0.1], [0.3, 0.4, 0.3], [0.1, 0.5, 0.4], [0.2, 0.3, 0.5]])
    labels = np.array([0, 0, 2, 2])
    top1, top3, per_class = export.accuracy(probabilities, labels, 3)
    assert (top1, top3) == (0.5, 1.0)
    assert per_class[0] == 0.5 and np.isnan(per_class[1]) and per_class[2] == 0.5
    rows = export.confidence_table(probabilities, labels, levels=(0.45, 0.6))
    assert rows[0] == {"threshold": 0.45, "answered": 0.75, "accuracy": pytest.approx(2 / 3)}
    assert rows[1] == {"threshold": 0.6, "answered": 0.25, "accuracy": 1.0}


def fake_run(root, names):
    """학습하지 않은 작은 모델과 가짜 캐시로 train.py 결과 폴더 흉내."""
    from train import build_model

    run, cache = root / "runs" / "tiny", root / "data" / "cache"
    run.mkdir(parents=True)
    cache.mkdir(parents=True)
    build_model(len(names)).save(run / "model.keras")
    (run / "labels_en.txt").write_text("".join(f"{n}\n" for n in names), encoding="utf-8")
    (run / "labels_ko.txt").write_text("".join(f"{n}-ko\n" for n in names), encoding="utf-8")
    (run / "metrics.json").write_text(json.dumps({
        "parameters": 1, "train_count": 12, "val_count": 6, "test_count": 6,
        "versions": {"tensorflow": "x"}}), encoding="utf-8")
    (run / "config.json").write_text(json.dumps({"epochs": 1, "seed": 42}), encoding="utf-8")
    (cache / "meta.json").write_text(json.dumps({"classes": names}), encoding="utf-8")
    rng = np.random.default_rng(0)
    for split, count in (("train", 12), ("test", 6)):
        labels = np.arange(count, dtype=np.int32) % len(names)
        np.savez_compressed(cache / f"{split}.npz", images=rng.integers(0, 256, (count, 28, 28), dtype=np.uint8),
                            labels=labels)
    return run


def test_export_end_to_end(tmp_path):
    names = ["cat", "dog", "sun"]
    run = fake_run(tmp_path, names)
    out = tmp_path / "export"
    assert export.main(["--run", str(run), "--data", str(tmp_path / "data"), "--out", str(out),
                        "--representative", "6", "--latency-runs", "5"]) == 0

    report = json.loads((out / "export.json").read_text(encoding="utf-8"))
    assert [v["variant"] for v in report["variants"]] == list(export.VARIANTS)
    assert all((run / "tflite" / f"{v}.tflite").exists() for v in export.VARIANTS)
    assert report["chosen"] in export.VARIANTS and report["representative_count"] == 6

    # 고른 모델: 입력 [1,28,28,1] float32, 출력 [1, 범주 수] float32 = 라벨 줄 수
    interpreter = export.load_interpreter((out / "quickdraw_cnn.tflite").read_bytes())
    source, target = interpreter.get_input_details()[0], interpreter.get_output_details()[0]
    assert source["shape"].tolist() == [1, 28, 28, 1] and source["dtype"] == np.float32
    assert target["shape"].tolist() == [1, len(names)] and target["dtype"] == np.float32
    assert export.read_lines(out / "labels_en.txt") == names
    assert export.read_lines(out / "labels_ko.txt") == [f"{n}-ko" for n in names]

    golden = json.loads((out / "golden_outputs.json").read_text(encoding="utf-8"))
    expected = [json.loads(p.read_text(encoding="utf-8")) for p in sorted((export.HERE / "golden").glob("*.json"))]
    assert [item["name"] for item in golden["items"]] == [c["name"] for c in expected if c["expected"] is not None]
    assert all(len(item["probabilities"]) == len(names) for item in golden["items"])
    assert "quickdraw-dataset" in (out / "model_card.md").read_text(encoding="utf-8")


def test_export_refuses_data_with_other_class_order(tmp_path):
    run = fake_run(tmp_path, ["cat", "dog"])
    (tmp_path / "data" / "cache" / "meta.json").write_text(json.dumps({"classes": ["dog", "cat"]}), encoding="utf-8")
    with pytest.raises(SystemExit, match="범주 순서"):
        export.main(["--run", str(run), "--data", str(tmp_path / "data"), "--out", str(tmp_path / "export")])
