# M1. 직접 학습시킨 그림 인식 모델 ("AI가 맞혀볼게요") — Quick, Draw! CNN 학습·경량화·내보내기

> 범위: 신규 `ml/quickdraw/`(Python 학습 파이프라인) + 문서. 앱 내 추론·게임 UI 는 A8(Android, TFLite)·A5(iOS, Core ML). **서버 변경 없음**(추론은 전부 기기 안에서 실행, 그림 데이터를 서버로 보내지 않는다). 알림·배포 무관. 사용자 요구: 구글 Quick, Draw! 데이터셋으로 **CNN 을 직접 학습**시키고 **경량화**해서 **Core ML(iOS)/TFLite(Android)** 로 기기 안에서 돌린다("API 를 가져다 쓴 게 아니라 직접 학습·경량화"가 포트폴리오 핵심). 친구 그림을 AI가 맞히는 게임 요소.

## Goal

1. 앱의 그림 미션에서 쓰이는 주제와 맞는 **약 50개 범주**의 낙서를 분류하는 소형 CNN 을 학습한다(목표: 테스트셋 top-1 ≥ 75%, top-3 ≥ 90%, 모델 ≤ 1 MB(양자화 후), 기기 추론 ≤ 30 ms).
2. **TFLite**(Android)와 **Core ML**(iOS) 모델 + 범주 라벨(한국어/영어) 파일을 산출한다.
3. **학습-앱 전처리 일치**를 보장하는 공통 명세와 골든 테스트 벡터를 만든다(앱에서 그린 그림이 학습 데이터와 같은 방식으로 28×28 입력이 되도록).
4. 학습·평가·경량화 과정을 재현 가능하게 문서화한다(`ml/quickdraw/REPORT.md`: 데이터, 구조, 학습 곡선, 정확도, 양자화 전후 비교, 크기·지연).

## Findings

- 앱 그림 데이터 출처: Android `feature/mission/DrawingModel.kt`(`DrawStroke(points: List<StrokePoint>, color, widthDp, isEraser)`, 펜/지우개 도구, 굵기 3단계, 색 7종) + `DrawingRenderer.kt`(`drawStrokes`, `renderDrawingPng(strokes, widthDp, heightDp, scale)`), 제출은 PNG(`SubmissionContent.Image(bytes, "png")`). iOS 는 PencilKit(`PencilCanvas`, `DrawMissionView.swift`). → **획 단위 좌표가 아니라 래스터 PNG 가 서버에 저장**되고, 결과 화면에서는 PNG 만 있다. 지우개 획이 있어 획 좌표만으로는 최종 그림을 재현할 수 없다.
- 그림 미션 주제: `DRAW_SKETCH{subject, seconds}`, `DRAW_A_TO_B{a, b}`(주제는 임시 생성기에서 `얼굴, 표정, 머리 모양, 구름, 풍선, 컵, 화분, 책, 집` 등; AI 생성(B8)에서는 자유 문장). 대상 지정 시 `[대상]`을 그리는 틀도 있음(사람 그리기 — 범주 밖).
- 데이터셋: Google **Quick, Draw!**(CC BY 4.0) — 범주 345개, 범주당 수만~수십만 샘플. 형식: ① `simplified` ndjson(획 좌표 0~255 정규화·RDP 단순화, `gs://quickdraw_dataset/full/simplified/<범주>.ndjson`), ② `numpy_bitmap` 28×28(`gs://quickdraw_dataset/full/numpy_bitmap/<범주>.npy`). **CC BY 4.0 이므로 앱에 출처 표기가 필요**(앱 라이선스 화면에 문구 추가 — A8/A5 에서).
- 저장소에는 파이썬/ML 코드가 아직 없다(`ml/` 디렉터리 없음). 모델 산출물은 앱 리소스로 복사한다(Android `assets/`, iOS Xcode 리소스).
- 앱 최소 사양: Android minSdk 26, iOS 17.0 → TFLite(CPU/XNNPACK) / Core ML(Neural Engine) 모두 충분.

## Root Cause / Key Implementation Points

