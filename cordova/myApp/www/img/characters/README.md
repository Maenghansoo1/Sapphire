# 프로필 캐릭터 이미지

`pages/characters.html` 목업 안에 직접 그려져 있던 캐릭터 SVG 8개를 **파일 하나씩으로 분리한 기초 틀**입니다.
그림은 목업과 똑같습니다(도형 그대로 옮김). 자유롭게 다듬어 주세요.

- `pages/characters.html`은 수정하지 않았습니다. 이 파일들을 화면에 연결하는 방식은 프론트에서 정해 주세요.
- 화면 안에서의 크기는 `<img>`의 `width`/`height`나 CSS로 조절하면 됩니다 (기본 80×80, `viewBox="0 0 80 80"`).

---

## 캐릭터 목록 (백엔드 DB와 맞춰야 하는 값)

| id | 이름 | svg_key | 파일 |
|---|---|---|---|
| 1 | 몽실이 | `monsili` | `monsili.svg` |
| 2 | 잎사귀 | `leaf` | `leaf.svg` |
| 3 | 곰돌이 | `bear` | `bear.svg` |
| 4 | 별이 | `star` | `star.svg` |
| 5 | 물방울 | `drop` | `drop.svg` |
| 6 | 감자 | `potato` | `potato.svg` |
| 7 | 꽃님 | `flower` | `flower.svg` |
| 8 | 필름이 | `film` | `film.svg` |

- **파일 이름 = `svg_key`** 입니다. 서버는 캐릭터를 `svgKey` 값으로 내려주고, 프론트는 그 값으로 파일을 찾습니다.
- 같은 목록이 백엔드 DB 초기 데이터(Flyway `V4__seed_characters.sql`, 준비 중)에도 들어갑니다.
- `monsili`, `bear`는 API 설계 문서 예시를 따랐고, 나머지 키는 **임시 이름**입니다. 바꾸고 싶으면 **백엔드 V4가 올라가기 전에** 알려 주세요. 올라간 뒤에는 DB 변경 파일을 새로 만들어야 합니다.

---

## 사용 예시

서버 응답 (`GET /api/characters`, `PATCH /api/me/character`)
```json
{ "id": 3, "name": "곰돌이", "svgKey": "bear" }
```

화면에서 그리기
```html
<!-- www/pages/*.html 안에서 -->
<img src="../img/characters/bear.svg" alt="곰돌이" width="66" height="66">

<!-- www/index.html 안에서 -->
<img src="img/characters/bear.svg" alt="곰돌이" width="66" height="66">
```

JS로 그리기
```js
function characterImageUrl(svgKey) {
  return `../img/characters/${svgKey}.svg`;
}
```

---

## 캐릭터를 추가·변경할 때

1. 이 폴더에 `새키.svg` 파일을 추가합니다.
2. 위 표에 한 줄 추가합니다.
3. 백엔드 담당자에게 id, 이름, svg_key를 알려 주면 DB에 추가합니다.
