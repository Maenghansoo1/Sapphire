"""사진-주제 적합도 점수. 다국어 CLIP: 이미지 clip-ViT-B-32 + 텍스트 clip-ViT-B-32-multilingual-v1 (한국어 그대로 입력).

원시 코사인은 값 범위가 좁아 해석이 어렵다. 그래서 주제 문장 T 를 일상 사진 주제 목록 D 와 함께 비교해
softmax(LOGIT_SCALE × cos(사진, [T ∪ D]))[T] 를 fit(0~1)으로 쓴다 = "다른 흔한 주제보다 이 주제에 얼마나 더 가까운가".

방식은 팀 사진 42장 비교 실험(2026-10-12, EVAL.md)으로 골랐다:
- 이 모델의 한국어 텍스트 벡터는 서로 너무 가깝다(관계없는 단어끼리도 코사인 0.9 이상) → 텍스트 유사도로
  비슷한 대조어를 거르면 대조어가 거의 다 빠져 점수가 항상 1 이 된다. 그래서 글자 기준으로만 거른다(keep_distractors).
- PHOTO_NOW 는 주제 단어만(AUC 0.968, "~ 사진" 문장을 섞으면 0.947), PHOTO_COLOR 는 문장 3개 평균(0.936, 단어만 0.911).
- softmax 배율 50 (100: 0.957/0.933, 20: 0.967/0.932).
"""
import io
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


class TopicFitScorer:
    """모델을 한 번 읽고, 대조 문장 벡터를 미리 계산해 둔다."""

    model_name = MODEL_NAME

    def __init__(self, device="cpu"):
        from sentence_transformers import SentenceTransformer

        self._image_model = SentenceTransformer(IMAGE_MODEL, device=device)
        self._text_model = SentenceTransformer(TEXT_MODEL, device=device)
        self._lock = threading.Lock()
        self._topics = {}
        self._distractors = {}
        for template in TEMPLATES:
            names = distractor_topics(template)
            vectors = [self._topic_vector_uncached(name, template) for name in names]
            self._distractors[template] = (names, np.stack(vectors))

    def _encode_texts(self, texts):
        with self._lock:
            return normalize(self._text_model.encode(texts, convert_to_numpy=True, show_progress_bar=False))

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
            return normalize(self._image_model.encode([image], convert_to_numpy=True, show_progress_bar=False))[0]

    def score_vector(self, image_vector, topic, template):
        topic_vector = self.topic_vector(topic, template)
        names, vectors = self._distractors[template]
        distractors = vectors[keep_distractors(topic_text(topic, template), names)]
        fit, cosine = fit_score(image_vector, topic_vector, distractors)
        return {"fit": fit, "cosine": cosine}

    def score(self, image, topic, template):
        return self.score_vector(self.image_vector(image), topic, template)
