"""두 인코더를 ONNX 로 내보낸다. 비전 서비스를 PyTorch 없이(onnxruntime 으로) 돌리기 위해서다.

PyTorch·sentence-transformers 가 있는 환경에서 한 번만 실행한다(requirements-export.txt).

  python export_onnx.py    # models/ 에 fp32·fp16 ONNX + 토크나이저를 만들고 PyTorch 와 결과를 비교

만드는 파일(models/, 크기가 커서 git 에 올리지 않음):
  image_encoder.onnx        pixel_values (n,3,224,224) float32 → image_embeds (n,512)          352MB
  text_encoder.onnx         input_ids, attention_mask (n,길이) int64 → sentence_embedding (n,512) 541MB
  *.fp16.onnx               위와 같은데 가중치를 fp16 으로 저장(입출력은 float32 그대로). 서비스는 이걸 먼저 쓴다  176MB·270MB
  tokenizer.json            텍스트 모델 토크나이저 (tokenizers 로 읽는다)
  manifest.json             원본 모델, 만든 시각, 파일 크기·해시, PyTorch 와의 차이

정확도(2026-10-19, 팀 평가 사진 5,140쌍 eval.py): fp32 는 PyTorch 와 점수 차이 최대 0.000001, fp16 은 0.00045,
배지·한마디 판정이 바뀐 쌍 0. int8 은 크기가 1/4 이지만 점수 차이가 최대 0.07~0.13 이라(배지 기준 0.017~0.132) 쓰지 않는다.
"""
import argparse
import hashlib
import json
import sys
from datetime import datetime
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import scoring  # noqa: E402

OPSET = 17
EVAL_PHOTOS = HERE.parent / "eval-data" / "topic-fit"


def image_module(clip):
    import torch

    class ImageEncoder(torch.nn.Module):
        """CLIP 사진 인코더 + 투영. transformers CLIPModel.get_image_features 와 같은 계산."""

        def __init__(self):
            super().__init__()
            self.vision = clip.vision_model
            self.projection = clip.visual_projection

        def forward(self, pixel_values):
            return self.projection(self.vision(pixel_values=pixel_values).pooler_output)

    return ImageEncoder().eval()


def text_module(bert, dense):
    import torch

    class TextEncoder(torch.nn.Module):
        """Transformer → 평균 풀링(패딩 제외) → Dense(768→512, 편향 없음). sentence-transformers 와 같은 순서."""

        def __init__(self):
            super().__init__()
            self.bert = bert
            self.dense = dense

        def forward(self, input_ids, attention_mask):
            hidden = self.bert(input_ids=input_ids, attention_mask=attention_mask).last_hidden_state
            mask = attention_mask.unsqueeze(-1).to(hidden.dtype)
            pooled = (hidden * mask).sum(dim=1) / mask.sum(dim=1).clamp(min=1e-9)
            return self.dense(pooled)

    return TextEncoder().eval()


def export(module, inputs, path, input_names, output_names, dynamic_axes):
    import torch

    with torch.no_grad():
        torch.onnx.export(module, inputs, str(path), input_names=input_names, output_names=output_names,
                          dynamic_axes=dynamic_axes, opset_version=OPSET, dynamo=False)


def sample_images():
    """비교용 사진: 크기·비율이 다른 합성 사진 + 있으면 팀 평가 사진 일부(개인 사진이라 결과 숫자만 남긴다)."""
    rng = np.random.default_rng(7)
    images = []
    for size in ((640, 480), (480, 640), (301, 300), (225, 1000), (1000, 225), (224, 224), (100, 50), (4032, 3024)):
        pixels = rng.integers(0, 256, (size[1], size[0], 3), dtype=np.uint8)
        images.append(Image.fromarray(pixels))
    for color in ((220, 30, 30), (30, 60, 220), (240, 220, 40)):
        image = Image.new("RGB", (400, 300), "white")
        image.paste(Image.new("RGB", (200, 150), color), (100, 75))
        images.append(image)
    photos = sorted(p for p in EVAL_PHOTOS.glob("*/*") if p.suffix.lower() in (".jpg", ".jpeg", ".png", ".heic", ".webp"))
    for path in photos[:24]:
        try:
            images.append(scoring.decode_image(path.read_bytes()))
        except ValueError:
            pass
    return images


def sample_texts():
    texts = []
    for template in scoring.TEMPLATES:
        for name in scoring.distractor_topics(template):
            texts += scoring.prompts(name, template)
    texts += ["손 닿는 곳에서 제일 만만한 물건", "냉장고 안 음식", "지금 책상 위", "창밖 풍경", "a" * 300]
    return list(dict.fromkeys(texts))


def topic_scores(text_vectors, image_vectors, texts, template):
    """사진마다 주제 12개에 대한 (fit, cosine). 서비스와 같은 방식(대조 주제 포함)으로 계산한다."""
    lookup = dict(zip(texts, text_vectors))
    names = scoring.distractor_topics(template)
    topics = {name: scoring.mean_vector([lookup[p] for p in scoring.prompts(name, template)]) for name in names}
    distractors = np.stack([topics[name] for name in names])
    scores = []
    for image_vector in image_vectors:
        for name in names[:12]:
            keep = scoring.keep_distractors(scoring.topic_text(name, template), names)
            scores.append(scoring.fit_score(image_vector, topics[name], distractors[keep]))
    return scores


