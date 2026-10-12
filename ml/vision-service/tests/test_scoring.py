import io

import numpy as np
import pytest
from PIL import Image

import scoring


def unit(*values):
    vector = np.asarray(values, dtype=np.float32)
    return vector / np.linalg.norm(vector)


def test_particle_follows_final_consonant():
    assert scoring.subject_particle("하늘") == "이"
    assert scoring.subject_particle("바다") == "가"
    assert scoring.subject_particle("컵") == "이"
    assert scoring.subject_particle("coffee") == "가"


def test_prompts_for_topic_and_color():
    assert scoring.prompts(" 창밖 풍경 ", "PHOTO_NOW") == ["창밖 풍경"]
    assert scoring.prompts("파랑", "PHOTO_COLOR") == ["파란색 물건", "파란색 물건 사진", "파란색이 눈에 띄는 사진"]
    assert scoring.prompts("파란색", "PHOTO_COLOR")[0] == "파란색 물건"
    assert scoring.topic_text("검정", "PHOTO_COLOR") == "검은색"


def test_distractor_lists():
    everyday = scoring.distractor_topics("PHOTO_NOW")
    assert len(everyday) >= 60 and len(set(everyday)) == len(everyday)
    assert not any(item.startswith("#") for item in everyday)
    assert len(scoring.distractor_topics("PHOTO_COLOR")) == 11


def test_fit_is_probability_and_rises_with_match():
    distractors = np.stack([unit(0, 1, 0), unit(0, 0, 1)])
    topic = unit(1, 0, 0)
    close, close_cosine = scoring.fit_score(unit(1, 0.1, 0), topic, distractors)
    far, far_cosine = scoring.fit_score(unit(0.1, 1, 0), topic, distractors)
    assert 0.0 <= far < close <= 1.0
    assert close_cosine > far_cosine


def test_keep_distractors_drops_same_or_contained_words():
    names = ["커피", "컵", "책상", "하늘", "커피 컵"]
    assert scoring.keep_distractors("커피 컵", names) == [2, 3]
    assert scoring.keep_distractors("책상 위", names) == [0, 1, 3, 4]
    assert scoring.keep_distractors("자전거", names) == [0, 1, 2, 3, 4]
    colors = scoring.distractor_topics("PHOTO_COLOR")
    assert len(scoring.keep_distractors(scoring.topic_text("파랑", "PHOTO_COLOR"), colors)) == 10


def test_decode_image_applies_rotation_and_rejects_garbage():
    image = Image.new("RGB", (40, 20), "red")
    exif = Image.Exif()
    exif[0x0112] = 6  # 오른쪽으로 90도 돌려 보여야 함
    buffer = io.BytesIO()
    image.save(buffer, "JPEG", exif=exif)
    decoded = scoring.decode_image(buffer.getvalue())
    assert decoded.size == (20, 40) and decoded.mode == "RGB"
    with pytest.raises(ValueError):
        scoring.decode_image(b"not an image")
