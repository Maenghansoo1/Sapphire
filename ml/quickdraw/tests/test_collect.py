import base64
import io
import json
import threading
import urllib.error
import urllib.request

import pytest
from PIL import Image

import collect

CATEGORIES = [{"id": 0, "quickdraw": "cat", "ko": "고양이", "aliases_ko": []},
              {"id": 1, "quickdraw": "hot air balloon", "ko": "열기구", "aliases_ko": []}]


def png_bytes():
    buffer = io.BytesIO()
    Image.new("RGB", (4, 4), "white").save(buffer, format="PNG")
    return buffer.getvalue()


def data_url():
    return "data:image/png;base64," + base64.b64encode(png_bytes()).decode()


def test_store_numbers_counts_and_deletes(tmp_path):
    store = collect.Store(tmp_path, CATEGORIES)
    assert store.save("a", "hot air balloon", png_bytes(), {"x": 1}) == "hot_air_balloon__001.png"
    assert store.save("a", "hot air balloon", png_bytes(), {}) == "hot_air_balloon__002.png"
    assert store.save("a", collect.OTHER, png_bytes(), {}) == "_other__001.png"
    assert store.save("b", "cat", png_bytes(), {}) == "cat__001.png"
    assert json.loads((tmp_path / "a" / "hot_air_balloon__001.json").read_text(encoding="utf-8")) == {"x": 1}
    assert store.counts("a") == {"cat": 0, "hot air balloon": 2, collect.OTHER: 1}
    store.delete("a", "hot_air_balloon__001.png")
    assert not (tmp_path / "a" / "hot_air_balloon__001.json").exists()
    assert store.save("a", "hot air balloon", png_bytes(), {}) == "hot_air_balloon__003.png"  # 번호는 다시 쓰지 않는다
    for bad in ("../b/cat__001.png", "dog__001.png", "cat__001.json"):
        with pytest.raises(ValueError):
            store.delete("a", bad)


def test_decode_png_rejects_other_data():
    assert collect.decode_png(data_url()) == png_bytes()
    for bad in ("data:image/jpeg;base64,AAAA", "data:image/png;base64,@@@", "data:image/png;base64," + base64.b64encode(b"GIF89a").decode(), None):
        with pytest.raises(ValueError):
            collect.decode_png(bad)


@pytest.fixture
def server(tmp_path):
    store = collect.Store(tmp_path, CATEGORIES)
    httpd = collect.ThreadingHTTPServer(("127.0.0.1", 0), collect.make_handler(store, goal=3, log=lambda _: None))
    thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    thread.start()
    yield f"http://127.0.0.1:{httpd.server_port}", tmp_path
    httpd.shutdown()
    httpd.server_close()


def call(url, body=None):
    request = urllib.request.Request(url, data=None if body is None else json.dumps(body).encode(),
                                     headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()


def test_server_saves_and_deletes_drawings(server):
    base, root = server
    status, page = call(base + "/")
    assert status == 200 and "그림 모으기".encode() in page
    status, body = call(base + "/api/state?person=a")
    state = json.loads(body)
    assert status == 200 and state["goal"] == 3 and [c["ko"] for c in state["categories"]] == ["고양이", "열기구"]
    assert "연필" in state["others"] and state["counts"]["cat"] == 0

    drawing = {"person": "a", "category": "cat", "png": data_url(), "canvas": {"widthDp": 320}, "draw_ms": 900,
               "strokes": [{"color": "#111111", "widthDp": 6, "eraser": False, "points": [[1, 2]]}]}
    status, body = call(base + "/api/drawings", drawing)
    assert status == 200 and json.loads(body)["file"] == "cat__001.png"
    record = json.loads((root / "a" / "cat__001.json").read_text(encoding="utf-8"))
    assert record["strokes"][0]["points"] == [[1, 2]] and record["draw_ms"] == 900
    status, body = call(base + "/api/drawings", {**drawing, "category": collect.OTHER, "word": "연필"})
    assert status == 200 and json.loads(body)["counts"][collect.OTHER] == 1

    for bad in ({**drawing, "person": "A/.."}, {**drawing, "category": "dog"}, {**drawing, "png": "x"},
                {**drawing, "category": collect.OTHER, "word": "고양이"}):
        assert call(base + "/api/drawings", bad)[0] == 400
    assert call(base + "/api/state?person=")[0] == 400
    assert call(base + "/nothing")[0] == 404

    status, body = call(base + "/api/drawings/delete", {"person": "a", "file": "cat__001.png"})
    assert status == 200 and json.loads(body)["counts"]["cat"] == 0
    assert not (root / "a" / "cat__001.png").exists()