def compare(reference, candidate, images, texts):
    """두 인코더의 벡터·점수 차이."""
    ref_text = scoring.normalize(reference.texts(texts))
    cand_text = scoring.normalize(candidate.texts(texts))
    ref_images = [scoring.normalize(reference.image(image)) for image in images]
    cand_images = [scoring.normalize(candidate.image(image)) for image in images]
    ref_scores, cand_scores = [], []
    for template in scoring.TEMPLATES:
        ref_scores += topic_scores(ref_text, ref_images, texts, template)
        cand_scores += topic_scores(cand_text, cand_images, texts, template)
    fit_diff = [abs(a[0] - b[0]) for a, b in zip(ref_scores, cand_scores)]
    cos_diff = [abs(a[1] - b[1]) for a, b in zip(ref_scores, cand_scores)]
    return {
        "text_vector_min_cosine": float(min((ref_text * cand_text).sum(axis=1))),
        "image_vector_min_cosine": float(min(float(a @ b) for a, b in zip(ref_images, cand_images))),
        "fit_max_abs_diff": float(max(fit_diff)),
        "fit_p99_abs_diff": float(np.percentile(fit_diff, 99)),
        "cosine_max_abs_diff": float(max(cos_diff)),
        "pairs": len(fit_diff),
        "images": len(images),
        "texts": len(texts),
    }

def file_info(path):
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    return {"bytes": path.stat().st_size, "sha256": digest}


def main(argv=None):
    parser = argparse.ArgumentParser(description="비전 인코더 ONNX 내보내기")
    parser.add_argument("--out", default=str(scoring.MODEL_DIR))
    args = parser.parse_args(argv)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    import torch

    torch.manual_seed(0)
    reference = scoring.TorchEncoders()
    clip = reference.image_model[0].auto_model
    text_parts = reference.text_model
    dense = text_parts[2]
    if not isinstance(getattr(dense, "activation_function", torch.nn.Identity()), torch.nn.Identity):
        raise SystemExit("Dense 활성화 함수가 Identity 가 아니에요. 내보내기 코드를 확인해 주세요.")

    print("사진 인코더 내보내는 중...")
    export(image_module(clip), (torch.zeros(1, 3, scoring.CLIP_SIZE, scoring.CLIP_SIZE),), out / "image_encoder.onnx",
           ["pixel_values"], ["image_embeds"], {"pixel_values": {0: "batch"}, "image_embeds": {0: "batch"}})
    print("글 인코더 내보내는 중...")
    text_parts[0].tokenizer.backend_tokenizer.save(str(out / "tokenizer.json"))
    sample = text_parts[0].tokenizer(["안녕하세요", "냉장고에서 제일 오래 버틴 음식"], padding=True, return_tensors="pt")
    export(text_module(text_parts[0].auto_model, dense.linear), (sample["input_ids"], sample["attention_mask"]),
           out / "text_encoder.onnx", ["input_ids", "attention_mask"], ["sentence_embedding"],
           {"input_ids": {0: "batch", 1: "tokens"}, "attention_mask": {0: "batch", 1: "tokens"},
            "sentence_embedding": {0: "batch"}})

    import onnx
    from onnxruntime.transformers.float16 import convert_float_to_float16

    for name in ("image_encoder", "text_encoder"):
        print(f"{name} fp16 으로 줄이는 중...")
        model = convert_float_to_float16(onnx.load(str(out / f"{name}.onnx")), keep_io_types=True)
        onnx.save(model, str(out / f"{name}.fp16.onnx"))
    variants = ["", "fp16"]

    images, texts = sample_images(), sample_texts()
    pixel_diff = max(float(np.abs(scoring.clip_pixels(image)
                                  - reference.image_model[0].preprocess([image])["pixel_values"].numpy()).max())
                     for image in images)
    checks = {"pixel_max_abs_diff": pixel_diff}
    for variant in variants:
        print(f"PyTorch 와 비교 중 ({variant or 'fp32'})...")
        checks[variant or "fp32"] = compare(reference, scoring.OnnxEncoders(out, variant), images, texts)
    manifest = {
        "source": {"image": scoring.IMAGE_MODEL, "text": scoring.TEXT_MODEL},
        "model_name": scoring.MODEL_NAME,
        "created": datetime.now().isoformat(timespec="seconds"),
        "opset": OPSET,
        "files": {path.name: file_info(path) for path in sorted(out.glob("*")) if path.name != "manifest.json"},
        "check": checks,
    }
    (out / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(manifest["check"], ensure_ascii=False, indent=2))
    for name, info in manifest["files"].items():
        print(f"{name}: {info['bytes'] / 1e6:.1f} MB")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())