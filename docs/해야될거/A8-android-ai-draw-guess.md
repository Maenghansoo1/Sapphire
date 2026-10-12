# A8. Android — "AI가 맞혀볼게요" (온디바이스 그림 인식 게임, TFLite)

> 범위: **Android 앱만**(`android/`). **전제: M1(모델 학습·경량화) 산출물 `ml/quickdraw/export/quickdraw_cnn.tflite`, `labels_ko.txt`, `labels_en.txt`, `model_card.md`, `ml/quickdraw/golden/`, `PREPROCESS_SPEC.md` 가 있을 것, A0(음성 제거) 이후.** 서버 변경 없음 — 추론은 전부 기기 안, **그림을 서버로 보내지 않는다.** iOS(Core ML)는 A5. 구현자: Claude 또는 Codex. 디자인이 없으므로 AI 말풍선·결과 UI 는 기존 컴포넌트 + 임시 이미지로 구성한다.
> 빌드/테스트: JDK 21 — `set JAVA_HOME=C:\Users\kimch\.jdks\ms-21.0.10` 후 `android\gradlew.bat :app:testDebugUnitTest --offline`.

## Goal

1. **그림 미션을 올리기 전** "AI에게 보여주기"를 누르면 AI 가 **내 그림을 보고 무엇인지 추측**(상위 3개 + 확률 막대)한다. 주제를 맞히면 "AI가 맞혔어요!", 아니면 "AI는 ○○라고 생각했어요" 처럼 재미있게 보여 준다(게임 요소, 결과는 저장·전송하지 않음).
2. **결과 피드/상세**의 친구 그림에서도 "AI의 추측 보기" 로 같은 추론을 실행한다(그림 PNG 를 기기 안에서 분석).
3. 앱 라이선스 화면에 **Quick, Draw! 데이터셋(CC BY 4.0) 출처**와 모델 카드 요약을 추가한다.

## Findings

- 모델 계약(M1): 입력 `[1, 28, 28, 1]` float32(0~1, 잉크=1), 출력 확률 벡터(범주 수 = `labels_ko.txt` 줄 수). 전처리는 `PREPROCESS_SPEC.md`(잉크 마스크 → bbox → 긴 변 20px 로 종횡비 유지 리사이즈 → 28×28 중앙 배치 → 정규화)를 **정확히** 따라야 하며, `golden/` 의 입력→기대 출력 벡터로 앱 구현을 검증한다. "너무 비어 있음"(`MIN_INK` 미만)은 추론을 건너뛰고 안내.
- 앱 그림 화면: `feature/mission/DrawMissionScreen.kt` — `DrawingModel`(`strokes`, 지우개 포함 `DrawStroke`), `renderDrawingPng(strokes, widthDp, heightDp, scale)` 로 PNG 생성 후 `onSubmit(ByteArray)`. "올리기" `HButton`(약 152행). 주제 `topic: String`, `spec: MissionSpec`(`seconds`, `drawFrom`, `drawTo`). 그림 틀은 `DRAW_SKETCH`(주제 1개를 그림)와 `DRAW_A_TO_B`(A를 B로 바꾸기); 대상 지정(`[대상]`) 시 사람 그리기.
- 결과 화면: `ResultsScreen.kt` 의 `DrawingGrid` / `DrawingArtwork`(Coil `AsyncImage`로 PNG 로드, 흰 배경 + 색 잉크, `ResultComponents.kt`), 상세 `SubmissionDetailScreen.kt`. 이미지 URL 은 `Submission.mediaURL`(원격) 또는 로컬 파일(`localImageFile(submission)`).
- 팔레트: `DrawPalette.colors` 7색(검정/회색/빨강/주황/연노랑/초록/파랑). 연한 색 잉크가 "흰색과의 거리" 임계에서 사라지지 않도록 임계는 낮게.
- 의존성: `android/app/build.gradle.kts` 는 Compose·Coil·OkHttp·CameraX·serialization 만. TFLite 없음 → `org.tensorflow:tensorflow-lite`(최신 안정 버전은 구현 시 확인) 추가 필요. 버전 카탈로그 `android/gradle/libs.versions.toml`. minSdk 26.
- 주제와 범주 연결: 그림 주제(`subject`)는 자유 문장 가능(AI 생성 B8). 범주(M1)는 약 50개 → 주제가 범주에 **매핑될 때만** "맞혔는지" 판정(`topic`/`subject` 문자열에 범주의 `ko`/`aliases_ko` 가 포함되면 매핑). 매핑 실패 시 판정 없이 "AI는 ○○라고 생각해요"만 표시.
- 라이선스 화면: `feature/mypage/LicensesScreen.kt`(`HListRow` 로 폰트 2종, `openInCustomTab`), 텍스트 자산 `assets/licenses/*.txt`.

