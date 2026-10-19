"""사진-주제 적합도 점수. 다국어 CLIP: 이미지 clip-ViT-B-32 + 텍스트 clip-ViT-B-32-multilingual-v1 (한국어 그대로 입력).

원시 코사인은 값 범위가 좁아 해석이 어렵다. 그래서 주제 문장 T 를 일상 사진 주제 목록 D 와 함께 비교해
softmax(LOGIT_SCALE × cos(사진, [T ∪ D]))[T] 를 fit(0~1)으로 쓴다 = "다른 흔한 주제보다 이 주제에 얼마나 더 가까운가".

방식은 팀 사진 42장 비교 실험(2026-10-12, EVAL.md)으로 골랐다:
- 이 모델의 한국어 텍스트 벡터는 서로 너무 가깝다(관계없는 단어끼리도 코사인 0.9 이상) → 텍스트 유사도로
  비슷한 대조어를 거르면 대조어가 거의 다 빠져 점수가 항상 1 이 된다. 그래서 글자 기준으로만 거른다(keep_distractors).
- PHOTO_NOW 는 주제 단어만(AUC 0.968, "~ 사진" 문장을 섞으면 0.947), PHOTO_COLOR 는 문장 3개 평균(0.936, 단어만 0.911).
- softmax 배율 50 (100: 0.957/0.933, 20: 0.967/0.932).

실행 방식(backend): models/ 에 ONNX 파일(export_onnx.py 가 만듦)이 있으면 onnxruntime 으로, 없으면 원래대로
sentence-transformers(PyTorch)로 돌린다. ONNX 는 PyTorch 가 필요 없어 설치가 훨씬 가볍다.
"""
import io
import os
import threading
from pathlib import Path

import numpy as np
from PIL import Image, ImageOps

try:
    from pillow_heif import register_heif_opener

    register_heif_opener()
except ImportError:  # HEIC 지원은 선택
    pass

HERE = Path(__file__).resolve().parent
IMAGE_MODEL = "clip-ViT-B-32"
TEXT_MODEL = "sentence-transformers/clip-ViT-B-32-multilingual-v1"
MODEL_NAME = "clip-ViT-B-32+multilingual-v1"
VERSION = "1"
LOGIT_SCALE = 50.0
MODEL_DIR = Path(os.environ.get("HAWE_VISION_MODEL_DIR", HERE / "models"))
# ONNX 파일 종류: "fp16"(절반 크기, 판정 같음) 또는 ""(fp32). 비워 두면 있는 파일 중 fp16 → fp32 순서로 고른다
ONNX_VARIANT = os.environ.get("HAWE_VISION_ONNX_VARIANT")
BACKEND = os.environ.get("HAWE_VISION_BACKEND", "auto")
CLIP_SIZE = 224
CLIP_MEAN = np.array([0.48145466, 0.4578275, 0.40821073], dtype=np.float32)
CLIP_STD = np.array([0.26862954, 0.26130258, 0.27577711], dtype=np.float32)
MAX_TEXT_TOKENS = 128
TEMPLATES = ("PHOTO_NOW", "PHOTO_COLOR")
MAX_TOPIC_LENGTH = 60

# 평가 이름표의 색 이름 → 프롬프트에 쓰는 말 (미션 params.color 는 이미 "파란색" 형태로 온다)
COLOR_WORDS = {
    "빨강": "빨간색", "주황": "주황색", "노랑": "노란색", "초록": "초록색", "파랑": "파란색", "보라": "보라색",
    "분홍": "분홍색", "흰색": "흰색", "검정": "검은색", "갈색": "갈색", "회색": "회색",
}


def has_final_consonant(word):
    last = word.strip()[-1:] if word.strip() else ""
    return bool(last) and 0xAC00 <= ord(last) <= 0xD7A3 and (ord(last) - 0xAC00) % 28 != 0


def subject_particle(word):
    return "이" if has_final_consonant(word) else "가"


def color_word(topic):
    return COLOR_WORDS.get(topic.strip(), topic.strip())


def prompts(topic, template):
    """주제를 텍스트 인코더에 넣을 문장들(평균낸다). 색은 문장 3개, 그 밖은 주제 단어만."""
    topic = topic.strip()
    if template == "PHOTO_COLOR":
        color = color_word(topic)
        return [f"{color} 물건", f"{color} 물건 사진", f"{color}{subject_particle(color)} 눈에 띄는 사진"]
    return [topic]