### 1. 전처리 명세 (학습과 앱이 공유하는 계약 — `ml/quickdraw/PREPROCESS_SPEC.md`)
입력은 **"잉크 마스크"**(그림이 있는 곳 = 1, 없는 곳 = 0, 해상도 자유)에서 시작한다.
1. 마스크의 잉크 경계 상자(bbox)를 구한다. 잉크 픽셀이 `MIN_INK` 개 미만이면 "너무 비어 있음"으로 추론을 건너뛴다(앱이 안내 표시).
2. bbox 를 **종횡비를 유지한 채 20×20 안에 맞게** 면적 평균(area) 보간으로 축소/확대한다(긴 변 = 20).
3. 결과를 28×28 캔버스 **중앙**에 배치(주변 4px 여백).
4. 값은 `0.0~1.0` 실수(잉크 = 1.0), 필요 시 가우시안 블러 σ=0.5 로 앱·학습 동일하게 부드럽게(선택 — 구현 시 한 쪽을 택해 명세에 고정).
5. 텐서 모양 `[1, 28, 28, 1]`(NHWC, TFLite) / Core ML 은 같은 의미의 입력.
- **앱 입력 생성**: 앱에서 그린 직후에는 캔버스를 투명 배경에 **검정 잉크로** 렌더(지우개 적용 후) → 알파/불투명도를 잉크 마스크로 사용. 결과 화면의 PNG(흰 배경 + 색 잉크)는 "흰색과의 거리 > 임계"인 픽셀을 잉크로 삼는다(팔레트에 연한 노랑 `0xFFF2D74E` 가 있으므로 임계는 낮게, 노란 잉크가 사라지지 않는지 샘플 테스트).
- **학습 데이터 변환**: `simplified` ndjson 의 획을 **고해상도(예: 256×256) 캔버스에 굵기 무작위(8~20 px, 라운드 캡)로 그린 마스크**로 만든 뒤 위 1~4단계를 동일하게 적용 → 앱 그림의 굵기 변화에 대한 강건성과 학습-앱 일치를 동시에 얻는다. (`numpy_bitmap` 은 전처리 방식이 달라 도메인 격차가 커서 **사용하지 않는다**.)
- **골든 벡터**: `ml/quickdraw/golden/` 에 입력 마스크(PNG 또는 JSON)와 기대 28×28 출력(JSON) 10쌍. 앱(Kotlin/Swift) 단위 테스트가 같은 벡터를 읽어 오차 ≤ 1e-3(보간 구현 차이 허용치는 구현 시 정하되 최대 절대오차 ≤ 0.02)임을 검증한다.

### 2. 범주 선정 (`ml/quickdraw/categories.json`)
- 약 50개: (a) 앱 주제와 겹치는 것(`face`, `cloud`, `cup`, `houseplant`, `book`, `house`, `hot air balloon` 등) (b) 쉽고 재미있게 그릴 수 있고 서로 혼동이 적은 것(`cat`, `dog`, `sun`, `moon`, `star`, `tree`, `flower`, `car`, `bicycle`, `fish`, `apple`, `banana`, `pizza`, `umbrella`, `key`, `clock`, `airplane`, `bird`, `butterfly`, `guitar`, `ice cream`, `lightning`, `mushroom`, `rainbow`, `shoe`, `snowman`, `table`, `tooth`, `train`, `t-shirt`, `wine glass` … ). **정확한 영문 범주명은 데이터셋의 공식 범주 목록(`categories.txt`)에 존재하는 철자로 맞춘다**(구현 시 대조·교정).
- 각 항목: `{ "id": 0, "quickdraw": "cat", "ko": "고양이", "aliases_ko": ["야옹이"] }`. 모델 출력 순서 = 이 파일의 `id` 순서(앱 라벨 파일 `labels_ko.txt` 와 일치).
- 확정 전 사용자에게 보여 줄 후보 목록(임시)을 `ml/quickdraw/categories.proposed.md` 로 남기고 보고서에 포함 — 범주 변경은 재학습이 필요하다.

### 3. 학습 (`ml/quickdraw/train.py`)
- 데이터: 범주당 `N_PER_CLASS = 30,000`(학습 24k / 검증 3k / 테스트 3k), 시드 고정, 중복 제거, `recognized == true` 샘플만 사용(데이터셋의 인식 성공 플래그).
- 증강(학습 시): 작은 회전(±10°)·이동·스케일(±10%)·획 굵기 변화(고해상도 래스터 단계에서) — 앱 입력 분포 흡수.
- 구조(소형 CNN): `Conv(32,3)-BN-ReLU → Conv(32,3)-BN-ReLU → MaxPool → Conv(64,3)-BN-ReLU → Conv(64,3)-BN-ReLU → MaxPool → Conv(128,3)-BN-ReLU → GlobalAvgPool → Dropout(0.3) → Dense(num_classes, softmax)`. 파라미터 ≈ 200~300k. AdamW, 라벨 스무딩 0.05, 코사인 학습률 스케줄, 조기 종료.
- 프레임워크: **TensorFlow/Keras**(TFLite 변환 경로가 가장 단순). Core ML 변환은 `coremltools`(Keras/SavedModel 경로)를 시도하고, **버전 호환 문제로 막히면 PyTorch 학습으로 전환**(`coremltools.convert(torch.jit.trace)` 는 안정적, TFLite 는 `ai-edge-torch` 또는 ONNX→TFLite 경로) — 선택한 경로와 **정확한 라이브러리 버전**을 `requirements.txt` 와 REPORT.md 에 고정. GPU 가 없으면 Colab 사용 가능(코드는 로컬 CPU 에서도 소규모로 도는 `--smoke` 모드 제공).
- 재현성: 시드·데이터 샘플 목록(범주별 사용 라인 범위)을 저장, `train.py --config config.yaml`.

