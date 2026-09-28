# HA'WE — Cordova 프로젝트

Claude 캔버스에서 만든 UI 목업(로그인부터 캐릭터 선택까지 12개 화면)을 그대로 옮긴 Cordova 프로젝트예요.
지금은 **화면과 화면 간 이동만 되는 정적 프로토타입**이라, 실제 로그인·제출·댓글 저장 같은 기능은 Spring Boot 백엔드 API와 연결해야 동작해요.

## 폴더 구조
```
hawe-cordova/
├── config.xml              # Cordova 앱 설정 (앱 이름, id, 권한 등)
├── www/
│   ├── index.html           # 진입점 = 로그인 화면
│   ├── css/base.css         # 공통 스타일(폰트, 리셋)
│   ├── js/app.js            # deviceready, 안드로이드 백버튼 처리
│   └── pages/
│       ├── main.html            # 그룹 목록
│       ├── mission-locked.html  # 오늘의 미션 (잠김)
│       ├── mission-submit.html  # 미션 제출
│       ├── group-feed.html      # 그룹 피드
│       ├── post-detail.html     # 게시물 상세
│       ├── mypage.html          # 마이페이지 (달력)
│       ├── day-detail.html      # 하루 상세
│       ├── members.html         # 멤버 목록
│       ├── group-settings.html  # 그룹 설정
│       ├── notifications.html   # 알림함
│       └── characters.html      # 프로필 캐릭터 선택
```

## 실행 방법

### 1. Cordova CLI 설치 (최초 1회)
```bash
npm install -g cordova
```

### 2. 이 프로�트를 Cordova 프로젝트로 등록
이 폴더(`hawe-cordova`)를 그대로 사용하거나, 새 Cordova 프로젝트를 만들고 `www/`, `config.xml`을 이 폴더 내용으로 교체하세요.

```bash
cd hawe-cordova
cordova platform add android   # 또는 ios
```

### 3. 브라우저에서 빠르게 미리보기 (에뮬레이터 없이)
```bash
cordova platform add browser
cordova run browser
```

### 4. 실제 기기/에뮬레이터로 실행
```bash
cordova run android
# 또는
cordova run ios
```

## 다음 단계 (백엔드 연동)
현재 화면들은 정적 HTML이라 버튼을 눌러도 서버에 아무것도 저장되지 않아요. 실제 동작하게 하려면:

1. `www/js/app.js`에 `fetch()`로 Spring Boot API(`/api/...`) 호출 로직 추가
2. 미션 제출 버튼 → `POST /api/missions/{id}/submissions` 연동
3. 로그인 버튼 → 카카오/Apple SDK 연동 후 `POST /api/auth/login` 호출
4. CORS: Spring Boot 쪽에서 Cordova WebView origin(`file://` 또는 커스텀 스킴) 허용 필요

## 참고
- 원본 UI 목업(Claude 캔버스): https://claude.ai/artifact/W7qp4JAc2gf7pxtZaZBdgh
- 백엔드 설계 문서: `hawe-backend-design.md` (이전에 전달드린 파일)
