import json

import numpy as np
import pytest

import dataset
from make_golden import CAT_STROKES


def write_data(root, names, per_split=3):
    for split in ("train", "val", "test"):
        (root / split).mkdir(parents=True, exist_ok=True)
        for name in names:
            rows = [json.dumps({"key_id": str(i), "drawing": CAT_STROKES}) for i in range(per_split)]
            (root / split / f"{name.replace(' ', '_')}.ndjson").write_text("\n".join(rows) + "\n", encoding="utf-8")


def write_categories(path, names):
    path.write_text(json.dumps([{"id": i, "quickdraw": n, "ko": n, "aliases_ko": []} for i, n in enumerate(names)]),
                    encoding="utf-8")


def test_present_classes_keeps_category_order_and_skips_missing(tmp_path):
    write_data(tmp_path / "data", ["cat", "hot air balloon"])
    write_categories(tmp_path / "cats.json", ["sun", "cat", "dog", "hot air balloon"])
    classes = dataset.present_classes(tmp_path / "data", dataset.load_categories(tmp_path / "cats.json"))
    assert [item["quickdraw"] for item in classes] == ["cat", "hot air balloon"]


def test_build_cache_shapes_labels_and_reuse(tmp_path):
    write_data(tmp_path / "data", ["cat", "face"])
    write_categories(tmp_path / "cats.json", ["cat", "face"])
    splits, classes = dataset.load_or_build(tmp_path / "data", tmp_path / "cats.json", workers=1, log=lambda _: None)
    images, labels = splits["train"]
    assert images.shape == (6, 28, 28) and images.dtype == np.uint8
    assert labels.tolist() == [0, 0, 0, 1, 1, 1]
    assert images.max() == 255
    assert [item["quickdraw"] for item in classes] == ["cat", "face"]
    messages = []
    again, _ = dataset.load_or_build(tmp_path / "data", tmp_path / "cats.json", workers=1, log=messages.append)
    assert all(message.startswith("캐시 사용") for message in messages)
    assert np.array_equal(again["train"][0], images)


def test_train_is_augmented_but_eval_is_fixed(tmp_path):
    write_data(tmp_path / "data", ["cat"], per_split=2)
    write_categories(tmp_path / "cats.json", ["cat"])
    classes = dataset.present_classes(tmp_path / "data", dataset.load_categories(tmp_path / "cats.json"))
    train, _ = dataset.build_split(tmp_path / "data", classes, "train", seed=1, workers=1)
    test, _ = dataset.build_split(tmp_path / "data", classes, "test", seed=1, workers=1)
    assert not np.array_equal(train[0], train[1])
    assert np.array_equal(test[0], test[1])
    again, _ = dataset.build_split(tmp_path / "data", classes, "train", seed=1, workers=1)
    assert np.array_equal(train, again)


def test_model_shape():
    pytest.importorskip("tensorflow")
    import train

    model = train.build_model(5)
    assert model.input_shape == (None, 28, 28, 1)
    assert model.output_shape == (None, 5)
    assert 100_000 < model.count_params() < 400_000
