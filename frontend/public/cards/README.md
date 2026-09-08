# 카드 이미지 출처

`svg-cards.svg`는 [htdebeer/SVG-cards](https://github.com/htdebeer/SVG-cards)
(David Bellot의 원작 [SVG-cards](http://svg-cards.sourceforge.net/)를 Huub de Beer가
포크)에서 그대로 가져온 파일이며, **LGPL-2.1** 라이선스입니다. 전체 라이선스 원문은 같은
폴더의 `LICENSE` 파일을 참고하세요.

## 사용 방식

카드 한 장 = `<use href="/cards/svg-cards.svg#{suit}_{rank}" />` 형태로 참조합니다.

- `suit`: `spade` | `heart` | `diamond` | `club`
- `rank`: `1`(에이스) ~ `10`, `jack`, `queen`, `king`
- 뒷면: `#back` (SVG의 `fill` 속성으로 색을 바꿀 수 있음)
- 카드 원본 크기: `width 169.075`, `height 244.640` (`viewBox="0 0 169.075 244.640"`)