## Root Cause / Key Implementation Points

1. **전처리 구현**(`feature/draw/InkPreprocessor.kt`, 순수 Kotlin + `android.graphics.Bitmap` 분리): 
   - 입력 어댑터 2종: ① **스트로크에서**: `DrawStroke` 목록을 알파 마스크 Bitmap(예: 256×256, 투명 배경, 검정 잉크, 지우개는 `PorterDuff.Mode.CLEAR`)으로 렌더(`drawStrokes` 재사용, 색은 무시하고 검정) → `alpha > 0.5` 를 잉크로. ② **PNG 에서**(피드): 디코드 후 `1 − min(R,G,B)/255` 가 `INK_DISTANCE`(예: 0.08) 초과 또는 알파 있는 픽셀을 잉크로.
   - 그 뒤 공통 `preprocess(mask: BooleanArray/FloatArray, w, h): FloatArray(28*28)` 가 명세 1~4단계. 면적 평균 보간 구현, 계산은 `FloatArray` 로 해 Bitmap 의존을 단위 테스트에서 분리한다.
2. **추론기** `feature/draw/QuickDrawClassifier.kt`: TFLite `Interpreter`(스레드 2, XNNPACK 기본), 모델/라벨은 `assets/quickdraw/`(`quickdraw_cnn.tflite`, `labels_ko.txt`, `labels_en.txt`) 에서 로드, **지연 로드 + 싱글턴**(앱 시작 비용 회피), 추론은 `Dispatchers.Default`. API: `suspend fun classify(mask): Prediction?` → 상위 3개 `(id, label, probability)`. 모델/라벨 로드 실패는 기능 숨김(크래시 금지). 라벨 수와 모델 출력 차원이 다르면 예외 대신 기능 비활성 + 로그.
3. **게임 UI**:
   - `DrawMissionScreen`: 올리기 버튼 옆/아래에 보조 버튼 "AI에게 보여주기"(그림이 있을 때만 활성). 누르면 하단 시트/다이얼로그에 "AI가 생각 중…"(최소 0.8초 연출, 임시) → 결과 카드: 1위 라벨(한국어) + 확률 막대 3개, 주제 매핑 시 "AI가 맞혔어요! 🎉"/"아쉽게 못 맞혔어요 — AI는 ○○라고 생각했어요". 신뢰도 임계(M1 모델 카드의 값, 기본 0.35) 미만이면 "잘 모르겠어요 — 더 그려 볼까요?". **업로드/제출 흐름에 영향 없음**(보기 전용).
   - 결과 피드/상세: 그림 카드에 작은 "AI의 추측" 버튼(내 그림과 친구 그림 모두) → 같은 결과 카드를 표시. 이미지가 로컬/원격이어도 PNG 바이트를 메모리로 읽어 `PNG 어댑터` 사용(원격은 Coil 캐시/OkHttp 로 다운로드 — 이미 로드된 이미지를 재사용할 수 있으면 사용).
   - 임시 이미지: AI 캐릭터 아이콘 `res/drawable/ic_ai_guess_temp.xml`(둥근 말풍선+점 3개 등 단순 벡터, 첫 줄 주석 `<!-- 임시 이미지: Figma 디자인 확정 시 교체 -->`), `HIcon.AI_GUESS_TEMP`. 이모지는 문구에서만 최소 사용.