### 4. 경량화·내보내기 (`export.py`)
- TFLite: ① float32 ② **float16 양자화** ③ **int8 전체 정수 양자화**(대표 데이터셋 500장으로 보정). 각각 정확도 하락·크기·지연(데스크톱 TFLite 인터프리터 + 가능하면 실기기) 비교표를 REPORT.md 에 기록, 정확도 하락 ≤ 1%p 인 가장 작은 모델을 앱용으로 선택.
- Core ML: `.mlpackage`(float16 가중치), 입력 이름/모양 고정, 출력은 확률 벡터. 라벨은 별도 파일(앱에서 매핑).
- 산출물 디렉터리 `ml/quickdraw/export/`: `quickdraw_cnn.tflite`, `QuickDrawCNN.mlpackage`, `labels_ko.txt`, `labels_en.txt`, `model_card.md`(데이터 출처·라이선스(CC BY 4.0)·범주·정확도·한계·버전). 모델 파일은 크기가 작으므로 저장소에 커밋(≤ 수 MB). 학습 원본 데이터·체크포인트는 커밋하지 않는다(`.gitignore`).

### 5. 평가 (`evaluate.py`)
- 분리된 테스트셋에서 top-1/top-3 정확도, 범주별 정밀도/재현율, 혼동행렬(가장 헷갈리는 쌍 상위 10), 보정(ECE).
- **도메인 격차 평가**: 팀원이 앱으로 직접 그린 그림 ≥ 300장(범주 라벨 포함, 동의된 데이터, `ml/eval-data/` 비커밋)에 대한 top-1/top-3 — 앱 실사용 정확도(발표용 핵심 수치). 격차가 크면(예: 15%p 이상) 전처리·증강을 조정해 재학습.
- 결과를 `ml/quickdraw/REPORT.md` 에 표·그래프(PNG)로 저장.

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `ml/quickdraw/{README.md, PREPROCESS_SPEC.md, categories.json, categories.proposed.md, config.yaml, download.py, rasterize.py, dataset.py, train.py, export.py, evaluate.py, requirements.txt, REPORT.md, tests/}` (신규) | 파이프라인 전체. `rasterize.py` 의 전처리 함수는 `PREPROCESS_SPEC.md` 와 1:1. |
| `ml/quickdraw/golden/*` (신규) | 골든 벡터 10쌍 |
| `ml/quickdraw/export/*` (신규) | 앱용 산출물 + 라벨 + 모델 카드 |
| `.gitignore` | `ml/**/data/`, `ml/**/.venv/`, `ml/eval-data/`, 체크포인트 |
| 문서 | `docs/FEATURE/PROJECT_OVERVIEW.md`(기능 소개 — 온디바이스 AI), `docs/해야될거/A8`(앱 연동), 라이선스 안내(CC BY 4.0) 문구 초안을 `ml/quickdraw/README.md` 에 포함(앱 라이선스 화면 반영은 A8/A5) |

## Implementation Steps

1. `categories.proposed.md` 작성 → 사용자 확인(범주 확정). 2. `download.py`(범주별 ndjson 일부 스트리밍 다운로드) + `rasterize.py` + 골든 벡터 생성. 3. `--smoke` 로 파이프라인 end-to-end(범주 5개·샘플 소량) 통과. 4. 전체 학습(GPU/Colab 권장) → 검증 곡선 확인. 5. 경량화 비교표 → 앱용 모델 선택 → Core ML 변환. 6. 도메인 격차 평가셋 수집·평가 → 필요 시 재학습. 7. 산출물·REPORT·모델 카드 정리. 8. pytest(전처리 단위 테스트, 골든 벡터, 모델 입출력 모양, 라벨 개수 == 출력 차원).

## Constraints

