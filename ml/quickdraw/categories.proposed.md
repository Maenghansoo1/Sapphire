# M1 그림 범주 후보 (50개)

> **확정 (2026-10-12):** 아래 50개를 그대로 확정해 `categories.json` 으로 만들었다. 학습 결과 혼동이 크면 바꾸고 다시 학습한다.

작성: 2026-10-12. 범주를 바꾸면 처음부터 다시 학습해야 하므로 **학습 전에 확정**한다(결정 #17, 우준 확정).
영문 이름은 공식 목록(`googlecreativelab/quickdraw-dataset` 의 `categories.txt`, 345개)과 철자를 대조했다. 확정되면 이 표 순서대로 `categories.json`(`id` = 모델 출력 순서)을 만든다.

## 고른 기준

1. **앱 그림 미션 주제와 겹치는 것**(★): 임시 생성기 주제 `얼굴, 표정, 구름, 풍선, 컵, 화분, 책, 집`. 풍선은 데이터셋에 없어서 `hot air balloon`(열기구)으로 대신한다. `머리 모양`은 해당 범주가 없다.
2. **30초 안에 쉽게 그릴 수 있고**, 사람마다 비슷하게 그리는 것.
3. **서로 헷갈리지 않는 것**: 비슷한 범주가 여러 개면 하나만 고른다(아래 "제외" 참고).
4. **미션 안전 규칙**(`PROJECT_OVERVIEW.md` §7)에 걸리지 않는 것: 무기·위험물·술·사람 몸은 뺀다.

## 후보 50개

| id | quickdraw | 한국어 | 묶음 |
|---|---|---|---|
| 0 | `sun` | 해 | 자연·날씨 |
| 1 | `moon` | 달 | 자연·날씨 |
| 2 | `star` | 별 | 자연·날씨 |
| 3 | `cloud` | 구름 ★ | 자연·날씨 |
| 4 | `rainbow` | 무지개 | 자연·날씨 |
| 5 | `lightning` | 번개 | 자연·날씨 |
| 6 | `snowflake` | 눈송이 | 자연·날씨 |
| 7 | `mountain` | 산 | 자연·날씨 |
| 8 | `tree` | 나무 | 식물 |
| 9 | `flower` | 꽃 | 식물 |
| 10 | `house plant` | 화분 ★ | 식물 |
| 11 | `mushroom` | 버섯 | 식물 |
| 12 | `cat` | 고양이 | 동물 |
| 13 | `dog` | 강아지 | 동물 |
| 14 | `rabbit` | 토끼 | 동물 |
| 15 | `pig` | 돼지 | 동물 |
| 16 | `elephant` | 코끼리 | 동물 |
| 17 | `giraffe` | 기린 | 동물 |
| 18 | `snake` | 뱀 | 동물 |
| 19 | `fish` | 물고기 | 동물 |
| 20 | `octopus` | 문어 | 동물 |
| 21 | `bird` | 새 | 동물 |
| 22 | `butterfly` | 나비 | 동물 |
| 23 | `apple` | 사과 | 음식 |
| 24 | `strawberry` | 딸기 | 음식 |
| 25 | `pizza` | 피자 | 음식 |
| 26 | `hamburger` | 햄버거 | 음식 |
| 27 | `ice cream` | 아이스크림 | 음식 |
| 28 | `birthday cake` | 생일 케이크 | 음식 |
| 29 | `cup` | 컵 ★ | 물건 |
| 30 | `book` | 책 ★ | 물건 |
| 31 | `umbrella` | 우산 | 물건 |
| 32 | `key` | 열쇠 | 물건 |
| 33 | `clock` | 시계 | 물건 |
| 34 | `light bulb` | 전구 | 물건 |
| 35 | `scissors` | 가위 | 물건 |
| 36 | `eyeglasses` | 안경 | 물건 |
| 37 | `guitar` | 기타 | 물건 |
| 38 | `crown` | 왕관 | 물건 |
| 39 | `teddy-bear` | 곰인형 | 물건 |
| 40 | `t-shirt` | 티셔츠 | 물건 |
| 41 | `shoe` | 신발 | 물건 |
| 42 | `car` | 자동차 | 탈것 |
| 43 | `bicycle` | 자전거 | 탈것 |
| 44 | `airplane` | 비행기 | 탈것 |
| 45 | `train` | 기차 | 탈것 |
| 46 | `hot air balloon` | 열기구 ★ (풍선 대신) | 탈것 |
| 47 | `house` | 집 ★ | 장소·사람 |
| 48 | `face` | 얼굴 ★ (얼굴·표정) | 장소·사람 |
| 49 | `snowman` | 눈사람 | 장소·사람 |

묶음별 개수: 자연·날씨 8, 식물 4, 동물 11, 음식 6, 물건 13, 탈것 5, 장소·사람 3. ★ = 앱 그림 미션 주제와 겹치는 것(7개).

## 뺀 것과 이유

| 이유 | 범주 |
|---|---|
| 안전 규칙: 무기·위험물 | `axe`, `knife`, `rifle`, `sword`, `cannon`, `skull`, `syringe`, `lighter`, `matches` |
| 안전 규칙: 술 (계획 초안의 `wine glass` 도 뺌) | `wine glass`, `wine bottle` |
| 안전 규칙: 사람 몸 (외모·몸 주제와 겹침) | `eye`, `nose`, `mouth`, `ear`, `hand`, `foot`, `arm`, `leg`, `beard`, `moustache` 등 |
| 너무 단순한 도형 | `circle`, `square`, `triangle`, `line`, `hexagon`, `octagon`, `squiggle`, `zigzag` |
| 고른 것과 헷갈림 (괄호 = 남긴 것) | `banana`(달), `smiley face`(얼굴), `mug`·`coffee cup`(컵), `truck`·`bus`·`van`·`police car`(자동차), `alarm clock`·`wristwatch`(시계), `sweater`·`jacket`(티셔츠), `cake`(생일 케이크), `rain`(구름), `watermelon`(피자), `lollipop`·`popsicle`(아이스크림), `bear`(곰인형), `donut`(시계·얼굴) |
| 이름이 특수함 (고유명사) | `The Eiffel Tower`, `The Great Wall of China`, `The Mona Lisa` |

## 학습 뒤 혼동행렬로 꼭 확인할 짝

- `cat` ↔ `dog` ↔ `pig` ↔ `face`: 동물을 얼굴만 그리는 사람이 많아 둥근 얼굴끼리 헷갈릴 수 있다.
- `sun` ↔ `flower`, `clock` ↔ `face`: 동그라미에 선을 덧붙이는 모양이 비슷하다.
- `tree` ↔ `house plant` ↔ `flower`: 식물끼리.

헷갈림이 크면(예: 서로 20% 이상 틀림) 한쪽을 위 "뺀 것" 목록이나 다른 범주로 바꾸고 다시 학습한다.

## 확정 방법

이 표에서 빼거나 넣을 범주를 정하면 `categories.json` 을 만든다. 넣을 수 있는 다른 후보 예: `sailboat`(돛단배), `cactus`(선인장), `penguin`(펭귄), `snail`(달팽이), `grapes`(포도), `pineapple`(파인애플), `donut`(도넛), `headphones`(헤드폰), `camera`(카메라), `chair`(의자), `pencil`(연필), `tent`(텐트), `castle`(성), `bee`(벌), `duck`(오리), `owl`(부엉이), `whale`(고래), `panda`(판다).
