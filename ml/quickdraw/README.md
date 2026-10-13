# M1 — Quick, Draw! 그림 인식 모델

앱의 "AI가 맞혀볼게요" 게임에 쓸 그림 분류 모델을 직접 학습하는 폴더다. 계획: `docs/해야될거/2번_우준/M1-quickdraw-model.md`.
범주 50개는 `categories.json`(모델 출력 순서 = `id`), 고른 이유는 `categories.proposed.md`.

## 준비 (Windows, Python 3.12)

```bat
cd ml\quickdraw
py -3.12 -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
```

## 데이터 받기 (`download.py`)

Quick, Draw! 의 `simplified` 파일(획 좌표, 범주당 50~90MB)을 받아 인식된(`recognized`) 그림만 남기고, 중복을 지운 뒤 시드로 섞어 8:1:1 로 나눈다.

```bat
:: 작은 테스트 (이 PC): 범주 5개, 파일 앞 1,500줄만 읽어 500장씩
python download.py --only "cat,face,cloud,hot air balloon,house" --per-class 500 --max-lines 1500 --out data/smoke

:: 전체 (Colab 권장): 50개 범주, 범주당 30,000장 (약 3~4GB 다운로드)
python download.py
```

- 결과: `data/{train,val,test}/<범주>.ndjson` (한 줄 = `{"key_id", "drawing"}`) + `data/manifest.json`(설정·개수·출처).
- 같은 설정으로 이미 받은 범주는 건너뛴다(`--force` 로 다시 받기). `data/` 는 깃에 올리지 않는다.

## 그림 → 28×28 (`rasterize.py`, `PREPROCESS_SPEC.md`)

획 좌표를 잉크 마스크로 그리고(학습 땐 굵기 3~20px·회전 ±10°·가로세로 ±10% 를 무작위로), 명세대로 28×28 로 바꾼다.
**앱(A8·A5)도 `PREPROCESS_SPEC.md` 를 똑같이 구현**하고, `golden/` 의 정답 예시 10개로 결과가 같은지 확인한다.

```bat
python make_golden.py                 :: golden/*.json 다시 만들기 (명세를 바꿨을 때만)
python preview.py --data data/smoke   :: data/smoke/preview_train.png — 왼쪽 6장 평가용 고정 설정, 오른쪽 6장 학습용 무작위 설정
```

## 학습 (`dataset.py`, `train.py`)

`dataset.py` 가 받은 그림을 28×28 로 **한 번만** 변환해 `<data>/cache/*.npz` 로 저장하고(학습용은 그림마다 무작위 설정, 검증·테스트는 고정 설정), `train.py` 가 소형 CNN(파라미터 약 14만 개)을 학습한다.

```bat
python train.py --smoke                  :: 이 PC: data/smoke 5개 범주, 15 epoch, 약 20초
python train.py --data data --name full  :: Colab: 50개 범주 전체 (기본 30 epoch, 검증 정확도가 4번 연속 안 오르면 멈춤)
```

- 결과 폴더 `runs/<이름>/`: `model.keras`, `best.keras`, `metrics.json`(테스트 top-1·top-3, 범주별 정확도, 걸린 시간, 버전), `history.csv`, `labels_en.txt`, `labels_ko.txt`, `config.json`. `runs/` 는 깃에 올리지 않는다.
- 측정값(2026-10-12, 이 PC): 그림 변환 약 730장/초(프로세스 1개) → 전체 150만 장은 프로세스 2개(Colab)로 약 20분. 작은 학습 결과 테스트 top-1 0.972 / top-3 0.992 (5개 범주, 학습 2,000장). Keras → TFLite(float32) 변환 확인, 출력 차이 2e-7.
- **본 학습(2026-10-13, Colab T4)**: 50개 범주, 학습 120만 / 검증 15만 / 테스트 15만 장, 파라미터 146,258개. 테스트 **top-1 93.1% / top-3 98.4%**. 그림 변환 1시간 45분(프로세스 2개 — 예상보다 오래 걸림) + 학습 47분. 가장 약한 범주는 강아지 67%(코끼리·고양이·돼지와 혼동), 가장 많이 헷갈린 쌍은 열기구↔전구.
- Colab 에서는 `colab_train.ipynb` 를 열어 위 칸부터 차례로 실행한다(코드는 `dist/quickdraw_code.zip` 으로 올린다 — download·rasterize·dataset·train·export.py, categories.json, golden/).

