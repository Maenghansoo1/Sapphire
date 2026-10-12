import json

import pytest

import download


def line(key, drawing, recognized=True):
    return json.dumps({"word": "cat", "key_id": key, "recognized": recognized, "drawing": drawing})


def samples(count):
    return [{"key_id": str(i), "drawing": f"[[[{i}],[{i}]]]"} for i in range(count)]


def test_categories_file_has_50_ordered_unique_entries():
    categories = download.load_categories(download.HERE / "categories.json")
    assert len(categories) == 50
    assert [item["id"] for item in categories] == list(range(50))
    assert len({item["quickdraw"] for item in categories}) == 50
    assert all(item["ko"].strip() for item in categories)
    assert all(isinstance(item["aliases_ko"], list) for item in categories)


def test_load_categories_rejects_gap_and_duplicate(tmp_path):
    gap = tmp_path / "gap.json"
    gap.write_text(json.dumps([{"id": 0, "quickdraw": "cat"}, {"id": 2, "quickdraw": "dog"}]), encoding="utf-8")
    with pytest.raises(ValueError):
        download.load_categories(gap)
    duplicate = tmp_path / "dup.json"
    duplicate.write_text(json.dumps([{"id": 0, "quickdraw": "cat"}, {"id": 1, "quickdraw": "cat"}]), encoding="utf-8")
    with pytest.raises(ValueError):
        download.load_categories(duplicate)


def test_source_url_encodes_spaces():
    assert download.source_url("hot air balloon").endswith("/simplified/hot%20air%20balloon.ndjson")
    assert download.source_url("teddy-bear").endswith("/simplified/teddy-bear.ndjson")
    assert download.file_stem("hot air balloon") == "hot_air_balloon"


def test_parse_line_keeps_only_recognized_drawings():
    parsed = download.parse_line(line("1", [[[0, 10], [0, 10]]]))
    assert parsed == {"key_id": "1", "drawing": "[[[0,10],[0,10]]]"}
    assert download.parse_line(line("2", [[[0], [0]]], recognized=False)) is None
    assert download.parse_line(line("3", [])) is None
    assert download.parse_line("{broken") is None
    assert download.parse_line("[]") is None


def test_dedupe_removes_same_key_and_same_drawing():
    result = download.dedupe([
        {"key_id": "1", "drawing": "a"},
        {"key_id": "1", "drawing": "b"},
        {"key_id": "2", "drawing": "a"},
        {"key_id": "3", "drawing": "c"},
    ])
    assert [sample["key_id"] for sample in result] == ["1", "3"]


def test_select_and_split_is_deterministic_and_8_1_1():
    first = download.select_and_split(samples(100), 50, "42:cat")
    again = download.select_and_split(list(reversed(samples(100))), 50, "42:cat")
    assert first == again
    assert [len(first[split]) for split in download.SPLITS] == [40, 5, 5]
    keys = [sample["key_id"] for split in download.SPLITS for sample in first[split]]
    assert len(set(keys)) == 50
    assert first != download.select_and_split(samples(100), 50, "7:cat")


def test_select_and_split_uses_everything_when_fewer_samples():
    result = download.select_and_split(samples(7), 30, "42:cat")
    assert sum(len(result[split]) for split in download.SPLITS) == 7


def test_write_split_round_trips(tmp_path):
    path = tmp_path / "train" / "cat.ndjson"
    download.write_split(path, [{"key_id": "1", "drawing": "[[[0,10],[0,10]]]"}])
    rows = [json.loads(text) for text in path.read_text(encoding="utf-8").splitlines()]
    assert rows == [{"key_id": "1", "drawing": [[[0, 10], [0, 10]]]}]
