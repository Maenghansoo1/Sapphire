"""팀원 그림 모으기 (M1 6단계). 이 PC 에서 켜고, 같은 와이파이의 휴대폰 브라우저로 접속해 그린다.

  python collect.py                 # 화면에 나오는 http://<이 PC 주소>:8000 을 휴대폰으로 연다

그림판(collect.html)은 앱 그림 미션과 같다: 흰 바탕, 펜 굵기 3·6·12, 색 7가지, 지우개(펜의 3배), 되돌리기.
저장도 앱처럼 화면 크기의 2배 PNG(흰 바탕 + 색 잉크)로 한다.
그림은 ml/eval-data/quickdraw/<사람>/<범주>__<번호>.png 와 같은 이름의 .json(획 좌표)으로 저장된다(깃 제외).
범주는 그 사람이 가장 적게 그린 것 중에서 무작위로 나온다. 6장에 1장꼴로 범주 밖 단어(OTHER_WORDS)가 나온다
— 모델이 모르는 그림에 "잘 모르겠어요" 라고 하는지 재는 데 쓴다(_other__<번호>.png).
그림은 이 PC 밖으로 나가지 않는다.
"""
import argparse
import base64
import binascii
import json
import re
import socket
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

from download import file_stem, load_categories

HERE = Path(__file__).resolve().parent
DEFAULT_OUT = HERE.parent / "eval-data" / "quickdraw"
PAGE = HERE / "collect.html"
PERSON = re.compile(r"^[a-z0-9_가-힣-]{1,20}$")  # 폴더 이름이 된다 (한글 이름 가능)
FILE = re.compile(r"^(?P<stem>[a-z0-9_-]+?)__(?P<number>\d+)\.png$")
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
MAX_BODY = 4 * 1024 * 1024
OTHER = "_other"
# 50개 범주에 없는 것. 비슷한 범주가 있는 것(트럭·버스·헬리콥터·성)도 일부러 넣었다
OTHER_WORDS = ["연필", "의자", "칫솔", "양말", "바나나", "포크", "숟가락", "거미", "개구리", "달팽이",
               "거북이", "오리", "당근", "포도", "수박", "사다리", "망치", "침대", "창문", "등대",
               "성", "로켓", "헬리콥터", "트럭", "버스", "돛단배", "텐트", "축구공", "모자", "장갑"]


class Store:
    """사람별 폴더에 그림을 저장하고 범주별로 센다."""

    def __init__(self, out, categories):
        self.out = Path(out)
        self.categories = categories
        self.by_stem = {file_stem(item["quickdraw"]): item["quickdraw"] for item in categories}
        self.by_stem[OTHER] = OTHER
        self.lock = threading.Lock()

    def files(self, person, stem=None):
        folder = self.out / person
        for path in sorted(folder.glob("*.png")) if folder.exists() else []:
            match = FILE.match(path.name)
            if match and match["stem"] in self.by_stem and (stem is None or match["stem"] == stem):
                yield path, match

    def counts(self, person):
        """{범주: 장수} — 범주 밖 그림은 OTHER 키로."""
        counts = {item["quickdraw"]: 0 for item in self.categories}
        counts[OTHER] = 0
        for _, match in self.files(person):
            counts[self.by_stem[match["stem"]]] += 1
        return counts

    def save(self, person, category, png, record):
        stem = file_stem(category)
        with self.lock:
            number = 1 + max((int(match["number"]) for _, match in self.files(person, stem)), default=0)
            name = f"{stem}__{number:03d}"
            folder = self.out / person
            folder.mkdir(parents=True, exist_ok=True)
            (folder / f"{name}.png").write_bytes(png)
            (folder / f"{name}.json").write_text(json.dumps(record, ensure_ascii=False) + "\n", encoding="utf-8")
        return f"{name}.png"

    def delete(self, person, file):
        match = FILE.match(file)
        if not match or match["stem"] not in self.by_stem:
            raise ValueError("지울 수 없는 파일 이름")
        with self.lock:
            path = self.out / person / file
            path.unlink(missing_ok=True)
            path.with_suffix(".json").unlink(missing_ok=True)


def decode_png(data_url):
    prefix = "data:image/png;base64,"
    if not isinstance(data_url, str) or not data_url.startswith(prefix):
        raise ValueError("PNG 가 아님")
    try:
        png = base64.b64decode(data_url[len(prefix):], validate=True)
    except binascii.Error as error:
        raise ValueError("PNG 가 아님") from error
    if not png.startswith(PNG_SIGNATURE):
        raise ValueError("PNG 가 아님")
    return png