4. **주제 매핑**(`DrawSubjectMatcher`, 순수): `categories`(라벨 json)의 `ko`/`aliases_ko` 와 미션 `topic`/`spec.drawFrom` 를 정규화(공백 제거·소문자) 후 부분 포함 비교, 여러 개 일치하면 가장 긴 것. `DRAW_A_TO_B` 는 `drawTo`(결과물)를 기준으로 판정(`spec.drawTo`), 대상 지정(`[대상]`) 미션은 판정하지 않음.
5. **라이선스 화면**: `LicensesScreen` 에 항목 추가 — "Quick, Draw! 데이터셋 — Google, CC BY 4.0"(링크 `https://creativecommons.org/licenses/by/4.0/` 및 `https://github.com/googlecreativelab/quickdraw-dataset`), "그림 인식 모델 — 직접 학습(모델 카드)" 한 줄. 하단 안내 문구에 "AI 추측은 재미용이며 서버로 전송되지 않아요." 포함.
6. **성능/배터리**: 입력이 28×28 이라 호출 비용이 작다. 피드 목록에서 자동 일괄 추론은 하지 않는다(탭할 때만).
7. **개인정보**: 그림 PNG/마스크를 파일·로그·분석 도구에 남기지 않는다. 추론 결과를 저장하지 않는다(DataStore/서버 모두).

## Files / Functions to Change

| 파일 | 변경 |
|---|---|
| `android/app/build.gradle.kts`, `android/gradle/libs.versions.toml` | `tensorflow-lite` 의존성(버전은 구현 시 확인·고정), aaptOptions `noCompress "tflite"`(필요 시) |
| `android/app/src/main/assets/quickdraw/*` | `quickdraw_cnn.tflite`, `labels_ko.txt`, `labels_en.txt`, `categories.json` (M1 산출물 복사) |
| `feature/draw/InkPreprocessor.kt`, `QuickDrawClassifier.kt`, `DrawSubjectMatcher.kt`, `AiGuessCard.kt` (신규 — 패키지 `feature/draw` 는 새 feature 폴더) | 전처리·추론·매칭·결과 UI |
| `feature/mission/DrawMissionScreen.kt` | "AI에게 보여주기" 버튼·시트 |
| `feature/results/ResultsScreen.kt`, `ResultComponents.kt`, `SubmissionDetailScreen.kt` | 그림 카드의 "AI의 추측" 진입 |
| `feature/mypage/LicensesScreen.kt` | CC BY 4.0 출처 |
| `designsystem/assets/HIcon.kt`, `res/drawable/ic_ai_guess_temp.xml` | 임시 아이콘 |
| 테스트 | 아래 |

## Implementation Steps

1. M1 산출물 확인(없으면 M1 먼저) → 자산 복사·의존성 추가. 2. `InkPreprocessor` 를 **골든 벡터 테스트부터**(`ml/quickdraw/golden/*` 를 `src/test/resources/quickdraw/golden/` 로 복사해 사용). 3. `QuickDrawClassifier`(모델 로드·추론) 계측 테스트가 어려우면 인터페이스로 분리하고 JVM 테스트에서는 가짜 구현 사용. 4. `DrawSubjectMatcher`. 5. 게임 UI(그림 화면 → 피드). 6. 라이선스. 7. `android\gradlew.bat :app:testDebugUnitTest --offline`. 8. 실기기 확인(사용자): 고양이/집/구름 등을 그려 정확도 체감, 지연, 빈 그림, 작은 그림.

## Constraints