def topic_text(topic, template):
    return color_word(topic) if template == "PHOTO_COLOR" else topic.strip()


def distractor_topics(template):
    if template == "PHOTO_COLOR":
        return list(dict.fromkeys(COLOR_WORDS.values()))
    lines = (HERE / "distractors.ko.txt").read_text(encoding="utf-8").splitlines()
    return [line.strip() for line in lines if line.strip() and not line.startswith("#")]


def normalize(vectors):
    vectors = np.asarray(vectors, dtype=np.float32)
    return vectors / np.linalg.norm(vectors, axis=-1, keepdims=True)


def mean_vector(vectors):
    return normalize(normalize(vectors).mean(axis=0))


def keep_distractors(topic, names):
    """주제와 같은 말이거나 한쪽이 다른 쪽을 포함하면(예: "커피" ⊂ "커피 컵") 대조 목록에서 뺀다. 남길 위치 목록."""
    text = topic.replace(" ", "")
    return [index for index, name in enumerate(names)
            if not (name.replace(" ", "") in text or text in name.replace(" ", ""))]


def fit_score(image_vector, topic_vector, distractor_vectors, scale=LOGIT_SCALE):
    """(fit, cosine). 벡터는 모두 정규화돼 있어야 한다."""
    cosine = float(image_vector @ topic_vector)
    logits = scale * np.concatenate([[cosine], distractor_vectors @ image_vector])
    weights = np.exp(logits - logits.max())
    return float(weights[0] / weights.sum()), cosine


def decode_image(data):
    """바이트 → RGB 이미지 (휴대폰 회전 정보 반영). 깨진 파일은 ValueError."""
    try:
        with Image.open(io.BytesIO(data)) as image:
            image.load()
            return ImageOps.exif_transpose(image).convert("RGB")
    except Exception as error:
        raise ValueError("이미지를 읽을 수 없습니다") from error


def clip_pixels(image):
    """CLIP 사진 전처리. transformers CLIPImageProcessor 와 같게: 짧은 변을 224 로 줄이고(bicubic, 긴 변은 버림),
    가운데 224×224 를 잘라 0~1 로 바꾼 뒤 평균·표준편차로 정규화한다. → (1, 3, 224, 224) float32"""
    width, height = image.size
    short, long = (width, height) if width <= height else (height, width)
    new_long = int(CLIP_SIZE * long / short)
    size = (CLIP_SIZE, new_long) if width <= height else (new_long, CLIP_SIZE)
    resized = image.convert("RGB").resize(size, Image.BICUBIC)
    left, top = (size[0] - CLIP_SIZE) // 2, (size[1] - CLIP_SIZE) // 2
    pixels = np.asarray(resized, dtype=np.float32)[top:top + CLIP_SIZE, left:left + CLIP_SIZE] / 255.0
    return ((pixels - CLIP_MEAN) / CLIP_STD).transpose(2, 0, 1)[np.newaxis].astype(np.float32)


class TorchEncoders:
    """원래 방식(sentence-transformers + PyTorch). ONNX 파일이 없을 때와, ONNX 내보내기·비교에 쓴다."""

    name = "torch"

    def __init__(self, device="cpu"):
        from sentence_transformers import SentenceTransformer

        self.image_model = SentenceTransformer(IMAGE_MODEL, device=device)
        self.text_model = SentenceTransformer(TEXT_MODEL, device=device)

    def texts(self, texts):
        return self.text_model.encode(list(texts), convert_to_numpy=True, show_progress_bar=False)

    def image(self, image):
        return self.image_model.encode([image], convert_to_numpy=True, show_progress_bar=False)[0]


def onnx_suffix(variant):
    return f".{variant}" if variant else ""


def onnx_files_ready(model_dir, variant):
    suffix = onnx_suffix(variant)
    names = (f"image_encoder{suffix}.onnx", f"text_encoder{suffix}.onnx", "tokenizer.json")
    return all((Path(model_dir) / name).is_file() for name in names)


def pick_variant(model_dir=MODEL_DIR, variant=ONNX_VARIANT):
    """쓸 ONNX 파일 종류. 정해 줬으면 그것, 아니면 있는 파일 중 fp16 → fp32. 하나도 없으면 None."""
    for candidate in ([variant] if variant is not None else ["fp16", ""]):
        if onnx_files_ready(model_dir, candidate):
            return candidate
    return None