## 크기 줄이기·앱용 파일 (`export.py`)

```bat
python export.py --smoke                        :: 이 PC: 연습 모델 → runs/smoke/export/
python export.py --run runs/full --data data    :: 본 모델 → export/ (Colab 노트북 7번 칸이 이것을 한다)
```

- TFLite 후보 3개 — float32, float16(가중치 절반), int8(계산 전체를 정수로, 학습 그림 500장으로 보정) — 를 테스트셋 전체로 비교하고, **Keras 원본보다 top-1 이 1%p 넘게 떨어지지 않는 것 중 가장 작은 것**을 고른다. 세 후보 모두 입력·출력이 float32 라서 앱 코드는 같다.
- 결과 `export/`(깃에 올림): `quickdraw_cnn.tflite`, `labels_ko.txt`, `labels_en.txt`, `model_card.md`(입력·출력, 후보 비교표, 범주, 라이선스, 한계), `export.json`(같은 내용의 숫자), `golden_outputs.json`(골든 예시를 넣었을 때의 출력 — 앱이 모델을 제대로 부르는지 확인용). 후보 3개는 `runs/<이름>/tflite/`(깃 제외).
- 연습 결과(2026-10-13, 5개 범주): float32 550KB → float16 280KB → int8 151KB, 세 개 모두 top-1 97.2% 그대로, 이 PC 지연 int8 0.09ms.
- **본 모델(2026-10-13)**: float32 573KB → float16 292KB → **int8 158KB(선택)**, 셋 다 top-1 93.13% 그대로. `export/` 에 있고, 이 PC 에서 다시 실행해도 골든 출력과 확률 차이 1e-6 이하(Colab 과 같음).
- iOS 용 Core ML 은 Windows 에서 만들 수 없어 아직 없다(Core ML 미검증).

## 앱 그림으로 평가 (`collect.py`, `evaluate.py`)

Quick, Draw! 테스트셋 점수만으로는 앱에서 그린 그림을 얼마나 맞히는지 모른다. 팀원이 휴대폰으로 그린 그림을 모아 따로 잰다.

```bat
python collect.py      :: 그림 모으기 서버. 화면에 나온 http://192.168.x.x:8000 을 같은 와이파이의 휴대폰으로 연다
python evaluate.py     :: export/quickdraw_cnn.tflite 로 팀원 그림(+ data/cache/test.npz 가 있으면 테스트셋) 평가 → EVAL.md
```

- 그림판(`collect.html`)은 앱 그림 미션과 같다: 흰 바탕, 펜 굵기 3·6·12, 색 7가지, 지우개, 되돌리기, 화면의 2배 크기 PNG. 이름(우준·경환·찬희·한수, 또는 직접 입력)을 고르면 그 사람이 가장 적게 그린 범주가 나오고, 5장마다 1장꼴로 범주 밖 단어(연필·의자 …)가 나온다. 한 사람 목표 75장(4명이면 300장).
- 그림은 `ml/eval-data/quickdraw/<사람>/<범주>__<번호>.png` + 같은 이름 `.json`(획 좌표)으로 이 PC 에만 저장된다(깃 제외). 처음 켤 때 Windows 방화벽 창이 뜨면 **개인 네트워크** 허용.
- `evaluate.py` 는 앱과 같은 전처리(`PREPROCESS_SPEC.md`)로 28×28 을 만들어 top-1·top-3, 사람별·범주별 정확도, 헷갈린 쌍, 테스트셋과의 차이(도메인 격차)를 적는다. **"잘 모르겠어요" 기준**은 맞힌 답 +1, 틀린 답 −1(범주 밖 그림에 답한 것 포함), 모르겠다 0 으로 셌을 때 점수가 가장 높은 값을 추천한다.
- 결과: `EVAL.md`(집계만, 깃에 올림) + `ml/eval-data/quickdraw/_results/`(그림별 결과 `predictions.csv`, 틀린 그림 모음 `mistakes.png`, 깃 제외). 연습: `python evaluate.py --smoke`.

## 테스트

```bat
python -m pytest
```

## 데이터 출처·라이선스

Quick, Draw! Dataset by Google, [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) — <https://github.com/googlecreativelab/quickdraw-dataset>.
모델 설명서와 앱 라이선스 화면(A8·A5)에 출처를 적는다. 구글이 이 모델을 보증하는 것처럼 표현하지 않는다.