- `android/` 만(자산은 M1 산출물 복사). **네트워크 전송·저장 금지**(그림, 마스크, 추론 결과). 서버 API 추가 금지. 외부 AI SDK 금지(TFLite 인터프리터만). 사용자의 제출 여부·도장·점수에 AI 추측이 영향 주지 않는다(순수 재미). 커밋/푸시 금지.
- 모델/라벨을 바꾸면 앱 재빌드만으로 반영(원격 모델 다운로드 금지).
- 정확도가 낮을 때 비난하는 문구 금지("AI가 아직 배우는 중이에요" 같은 중립/유머).

## Edge Cases

- 빈 그림/아주 작은 그림/점 하나 → "조금 더 그려 주세요" 안내(추론 생략).
- 지우개로 대부분 지운 그림 → 스트로크 어댑터가 지우개 반영한 마스크를 사용(알파 CLEAR). PNG 어댑터는 최종 이미지 기준이라 동일.
- 연한 노랑 잉크만 사용한 그림 → PNG 어댑터에서 임계 때문에 비는지 샘플 테스트.
- 범주 밖 그림/글자/여러 물체 → 낮은 신뢰도 → "잘 모르겠어요".
- 주제가 범주와 매핑되지 않는 미션 → 판정 문구 없이 추측만.
- 모델 자산 누락/손상/라벨 불일치 → 버튼 숨김 + 로그(크래시 금지).
- 저사양 기기 첫 추론 지연(모델 로드) → 로딩 연출로 가림, 이후 즉시.
- 원격 PNG 다운로드 실패 → "그림을 불러오지 못했어요" 토스트.
- 접근성: 결과 카드에 라벨·확률 `contentDescription`.

## Regression Risks

- 그림 화면 하단 UI(올리기 버튼) 레이아웃 변경으로 작은 화면에서 가려짐 → 확인.
- APK 크기 증가(TFLite 런타임 ≈ 수 MB + 모델) → 허용 범위 보고(ABI 필터링 고려).
- 의존성 추가로 minSdk/ProGuard 설정 영향 없음 확인(릴리스 빌드는 이번 범위 밖이지만 keep 규칙 필요 여부 메모).
- 전처리가 명세와 어긋나면 정확도만 조용히 하락 → 골든 테스트가 방어선.

## Tests Required

- `InkPreprocessorTest`: 골든 벡터 10쌍 일치(최대 절대오차 ≤ 0.02), 빈 마스크 → null, 점/선 bbox, 종횡비 유지·중앙 배치, 값 범위 0~1.
- `DrawSubjectMatcherTest`: 매핑 성공/실패/별칭/가장 긴 일치/`DRAW_A_TO_B` 기준/`[대상]` 제외.
- `QuickDrawClassifier` 후처리 순수 함수(top-3 정렬, 임계 미만 → null 판정) 단위 테스트; 모델 추론 자체는 기기 계측 테스트가 없는 repo 이므로 수동 확인 + (가능하면) JVM TFLite 로 골든 입력 top-1 확인은 M1 의 pytest 가 담당.
- 라이선스 항목은 컴파일/수동.

## Validation Steps

`android\gradlew.bat :app:testDebugUnitTest --offline`(JDK 21) 통과 기록, APK 크기 증가량 보고, 수동 체크리스트(범주 10개 직접 그려 top-3 적중 수, 평균 지연, 빈 그림, 피드에서 친구 그림 추측) 보고서에 포함, 못 했으면 "미검증".

## Potential Merge Conflicts

`DrawMissionScreen.kt`, `ResultsScreen.kt`, `ResultComponents.kt`, `SubmissionDetailScreen.kt`, `LicensesScreen.kt`, `HIcon.kt`, `build.gradle.kts`/`libs.versions.toml`(A0·A4·A7 과 겹침 — A0 먼저, A4 와 A8 은 같은 화면을 다른 곳에서 건드리므로 순차).