class OnnxEncoders:
    """onnxruntime 으로 돌린다(PyTorch 불필요). 파일은 export_onnx.py 가 models/ 에 만든다."""

    def __init__(self, model_dir=MODEL_DIR, variant=""):
        import onnxruntime
        from tokenizers import Tokenizer

        model_dir, suffix = Path(model_dir), onnx_suffix(variant)
        self.name = f"onnx{suffix}"
        providers = ["CPUExecutionProvider"]
        self._image = onnxruntime.InferenceSession(str(model_dir / f"image_encoder{suffix}.onnx"), providers=providers)
        self._text = onnxruntime.InferenceSession(str(model_dir / f"text_encoder{suffix}.onnx"), providers=providers)
        tokenizer = Tokenizer.from_file(str(model_dir / "tokenizer.json"))
        tokenizer.enable_truncation(max_length=MAX_TEXT_TOKENS)
        tokenizer.enable_padding(pad_id=tokenizer.token_to_id("[PAD]"), pad_token="[PAD]")
        self._tokenizer = tokenizer

    def texts(self, texts):
        encodings = self._tokenizer.encode_batch(list(texts))
        ids = np.array([encoding.ids for encoding in encodings], dtype=np.int64)
        mask = np.array([encoding.attention_mask for encoding in encodings], dtype=np.int64)
        return self._text.run(None, {"input_ids": ids, "attention_mask": mask})[0]

    def image(self, image):
        return self._image.run(None, {"pixel_values": clip_pixels(image)})[0][0]


def load_encoders(backend=BACKEND, device="cpu", model_dir=MODEL_DIR, variant=ONNX_VARIANT):
    """auto: models/ 에 ONNX 파일이 있으면 ONNX(fp16 먼저), 없으면 PyTorch."""
    if backend not in ("auto", "onnx", "torch"):
        raise ValueError(f"알 수 없는 backend: {backend}")
    chosen = pick_variant(model_dir, variant) if backend != "torch" else None
    if chosen is not None:
        return OnnxEncoders(model_dir, chosen)
    if backend == "onnx":
        raise FileNotFoundError(f"{model_dir} 에 ONNX 파일이 없습니다. python export_onnx.py 로 만들어 주세요")
    try:
        return TorchEncoders(device)
    except ImportError as error:
        raise FileNotFoundError(f"{model_dir} 에 ONNX 파일이 없고 PyTorch 도 없습니다. "
                                "ONNX 파일을 복사하거나 requirements-export.txt 로 설치해 주세요") from error


class TopicFitScorer:
    """모델을 한 번 읽고, 대조 문장 벡터를 미리 계산해 둔다."""

    model_name = MODEL_NAME

    def __init__(self, device="cpu", backend=BACKEND, model_dir=MODEL_DIR, variant=ONNX_VARIANT):
        self._encoders = load_encoders(backend, device, model_dir, variant)
        self.backend = self._encoders.name
        # fp16 은 점수가 아주 조금(최대 0.0005) 달라서 버전에 표시한다. fp32 ONNX 는 PyTorch 와 같은 점수
        self.version = f"{VERSION}-fp16" if self.backend == "onnx.fp16" else VERSION
        self._lock = threading.Lock()
        self._topics = {}
        self._distractors = {}
        for template in TEMPLATES:
            names = distractor_topics(template)
            vectors = [self._topic_vector_uncached(name, template) for name in names]
            self._distractors[template] = (names, np.stack(vectors))

    def _encode_texts(self, texts):
        with self._lock:
            return normalize(self._encoders.texts(texts))

    def _topic_vector_uncached(self, topic, template):
        return mean_vector(self._encode_texts(prompts(topic, template)))

    def topic_vector(self, topic, template):
        key = (topic.strip(), template)
        if key not in self._topics:
            if len(self._topics) > 5000:
                self._topics.clear()
            self._topics[key] = self._topic_vector_uncached(topic, template)
        return self._topics[key]

    def image_vector(self, image):
        with self._lock:
            return normalize(self._encoders.image(image))

    def score_vector(self, image_vector, topic, template):
        topic_vector = self.topic_vector(topic, template)
        names, vectors = self._distractors[template]
        distractors = vectors[keep_distractors(topic_text(topic, template), names)]
        fit, cosine = fit_score(image_vector, topic_vector, distractors)
        return {"fit": fit, "cosine": cosine}

    def score(self, image, topic, template):
        return self.score_vector(self.image_vector(image), topic, template)