def make_handler(store, goal, log=print):
    names = {item["quickdraw"] for item in store.categories} | {OTHER}
    categories = [{"quickdraw": item["quickdraw"], "ko": item["ko"]} for item in store.categories]

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, format, *args):  # 요청마다 찍지 않는다 (저장만 따로 알린다)
            pass

        def send_body(self, status, data, content_type):
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(data)

        def send_json(self, status, body):
            self.send_body(status, json.dumps(body, ensure_ascii=False).encode("utf-8"), "application/json; charset=utf-8")

        def do_GET(self):
            url = urlparse(self.path)
            if url.path == "/":
                return self.send_body(200, PAGE.read_bytes(), "text/html; charset=utf-8")
            if url.path == "/api/state":
                person = parse_qs(url.query).get("person", [""])[0]
                if not PERSON.match(person):
                    return self.send_json(400, {"error": "이름은 한글·영문 소문자·숫자 1~20자"})
                return self.send_json(200, {"categories": categories, "others": OTHER_WORDS,
                                            "counts": store.counts(person), "goal": goal})
            self.send_json(404, {"error": "없는 주소"})

        def do_POST(self):
            url = urlparse(self.path)
            length = int(self.headers.get("Content-Length") or 0)
            if length <= 0 or length > MAX_BODY:
                return self.send_json(413, {"error": "너무 큼"})
            try:
                body = json.loads(self.rfile.read(length))
            except (json.JSONDecodeError, UnicodeDecodeError):
                return self.send_json(400, {"error": "JSON 이 아님"})
            person = body.get("person", "") if isinstance(body, dict) else ""
            if not PERSON.match(person if isinstance(person, str) else ""):
                return self.send_json(400, {"error": "이름은 한글·영문 소문자·숫자 1~20자"})
            try:
                if url.path == "/api/drawings":
                    if body.get("category") not in names:
                        raise ValueError("없는 범주")
                    if body["category"] == OTHER and body.get("word") not in OTHER_WORDS:
                        raise ValueError("없는 단어")
                    png = decode_png(body.get("png"))
                    record = {"person": person, "category": body["category"], "word": body.get("word"),
                              "saved_at": time.strftime("%Y-%m-%dT%H:%M:%S"),
                              "canvas": body.get("canvas"), "draw_ms": body.get("draw_ms"), "strokes": body.get("strokes")}
                    file = store.save(person, body["category"], png, record)
                    log(f"저장: {person}/{file}")
                    return self.send_json(200, {"file": file, "counts": store.counts(person)})
                if url.path == "/api/drawings/delete":
                    store.delete(person, body.get("file", ""))
                    log(f"지움: {person}/{body['file']}")
                    return self.send_json(200, {"counts": store.counts(person)})
            except ValueError as error:
                return self.send_json(400, {"error": str(error)})
            self.send_json(404, {"error": "없는 주소"})

    return Handler


def lan_addresses():
    """휴대폰에서 접속할 이 PC 의 주소들. 첫 번째가 인터넷에 연결된 랜카드(와이파이) 주소, 나머지는 가상 랜카드일 수 있다."""
    primary = None
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
            probe.connect(("10.255.255.255", 1))  # 실제로 보내지는 않는다. 기본 경로의 내 주소만 얻는다
            primary = probe.getsockname()[0]
    except OSError:
        pass
    others = set()
    try:
        others.update(info[4][0] for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET))
    except OSError:
        pass
    ordered = ([primary] if primary else []) + sorted(others - {primary})
    return [address for address in ordered if not address.startswith("127.")]


def main(argv=None):
    parser = argparse.ArgumentParser(description="팀원 그림 모으기 서버")
    parser.add_argument("--port", type=int, default=8000)
    parser.add_argument("--out", default=str(DEFAULT_OUT))
    parser.add_argument("--categories", default=str(HERE / "categories.json"))
    parser.add_argument("--goal", type=int, default=75, help="한 사람 목표 장수 (4명 × 75 = 300)")
    args = parser.parse_args(argv)

    store = Store(args.out, load_categories(args.categories))
    server = ThreadingHTTPServer(("0.0.0.0", args.port), make_handler(store, args.goal))
    print(f"그림 저장 폴더: {Path(args.out).resolve()}")
    print("휴대폰(같은 와이파이)에서 이 주소를 연다 (안 열리면 아래 다른 주소):")
    for index, address in enumerate(lan_addresses() or ["<이 PC 의 IP>"]):
        print(f"  http://{address}:{args.port}" + ("   <- 먼저 이것" if index == 0 else ""))
    print("끝내려면 Ctrl+C")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
