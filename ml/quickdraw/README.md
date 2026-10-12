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

## 테스트

```bat
python -m pytest
```

## 데이터 출처·라이선스

Quick, Draw! Dataset by Google, [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) — <https://github.com/googlecreativelab/quickdraw-dataset>.
모델 설명서와 앱 라이선스 화면(A8·A5)에 출처를 적는다. 구글이 이 모델을 보증하는 것처럼 표현하지 않는다.
