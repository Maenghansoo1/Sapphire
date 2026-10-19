# 비전 서비스 — 사진이 오늘 주제에 맞는지 점수 계산 (B10 Python 부분)

계획: `docs/해야될거/2번_우준/B10-photo-topic-fit-ai.md` (Key Points 1~6). Spring 연동(Key Points 7~15)은 찬희.
사진과 주제를 받아 **점수(`fit`, 0~1)만** 돌려준다. 배지·문구 판정과 저장은 백엔드가 한다.

## 실행 (Windows, Python 3.12)

서비스는 `models/` 의 **ONNX 파일**로 점수를 낸다. PyTorch 가 필요 없어서 설치가 가볍다(아래 "ONNX").

```bat
cd ml\vision-service
py -3.12 -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app:app --port 8001
```

- `models/` 에 ONNX 파일이 있어야 한다. 처음 한 번 `export_onnx.py` 로 만들거나, 만들어 둔 파일을 복사해 온다(아래).
- 켜는 데 약 2초, 요청 한 번에 CPU 기준 약 0.4초.
- `models/` 에 ONNX 파일이 없으면 예전처럼 sentence-transformers(PyTorch)로 돌린다. 이때는 `pip install -r requirements-export.txt` 가 필요하고, 처음 켤 때 모델 2개(약 1.1GB)를 Hugging Face 에서 받는다.

## ONNX (2026-10-19)

PyTorch 없이 `onnxruntime` 으로 돌리려고 두 인코더(사진 CLIP, 한국어 글)를 ONNX 파일로 바꿔 둔다. 사진 전처리(짧은 변 224 로 줄이고 가운데 자르기)는 `scoring.clip_pixels` 가 transformers 와 똑같이 한다.

**만들기** (PyTorch 가 있는 환경에서 한 번, 몇 분):

```bat
pip install -r requirements-export.txt
python export_onnx.py
```

`models/`(깃 제외)에 생기는 파일:

| 파일 | 크기 | 설명 |
|---|---|---|
| `image_encoder.fp16.onnx` | 176MB | 사진 → 벡터. **서비스가 쓰는 파일** |
| `text_encoder.fp16.onnx` | 270MB | 글 → 벡터. **서비스가 쓰는 파일** |
| `tokenizer.json` | 3MB | 글 토크나이저. **서비스가 쓰는 파일** |
| `image_encoder.onnx`, `text_encoder.onnx` | 352MB, 541MB | fp32 원본(fp16 을 만드는 재료, 비교용) |
| `manifest.json` | | 원본 모델, 해시, PyTorch 와의 차이 |

**정확도**: fp16 은 가중치만 절반 크기로 저장하고 계산 결과는 거의 같다. 팀 평가 사진 5,140쌍(`eval.py --backend onnx`)에서 PyTorch 대비 점수 차이 최대 0.00045, **배지·한마디 판정이 바뀐 쌍 0**, ROC-AUC 와 추천 임계값도 같다. int8 은 크기가 1/4 이지만 점수 차이가 최대 0.07~0.13 이라 쓰지 않는다.

**크기 비교**: 예전(PyTorch) 프로그램 약 1.2GB + 모델 약 1.1GB → 지금 프로그램 약 230MB + 모델 449MB.

**다른 서버(AWS)에 올리기**: `requirements.txt` 로 설치하고, `models/` 에 위 표의 "서비스가 쓰는 파일" 3개만 복사하면 된다(약 450MB). `GET /healthz` 의 `backend` 가 `onnx.fp16` 이면 ONNX 로 돌고 있다.

| 환경변수 | 기본 | 설명 |
|---|---|---|
| `HAWE_VISION_MODEL_DIR` | `models/` | ONNX 파일 위치 |
| `HAWE_VISION_ONNX_VARIANT` | 비움 | `fp16` 또는 빈 값(fp32). 비우면 있는 파일 중 fp16 → fp32 순서 |
| `HAWE_VISION_BACKEND` | `auto` | `auto`(ONNX 있으면 ONNX), `onnx`, `torch` |

