# 비전 서비스 — 사진이 오늘 주제에 맞는지 점수 계산 (B10 Python 부분)

계획: `docs/해야될거/2번_우준/B10-photo-topic-fit-ai.md` (Key Points 1~6). Spring 연동(Key Points 7~15)은 찬희.
사진과 주제를 받아 **점수(`fit`, 0~1)만** 돌려준다. 배지·문구 판정과 저장은 백엔드가 한다.

## 실행 (Windows, Python 3.12)

```bat
cd ml\vision-service
py -3.12 -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app:app --port 8001
```

- 처음 켤 때 모델 2개(약 1.1GB)를 Hugging Face 에서 받는다(`%USERPROFILE%\.cache\huggingface`). 그 뒤로는 `set HF_HUB_OFFLINE=1` 로 인터넷 없이 켤 수 있다.
- 켜는 데 수 초, 요청 한 번에 CPU 기준 약 0.36초(p50) · 0.53초(p95).

## API (백엔드와의 약속은 이것 하나)

`POST /v1/topic-fit` — `multipart/form-data`

| 필드 | 필수 | 설명 |
|---|---|---|
| `image` | 예 | 사진 파일 (jpg/png/webp/heic, ≤ 10MB). 휴대폰 회전 정보는 서비스가 반영한다 |
| `topic` | 예 | 주제 명사구, 1~60자. `PHOTO_NOW` 는 `params.subject`(예: `창밖 풍경`), `PHOTO_COLOR` 는 `params.color`(예: `파란색` 또는 `파랑`) |
| `template` | 아니오 | `PHOTO_NOW`(기본) 또는 `PHOTO_COLOR` |

```json
// 200
{ "fit": 0.191509, "cosine": 0.271159, "model": "clip-ViT-B-32+multilingual-v1", "version": "1" }
```

| 상태 | `code` | 경우 |
|---|---|---|
| 400 | `INVALID_TOPIC` | topic 이 비었거나 60자 초과 |
| 400 | `UNSUPPORTED_TEMPLATE` | template 이 두 가지가 아님 |
| 400 | `INVALID_IMAGE` | 사진을 읽을 수 없음 |
| 400 | `VALIDATION_ERROR` | image 나 topic 이 없음 |
| 413 | `FILE_TOO_LARGE` | 10MB 초과 |

`GET /healthz` → `{ "status": "ok", "model": "...", "version": "1" }`

```bash
curl -F "image=@photo.jpg" -F "topic=창밖 풍경" http://localhost:8001/v1/topic-fit
```

> Windows Git Bash 의 `curl` 은 명령줄의 한글을 깨뜨려 보낸다(모든 주제가 같은 점수로 나옴). 한글 주제는 UTF-8 파일로 보내거나(`-F "topic=<topic.txt"`) Postman·Java·Python 클라이언트로 보낸다.

## 점수와 임계값 (`EVAL.md`)

- `fit` = 주제 1개 + 대조 주제(일상 72개 `distractors.ko.txt`, 또는 다른 색 10개) 중 **이 주제일 확률**. 관련 없을 때 기준값이 일상 약 0.014, 색 약 0.09 라서 **미션 틀마다 임계값이 다르다** → 백엔드 설정도 틀별로 둔다.
- 2026-10-12 평가(사진 42장, 짝 216개)의 추천값:

| 틀 | 찰떡상 (정밀도 ≥ 95%) | 칭찬 한 줄 (정밀도 ≥ 85%) | ROC-AUC |
|---|---|---|---|
| `PHOTO_NOW` | 0.020 | 0.015 | 0.969 |
| `PHOTO_COLOR` | 0.177 | 0.106 | 0.936 |

사진이 늘면 `python eval.py` 로 다시 계산한다(아래).

## 평가 (`eval.py`)

`ml/eval-data/topic-fit/<사람>/` 에 사진과 `<사람>.csv`(`파일,주제,색,메모`, 주제는 `|` 로 여러 개, 색은 `빨강·주황·노랑·초록·파랑·보라·분홍·흰색·검정·갈색·회색`)를 넣고:

```bat
python eval.py                               :: 모델을 직접 읽어 평가
python eval.py --url http://localhost:8001   :: 떠 있는 서비스로 평가 (지연 시간 포함)
```

- 사진마다 "맞음" 짝(이름표)과 같은 수의 "안 맞음" 짝(관련 없는 주제·색)을 자동으로 만든다. 사진을 더해도 기존 짝은 바뀌지 않는다.
- 결과: `EVAL.md`(집계만, 깃에 올림) + `ml/eval-data/topic-fit/_results/`(사진 이름 포함, 깃 제외).

## 테스트

```bat
python -m pytest                                        :: 모델 없이 (API·점수 계산·평가 도구)
set HAWE_VISION_MODEL_TESTS=1 && python -m pytest       :: 실제 모델 포함 (모델을 받아 둔 경우)
```

## 개인정보·라이선스

- 사진을 디스크에 저장하지 않고, 로그에는 요청 id·상태·지연만 남긴다(사진·주제 없음). 외부 AI API 를 부르지 않는다.
- 평가 사진은 `ml/eval-data/`(깃 제외)에만 둔다.
- 모델: 텍스트 `sentence-transformers/clip-ViT-B-32-multilingual-v1`(Apache-2.0), 이미지 `sentence-transformers/clip-ViT-B-32`(OpenAI CLIP 기반, CLIP 은 MIT. 모델 카드에 라이선스 표기가 없어 운영 배포 전 확인).
