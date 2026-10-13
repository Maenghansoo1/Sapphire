import numpy as np
import pytest
from PIL import Image, ImageDraw

import evaluate
import export


def test_unsure_curve_counts_out_of_scope_answers_as_wrong():
    probabilities = np.array([[0.9, 0.1], [0.6, 0.4], [0.3, 0.7], [0.55, 0.45]])
    labels = np.array([0, 1, 1, -1])  # 두 번째는 틀림, 네 번째는 범주 밖
    curve = {row["threshold"]: row for row in evaluate.unsure_curve(probabilities, labels, thresholds=(0.5, 0.58, 0.8))}
    assert curve[0.5] == {"threshold": 0.5, "answered": 1.0, "accuracy": pytest.approx(2 / 3), "other_answered": 1.0,
                          "precision": 0.5, "score": 0.0}
    assert curve[0.58]["other_answered"] == 0.0 and curve[0.58]["score"] == pytest.approx(1 / 4)
    assert curve[0.8]["answered"] == pytest.approx(1 / 3) and curve[0.8]["score"] == pytest.approx(1 / 4)
    assert evaluate.pick_unsure_threshold(list(curve.values()))["threshold"] == 0.58  # 동점이면 낮은 기준
    assert evaluate.pick_unsure_threshold([]) is None


def test_confused_pairs_precision_and_calibration():
    probabilities = np.array([[0.9, 0.1, 0.0], [0.2, 0.8, 0.0], [0.1, 0.9, 0.0], [0.0, 0.0, 1.0]])
    labels = np.array([0, 0, 0, 2])
    assert evaluate.confused_pairs(probabilities, labels) == [{"true": 0, "predicted": 1, "count": 2, "rate": 2 / 3}]
    precision = evaluate.precision_per_class(probabilities, labels, 3)
    assert precision[0] == 1.0 and precision[1] == 0.0 and precision[2] == 1.0
    assert evaluate.calibration_error(np.array([[1.0, 0.0], [0.0, 1.0]]), np.array([0, 1])) == 0.0
    assert evaluate.calibration_error(np.array([[0.8, 0.2]]), np.array([1])) == pytest.approx(0.8)


def save_drawing(path, line=True):
    path.parent.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGB", (200, 240), "white")
    if line:
        ImageDraw.Draw(image).line([(20, 30), (180, 200)], fill="#2F5BFF", width=12)
    image.save(path)


def test_load_drawings_labels_and_skips(tmp_path):
    save_drawing(tmp_path / "a" / "hot_air_balloon__001.png")
    save_drawing(tmp_path / "a" / "_other__001.png")
    save_drawing(tmp_path / "a" / "cat__002.png", line=False)  # 너무 비어 있음
    save_drawing(tmp_path / "b" / "banana__001.png")           # 모델에 없는 범주
    save_drawing(tmp_path / "b" / "notes.png")                 # 이름 규칙 밖 → 무시
    save_drawing(tmp_path / "_results" / "cat__001.png")       # 결과 폴더 → 무시
    drawings, skipped = evaluate.load_drawings(tmp_path, ["cat", "hot air balloon"])
    assert [(d["person"], d["file"], d["label"]) for d in drawings] == [("a", "_other__001.png", -1),
                                                                     ("a", "hot_air_balloon__001.png", 1)]
    assert drawings[0]["input"].shape == (28, 28) and drawings[0]["input"].max() == pytest.approx(1.0)
    assert skipped == {"너무 비어 있음": 1, "모델에 없는 범주": 1}


def test_evaluate_end_to_end_without_test_set(tmp_path):
    from train import build_model

    model_dir = tmp_path / "export"
    model_dir.mkdir()
    (model_dir / "quickdraw_cnn.tflite").write_bytes(export.convert(build_model(2), "float32"))
    (model_dir / "labels_en.txt").write_text("cat\nsun\n", encoding="utf-8")
    (model_dir / "labels_ko.txt").write_text("고양이\n해\n", encoding="utf-8")
    for person in ("a", "b"):
        save_drawing(tmp_path / "drawings" / person / "cat__001.png")
        save_drawing(tmp_path / "drawings" / person / "sun__001.png")
        save_drawing(tmp_path / "drawings" / person / "_other__001.png")
    out = tmp_path / "EVAL.md"
    assert evaluate.main(["--model", str(model_dir / "quickdraw_cnn.tflite"), "--drawings", str(tmp_path / "drawings"),
                          "--data", str(tmp_path / "no-data"), "--out", str(out)]) == 0
    text = out.read_text(encoding="utf-8")
    assert "| 팀원이 앱 그림판으로 그린 그림 | 4 |" in text and "추천 기준" in text and "테스트셋 |" not in text
    assert "_other" not in text and ".png" not in text  # 보고서에는 파일 이름을 적지 않는다
    rows = (tmp_path / "drawings" / "_results" / "predictions.csv").read_text(encoding="utf-8-sig").splitlines()
    assert len(rows) == 1 + 6


def test_evaluate_without_drawings_still_writes_report(tmp_path):
    from train import build_model

    (tmp_path / "quickdraw_cnn.tflite").write_bytes(export.convert(build_model(2), "float32"))
    (tmp_path / "labels_en.txt").write_text("cat\nsun\n", encoding="utf-8")
    (tmp_path / "labels_ko.txt").write_text("고양이\n해\n", encoding="utf-8")
    out = tmp_path / "EVAL.md"
    assert evaluate.main(["--model", str(tmp_path / "quickdraw_cnn.tflite"), "--drawings", str(tmp_path / "none"),
                          "--data", str(tmp_path / "none"), "--out", str(out)]) == 0
    assert "팀원 그림이 없어" in out.read_text(encoding="utf-8")
