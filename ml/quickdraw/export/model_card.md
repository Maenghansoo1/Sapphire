# 그림 인식 모델 설명서 — `quickdraw_cnn.tflite`

만든 날 2026-10-13 · 학습 결과 `runs/full` · 전처리 명세 버전 1(`../PREPROCESS_SPEC.md`).
이 파일은 `export.py` 가 만든다. 손으로 고치지 말고 다시 만든다.

## 무엇을 하는 모델인가

앱에서 그린 그림을 28×28 로 바꿔 넣으면 범주 50개 중 무엇인지 확률로 답한다. 직접 학습한 소형 CNN(파라미터 146,258개)이고, 사전 학습된 모델은 쓰지 않았다. 휴대폰 안에서만 실행한다(그림을 서버로 보내지 않는다).

## 입력·출력 (앱과의 약속)

| | 텐서 이름 | 모양 | 형식 | 뜻 |
|---|---|---|---|---|
| 입력 | `serving_default_image:0` | `[1, 28, 28, 1]` | float32 | 0.0(빈 곳)~1.0(잉크). `PREPROCESS_SPEC.md` 대로 만든다 |
| 출력 | `StatefulPartitionedCall_1:0` | `[1, 50]` | float32 | 범주별 확률(합 1). i 번째 값 = `labels_ko.txt`·`labels_en.txt` 의 i 번째 줄 |

크기 줄이기는 **int8** 를 골랐다. 어느 후보든 입력·출력은 float32 라서, 다시 골라도 앱 코드는 바뀌지 않는다.
앱은 이름 대신 0번 입력·0번 출력으로 부르면 된다(텐서 이름은 변환기가 붙인 것).
앱 테스트는 `golden_outputs.json` 을 쓴다: `../golden/<name>.json` 의 `expected` 를 넣었을 때 모든 확률의 차이가 0.02 이하이고, `margin`(1등과 2등 차이)이 0.05 이상인 예시는 1등 범주도 같은지 확인한다.

## 정확도 (Quick, Draw! 테스트셋)

학습에 쓰지 않은 그림 150,000장(범주마다 같은 수, 굵기 8px 로 그림). int8 보정에는 학습 그림 500장(범주마다 같은 수)을 썼다.

| 후보 | 크기 | top-1 | top-3 | 원본 대비 top-1 | 원본과 1등 일치 | 가장 많이 떨어진 범주 | 지연 p50 / p95 |
|---|---|---|---|---|---|---|---|
| Keras 원본 | - | 93.1% | 98.4% | - | - | - | - |
| float32 | 573 KB | 93.1% | 98.4% | +0.00%p | 100.0% | sun (+0.0%p) | 0.50 / 0.59 ms |
| float16 | 292 KB | 93.1% | 98.4% | +0.00%p | 100.0% | moon (-0.0%p) | 0.50 / 1.05 ms |
| **int8** (선택) | 158 KB | 93.1% | 98.4% | -0.00%p | 99.5% | airplane (-0.8%p) | 1.16 / 1.92 ms |

고르는 규칙: Keras 원본보다 top-1 이 1%p 넘게 떨어지지 않는 후보 중 가장 작은 것.
지연은 x86_64 (CPU 2개), 스레드 2개로 한 장씩 부른 값이다. 휴대폰에서 잰 값은 A8 에서 적는다.

| 목표 | 기준 | 결과 |
|---|---|---|
| top-1 | ≥ 75% | 93.1% ✅ |
| top-3 | ≥ 90% | 98.4% ✅ |
| 크기 | ≤ 1 MB | 158 KB ✅ |
| 지연 | 휴대폰 ≤ 30 ms | 이 컴퓨터 1.16 ms · 휴대폰 미측정 |

가장 못 맞힌 범주 5개: 강아지(dog) 66.9%, 전구(light bulb) 82.1%, 열기구(hot air balloon) 83.8%, 돼지(pig) 83.9%, 새(bird) 85.9%

## "잘 모르겠어요" 기준

가장 높은 확률이 기준보다 낮으면 앱은 답 대신 "잘 모르겠어요" 를 보여 준다. 테스트셋에서:

| 기준 | 답하는 비율 | 답한 것의 정확도 |
|---|---|---|
| 0.2 | 99.3% | 93.6% |
| 0.35 | 96.5% | 95.0% |
| 0.5 | 91.8% | 96.7% |

