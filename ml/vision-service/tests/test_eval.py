import pytest

import eval as evaluation


def write_person(root, person, rows, files=None):
    folder = root / person
    folder.mkdir(parents=True)
    for name in files if files is not None else [row[0] for row in rows]:
        (folder / name).write_bytes(b"x")
    lines = ["파일,주제,색,메모"] + [",".join(row) for row in rows]
    (folder / f"{person}.csv").write_text("\n".join(lines) + "\n", encoding="utf-8-sig")


def test_read_labels_collects_all_people_and_reports_problems(tmp_path):
    write_person(tmp_path, "우준", [("a.jpg", "하늘|바다", "파랑", ""), ("b.jpg", "컵", "", "메모"),
                                   ("gone.jpg", "책", "", "")], files=["a.jpg", "b.jpg"])
    write_person(tmp_path, "찬희", [("c.jpg", "", "자주", ""), ("d.jpg", "신발", "하늘색", "")])
    (tmp_path / "_results").mkdir()
    photos, problems = evaluation.read_labels(tmp_path)
    assert [(p["person"], p["file"], p["topics"], p["color"]) for p in photos] == [
        ("우준", "a.jpg", ["하늘", "바다"], "파랑"), ("우준", "b.jpg", ["컵"], ""), ("찬희", "d.jpg", ["신발"], "")]
    assert len(problems) == 4  # 없는 파일, 알 수 없는 색 2개, 주제·색 모두 빔


def photo(file, topics, color=""):
    return {"person": "p", "file": file, "path": None, "topics": topics, "color": color, "note": ""}


def test_pairs_are_balanced_and_avoid_related_topics():
    photos = [photo("a.jpg", ["하늘", "바다"], "파랑"), photo("b.jpg", ["노트북", "책상 위"]), photo("c.jpg", ["바셀린"])]
    pairs = evaluation.make_pairs(photos)
    for name, topics in (("p/a.jpg", {"하늘", "바다"}), ("p/b.jpg", {"노트북", "책상 위"})):
        mine = [p for p in pairs if p["photo"] == name and p["template"] == "PHOTO_NOW"]
        assert sum(p["label"] for p in mine) == len(topics) == len(mine) - sum(p["label"] for p in mine)
        negatives = {p["topic"] for p in mine if p["label"] == 0}
        assert not negatives & evaluation.blocked_topics(topics)
    color = [p for p in pairs if p["template"] == "PHOTO_COLOR"]
    assert [(p["topic"], p["label"]) for p in color][0] == ("파랑", 1)
    assert color[1]["topic"] not in {"파랑", "보라"} and color[1]["label"] == 0


def test_pairs_are_stable_when_photos_are_added():
    first = evaluation.make_pairs([photo("a.jpg", ["컵"])])
    more = evaluation.make_pairs([photo("a.jpg", ["컵"]), photo("b.jpg", ["하늘"])])
    assert more[:len(first)] == first


def test_roc_auc():
    assert evaluation.roc_auc([0.9, 0.8, 0.2, 0.1], [1, 1, 0, 0]) == 1.0
    assert evaluation.roc_auc([0.1, 0.2, 0.8, 0.9], [1, 1, 0, 0]) == 0.0
    assert evaluation.roc_auc([0.5, 0.5], [1, 0]) == 0.5
    assert evaluation.roc_auc([0.5], [1]) is None


def test_threshold_curve_and_pick():
    curve = evaluation.threshold_curve([0.95, 0.9, 0.6, 0.3, 0.1], [1, 1, 0, 1, 0])
    assert len(curve) == 1001
    at_half = next(row for row in curve if row["threshold"] == 0.5)
    assert (at_half["tp"], at_half["fp"], at_half["fn"], at_half["tn"]) == (2, 1, 1, 1)
    assert at_half["precision"] == pytest.approx(2 / 3)
    picked = evaluation.pick_threshold(curve, 0.95)
    assert picked["threshold"] == pytest.approx(0.601) and picked["fp"] == 0
    assert evaluation.pick_threshold(evaluation.threshold_curve([0.9], [0]), 0.95) is None
