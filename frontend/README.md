# Texas Hold'em Poker Frontend

Spring Boot 백엔드(REST + 순수 WebSocket)와 통신하는 텍사스 홀덤 웹 클라이언트. 서버가 게임 규칙을 전부 판단하고, 프론트는 서버가 내려주는 상태를 그대로 그리는 데만 집중한다 — 낙관적 업데이트나 클라이언트 측 규칙 검증은 없다.

현재는 **단일 고정 테이블 MVP**이며, 이 문서는 실제로 구현된 화면/기능만 기준으로 작성했다.

## 기술 스택

![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-6-3178C6?logo=typescript&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-8-646CFF?logo=vite&logoColor=white)
![TailwindCSS](https://img.shields.io/badge/Tailwind%20CSS-4-06B6D4?logo=tailwindcss&logoColor=white)
![WebSocket](https://img.shields.io/badge/WebSocket-native-010101?logo=websocket&logoColor=white)
![Framer Motion](https://img.shields.io/badge/Framer%20Motion-installed-0055FF?logo=framer&logoColor=white)

> Framer Motion은 의존성만 설치돼 있고 아직 실제로 쓰이지 않는다(애니메이션은 다음 단계). 라우터/전역 상태 관리 라이브러리(React Router, Redux, Zustand)는 지금 규모에 과하다고 판단해 의도적으로 안 썼다 — 화면 전환은 로컬 state 토글, 전역 상태는 Context+`useReducer` 하나로 충분하다.

## 폴더 구조

```
src
├── 🌐 api            # REST 클라이언트 (httpClient, roomApi)
├── 🔌 ws             # WebSocket 훅 (연결/재연결/메시지 송수신)
├── 🗂️ state          # RoomContext — 전역 상태의 유일한 진실의 원천
├── 🃏 types          # 백엔드 DTO와 1:1 대응하는 타입
├── ♟️ game           # 순수 함수 (지금 가능한 액션 계산 등)
├── 🧮 lib            # 순수 유틸 (금액 포맷, 좌석 포지션, 족보 라벨/하이라이트)
└── 🎨 components
    ├── room          # 참가 화면, 게임 규칙 페이지
    └── table         # 포커 테이블 전체 (좌석/카드/칩/액션 UI)
```

## 주요 기능

- **실시간 포커 테이블** — 좌석 배치, 내 좌석 항상 하단 고정, 원근감 있는 크기·간격(3D 변환 없이 CSS만으로), 딜러/블라인드 포지션 배지(BTN/SB/BB/UTG/HJ/CO, 인원 변화에 대응하는 구조)
- **WebSocket 실시간 동기화** — 연결 끊김 자동 재연결(1회) + 수동 재연결, 연결 상태 배지
- **액션 UI** — 체크/콜/폴드/올인 원클릭 버튼, 베팅/레이즈는 칩 프리셋으로 금액을 쌓아가는 빌더. "지금 가능한 액션"은 서버가 알려주지 않으므로 프론트가 직접 계산하되(`game/legalActions.ts`), 최종 검증은 항상 서버가 한다
- **쇼다운 결과 표시** — 족보 이름(로열플러시 별도 판정 포함), 승자 하이라이트, **실제로 승부를 결정한 카드만** 정확히 강조(무관한 킥커는 제외)
- **칩 시각화** — 보유 칩을 6단계 등급의 칩 더미 그래픽으로 표현, 핸드 종료 시 손익(+/-) 표시
- **게임 규칙 페이지** — 족보 10단계를 카드 예시 + 하이라이트로 직접 보여주며 설명

## 상태 관리 원칙

- **서버 상태가 유일한 진실의 원천**이다. `RoomContext`가 REST 응답과 WebSocket `STATE` 메시지를 모두 같은 리듀서 액션(`ROOM_STATE_RECEIVED`)으로 처리하며, 클라이언트가 미리 결과를 그려두는 낙관적 업데이트는 하지 않는다.
- `playerId`는 `localStorage`에 저장하되 **그대로 신뢰하지 않는다** — 새로고침 시 `GET /api/room`으로 서버에 실제 존재하는지 검증한 뒤에만 참가 상태로 복원하고, 없으면 지우고 참가 화면으로 되돌린다(백엔드에 세션 개념이 없어 직접 관리해야 함).
- WebSocket 연결은 `myPlayerId`가 확정된 시점에만 열고, `onMessage` 콜백의 아이덴티티가 매 렌더 바뀌어도 재연결이 일어나지 않도록 최신 콜백을 ref로 감싸 effect 의존성을 `playerId` 하나로 고정했다.

## 구현 단계

| 단계 | 내용 |
|---|---|
| 1 | Vite + React + TypeScript + Tailwind 스캐폴딩 |
| 2 | 백엔드 CORS 설정 (dev origin만 허용) |
| 3 | REST 연동 — 참가/방 상태 조회/핸드 시작 |
| 4 | WebSocket 연동 — 실시간 상태 동기화, 재연결 |
| 5 | 포커 테이블 UI — 좌석/카드/팟/포지션 배지 |
| 6 | 액션 UI — 체크/콜/벳/레이즈/폴드/올인 |
| 6.5 | 폴리싱 — 쇼다운 결과, 칩 시각화, 규칙 페이지, 다수의 버그 수정 |
| 7 | 카드/칩 애니메이션 (다음 단계, 미착수) |

## 기술적으로 흥미로웠던 부분

- **원근감을 3D 변환 없이 구현**: 테이블을 실제로 기울이는 CSS 3D(`perspective`+`rotateX`)도 시도해봤지만, 텍스트/카드가 같이 기울어져 가독성이 떨어지는 문제가 있었다. 대신 좌석 좌표 계산(`computeSeatPositions`)에서 나(하단)에 가까운 좌석은 넓게·크게, 먼 좌석은 좁게·작게 배치하는 "가짜 원근감"으로 정리했다 — 앉아서 보는 느낌은 유지하면서 텍스트는 항상 수평.
- **쇼다운 하이라이트의 "의미 있는 카드만" 판정**: 서버가 내려주는 `bestFive`를 그대로 다 하이라이트하면 원페어처럼 킥커가 섞인 족보에서 승부와 무관한 카드까지 강조되는 문제가 있었다. 족보별로 실제 조합을 구성하는 카드만(원페어=페어+최상위 킥커 1장, 트리플/포카드=조합 카드만, 스트레이트/플러시/풀하우스=5장 전부) 골라내는 로직(`getHighlightedCards`)으로 해결했다.
- **REST/WebSocket 이원화가 만든 버그들**: "핸드 시작" REST 응답이 요청자를 모른 채(관전자 시점) 만들어져서 클릭한 사람 화면에서 순간적으로 본인 카드가 사라지던 문제, 폴드로 핸드가 조기 종료됐을 때 이미 끝난 베팅 라운드의 잔여 상태(`currentActorId`)가 응답에 남아 상대방 화면에 "아직 내 차례"처럼 보이던 문제 — 둘 다 REST와 WebSocket이 같은 게임 상태를 서로 다른 시점에 반영하면서 생긴 문제였고, 백엔드 응답을 개인화하거나 상태를 명시적으로 정리하는 방식으로 고쳤다.

## 아직 구현하지 않은 것 (의도적으로 미룸)

- **카드/칩 애니메이션**: 커뮤니티 카드 등장, 칩이 좌석에서 팟으로 이동하는 연출, 액션(콜/레이즈 등)이 순간적으로 사라지지 않고 잠깐 보인 뒤 다음 스트리트로 넘어가는 페이싱. 전부 다음 단계 몫이다.
- **홀카드 뒷면 → 클릭 오픈/커버 토글**: 실제 홀덤처럼 내 카드를 뒤집어 확인하는 상호작용은 아직 없다(지금은 내 카드가 항상 앞면으로 보임).
- **턴 타이머 / 연결 끊김 자동 폴드**: 시간 제한이 없어 한 명이 응답하지 않으면 게임이 무한정 멈춘다.
- **ready 시스템**: "핸드 시작"은 아직 방에 있는 아무나 눌러도 전원이 강제로 시작된다.
- **방 생성/멀티룸/회원가입**: 지금은 서버 전체에 고정된 단일 방 하나뿐이고, 로그인/게스트 계정 개념이 없다.