**기준은 0.55.** 테스트셋이 아니라 팀원이 앱 그림판으로 그린 그림(범주 밖 그림 포함)으로 정했다 — 근거와 다른 기준값 비교는 `../EVAL.md`. 앱(Android `AI_GUESS_THRESHOLD`)도 같은 값을 쓴다.

## 범주

| 번호 | 한국어 | 영어 |
|---|---|---|
| 0 | 해 | sun |
| 1 | 달 | moon |
| 2 | 별 | star |
| 3 | 구름 | cloud |
| 4 | 무지개 | rainbow |
| 5 | 번개 | lightning |
| 6 | 눈송이 | snowflake |
| 7 | 산 | mountain |
| 8 | 나무 | tree |
| 9 | 꽃 | flower |
| 10 | 화분 | house plant |
| 11 | 버섯 | mushroom |
| 12 | 고양이 | cat |
| 13 | 강아지 | dog |
| 14 | 토끼 | rabbit |
| 15 | 돼지 | pig |
| 16 | 코끼리 | elephant |
| 17 | 기린 | giraffe |
| 18 | 뱀 | snake |
| 19 | 물고기 | fish |
| 20 | 문어 | octopus |
| 21 | 새 | bird |
| 22 | 나비 | butterfly |
| 23 | 사과 | apple |
| 24 | 딸기 | strawberry |
| 25 | 피자 | pizza |
| 26 | 햄버거 | hamburger |
| 27 | 아이스크림 | ice cream |
| 28 | 생일 케이크 | birthday cake |
| 29 | 컵 | cup |
| 30 | 책 | book |
| 31 | 우산 | umbrella |
| 32 | 열쇠 | key |
| 33 | 시계 | clock |
| 34 | 전구 | light bulb |
| 35 | 가위 | scissors |
| 36 | 안경 | eyeglasses |
| 37 | 기타 | guitar |
| 38 | 왕관 | crown |
| 39 | 곰인형 | teddy-bear |
| 40 | 티셔츠 | t-shirt |
| 41 | 신발 | shoe |
| 42 | 자동차 | car |
| 43 | 자전거 | bicycle |
| 44 | 비행기 | airplane |
| 45 | 기차 | train |
| 46 | 열기구 | hot air balloon |
| 47 | 집 | house |
| 48 | 얼굴 | face |
| 49 | 눈사람 | snowman |

## 학습

- 데이터: Quick, Draw! `simplified`, 범주마다 인식된(`recognized`) 그림 학습 24,000 / 검증 3,000 / 테스트 3,000장 (중복 제거, 시드 42).
- 학습 그림: 굵기 3~20px · 회전 ±10° · 가로세로 ±10% 를 그림마다 무작위로. 검증·테스트는 굵기 8px 고정.
- 구조: Conv32 ×2 → MaxPool → Conv64 ×2 → MaxPool → Conv128 → GlobalAvgPool → Dropout 0.3 → Dense(softmax). Conv 마다 BatchNorm(momentum 0.9) + ReLU.
- 설정: epoch 30 (최대 30, 검증 정확도가 4번 안 오르면 멈춤), batch 256, AdamW(lr 0.002, weight decay 0.0001, 코사인 감소), label smoothing 0.05.
- 버전: 학습 tensorflow 2.21.0, keras 3.13.2, numpy 2.1.3 · 내보내기 tensorflow 2.21.0, keras 3.13.2.

## 데이터 출처·라이선스

Quick, Draw! Dataset by Google, [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) — <https://github.com/googlecreativelab/quickdraw-dataset>.
앱 라이선스 화면(A8·A5)에 이 출처를 적는다. 구글이 이 모델을 만들거나 보증한 것이 아니다.

## 한계

- 학습 그림은 Quick, Draw! 사용자들이 20초 안에 그린 낙서다. 앱에서 천천히 그린 그림은 다를 수 있다 → 6단계에서 앱 그림으로 따로 잰다.
- 범주 밖 그림·글자·여러 물체도 범주 중 하나로 답한다 → 위 "잘 모르겠어요" 기준으로 거른다.
- iOS 용 Core ML 모델은 아직 없다(Windows 에서는 만들 수 없음 — Core ML 미검증).