응답과 `/healthz` 의 `version` 은 fp16 일 때 `1-fp16`, fp32·PyTorch 일 때 `1` 이다(백엔드는 기록만 한다).

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

`GET /healthz` → `{ "status": "ok", "model": "...", "version": "1-fp16", "backend": "onnx.fp16" }`

```bash
curl -F "image=@photo.jpg" -F "topic=창밖 풍경" http://localhost:8001/v1/topic-fit
```

> Windows Git Bash 의 `curl` 은 명령줄의 한글을 깨뜨려 보낸다(모든 주제가 같은 점수로 나옴). 한글 주제는 UTF-8 파일로 보내거나(`-F "topic=<topic.txt"`) Postman·Java·Python 클라이언트로 보낸다.

## 점수와 임계값 (`EVAL.md`)

- `fit` = 주제 1개 + 대조 주제(일상 72개 `distractors.ko.txt`, 또는 다른 색 10개) 중 **이 주제일 확률**. 관련 없을 때 기준값이 일상 약 0.014, 색 약 0.09 라서 **미션 틀마다 임계값이 다르다** → 백엔드 설정도 틀별로 둔다.
- 2026-10-13 평가(사진 177장, 팀원 2명, 짝 1,028개, "안 맞음" 짝 5번 뽑아 합침)의 추천값 — **백엔드 설정은 이 값을 쓴다**:

| 틀 | 찰떡상 (정밀도 ≥ 95%) | 칭찬 한 줄 (정밀도 ≥ 85%) | ROC-AUC |
|---|---|---|---|
| `PHOTO_NOW` | **0.024** (0.022~0.025) | **0.017** (0.016~0.017) | 0.958 |
| `PHOTO_COLOR` | **0.132** (0.116~0.148) | **0.104** (0.095~0.112) | 0.959 |

괄호는 "안 맞음" 짝을 다르게 뽑았을 때의 범위. 사진이 늘면 `python eval.py` 로 다시 계산한다(아래).

## 평가 (`eval.py`)

`ml/eval-data/topic-fit/<사람>/` 에 사진과 `<사람>.csv`(`파일,주제,색,메모`, 주제는 `|` 로 여러 개, 색은 `빨강·주황·노랑·초록·파랑·보라·분홍·흰색·검정·갈색·회색`)를 넣고:

```bat
python eval.py                               :: 모델을 직접 읽어 평가 (--backend onnx|torch 로 방식 지정)
python eval.py --url http://localhost:8001   :: + 떠 있는 서비스로 60번 호출해 지연 시간·점수 일치 확인
```

- 사진마다 "맞음" 짝(이름표)과 같은 수의 "안 맞음" 짝(관련 없는 주제·색)을 자동으로 만든다. "안 맞음" 짝은 5번(`--repeats`) 다르게 뽑아 합친다.
- 짝은 사진 내용(해시)으로 정해져서, 사진을 더하거나 폴더·파일 이름을 바꿔도 기존 짝은 바뀌지 않는다.
- 메모가 `제외` 로 시작하는 줄(얼굴·화면 캡처 등)은 건너뛴다.
- 결과: `EVAL.md`(집계만, 깃에 올림) + `ml/eval-data/topic-fit/_results/`(사진 이름 포함, 깃 제외).

## 테스트

```bat
python -m pytest                                        :: 모델 없이 (API·점수 계산·평가 도구)
set HAWE_VISION_MODEL_TESTS=1 && python -m pytest       :: 실제 모델 포함 (ONNX 파일, PyTorch 가 있으면 둘의 점수 비교까지)
```

## 개인정보·라이선스

- 사진을 디스크에 저장하지 않고, 로그에는 요청 id·상태·지연만 남긴다(사진·주제 없음). 외부 AI API 를 부르지 않는다.
- 평가 사진은 `ml/eval-data/`(깃 제외)에만 둔다.
- 모델: 텍스트 `sentence-transformers/clip-ViT-B-32-multilingual-v1`(Apache-2.0), 이미지 `sentence-transformers/clip-ViT-B-32`(OpenAI CLIP 기반, CLIP 은 MIT. 모델 카드에 라이선스 표기가 없어 운영 배포 전 확인).