- **그림 데이터를 서버·외부로 전송하는 코드를 만들지 않는다.** 앱 추론은 오프라인. 사용자 그림을 학습 데이터로 수집하는 기능 금지(평가셋은 팀원 동의 하에 수동 수집).
- Quick, Draw! 데이터셋 사용 시 **CC BY 4.0 출처 표기 의무** 준수(모델 카드·앱 라이선스 화면). 모델 이름/주장에 구글 보증 암시 금지.
- 사전 학습된 외부 분류 모델(MobileNet 등)을 그대로 가져와 쓰지 않는다 — "직접 학습" 이 요구사항(전이학습 기반 확장은 보고서에 별도 실험으로만).
- 모델 크기 상한 3 MB(앱 용량), 앱 최소 사양에서 동작. 파이썬 버전·라이브러리 버전 고정. 백엔드/앱 코드는 이 계획에서 수정하지 않는다.
- 커밋/푸시·지침 파일 수정 금지.

## Edge Cases

- 범주가 서로 비슷해 혼동(고양이/개, 해/별, 구름/비): 혼동행렬로 확인해 정말 혼동이 크면 범주 교체.
- 앱 그림이 학습 분포와 다름: 작은 그림(캔버스 일부만 사용), 색 잉크(연한 색), 지우개로 일부 삭제, 매우 단순(점/선 한 개) → 전처리 명세의 `MIN_INK`·bbox 처리와 증강으로 대응, 골든 벡터에 이 케이스 포함.
- 여러 물체를 한 그림에 그림/글자 쓰기/낙서: 모델은 항상 범주 중 하나를 고른다 → 앱은 **최대 확률이 임계(예: 0.35) 미만이면 "잘 모르겠어요"**로 표시(임계값은 보정 곡선으로 결정해 모델 카드에 기록).
- 범주 밖 그림(사람 얼굴 외 임의 그림): 같은 이유로 낮은 신뢰도 처리.
- 양자화로 일부 범주 정확도 급락: 범주별 정확도 비교로 발견, 대표 데이터셋 구성 조정.
- 플랫폼 간 결과 차이(TFLite vs Core ML): 같은 골든 입력에서 top-1 일치율 ≥ 99% 를 확인.
- 데이터 다운로드 실패/속도: 범주당 파일이 수백 MB 일 수 있어 앞부분만 읽도록 스트리밍(`N_PER_CLASS` 도달 시 중단).

## Regression Risks

- 이 계획은 ML 산출물만 추가하므로 서버/앱 코드 회귀 위험은 없다. 위험은 **학습-앱 전처리 불일치**(정확도가 오프라인 수치보다 크게 낮아짐) — 골든 벡터 테스트와 실기기 평가로 방지.
- 라이선스 표기 누락(법적 위험) → A8/A5 의 라이선스 화면 항목이 완료 조건에 들어 있어야 한다.
- 모델 파일이 커지면 앱 용량·APK 크기 증가 — 상한으로 방지.

## Tests Required

- pytest: 전처리 함수(빈 마스크, 한 점, 가로선 — bbox 종횡비 유지·중앙 배치·값 범위 0~1), 골든 벡터 재생성 일치, ndjson → 마스크 래스터라이저(굵기 범위), 라벨/카테고리 파일 일관성(`id` 연속·중복 없음·출력 차원 일치), TFLite 모델 입출력 모양/dtype, Core ML 변환 후 샘플 입력 일치(macOS 에서만 실행되는 테스트는 마커로 분리).
- 평가 스크립트 스모크 테스트(소량 데이터).

## Validation Steps

`pytest ml/quickdraw/tests` 통과, `evaluate.py` 로 목표 수치(top-1 ≥ 75%, top-3 ≥ 90%, 크기 ≤ 1 MB, 지연 ≤ 30 ms) 달성 여부와 실수치를 REPORT.md 에 기록(미달이면 원인과 다음 시도), 골든 벡터를 A8/A5 가 소비할 수 있게 경로를 보고서에 명시. macOS 가 없어 Core ML 변환/검증을 못 했으면 "Core ML 미검증"으로 명시하고 TFLite 산출물만 먼저 A8 에 사용.

## Potential Merge Conflicts

신규 디렉터리라 코드 충돌 없음. `.gitignore`, `docs/FEATURE/PROJECT_OVERVIEW.md` 만 겹칠 수 있음. A8/A5 가 `ml/quickdraw/export/` 산출물을 앱 리소스로 복사하므로 모델 갱신 시 두 앱 모두 갱신 필요.
