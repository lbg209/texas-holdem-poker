# Texas Hold'em Poker Backend

텍사스 홀덤 포커를 서버 권위(server-authoritative) 방식으로 진행하는 게임 서버. 카드 셔플/딜, 족보 판정, 베팅 라운드 진행, 사이드팟 계산, 쇼다운까지 모든 게임 규칙을 서버가 판단하고, 클라이언트는 REST/WebSocket으로 상태를 조회·조작만 한다.

현재는 **단일 고정 테이블 MVP**(로비/멀티룸/로그인 없음)이다. 프론트엔드(`../frontend`)는 별도 React 클라이언트로 구현돼 있으며 자세한 내용은 [frontend/README.md](../frontend/README.md) 참고. 이 문서는 백엔드에서 지금까지 구현된 내용만 기준으로 작성한다.

## 기술 스택 및 개발 환경

![Java](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?logo=springboot&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-Build-02303A?logo=gradle&logoColor=white)
![Jackson](https://img.shields.io/badge/Jackson-3.x-000000)
![JUnit5](https://img.shields.io/badge/JUnit-5-25A162?logo=junit5&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-planned-lightgrey?logo=mysql&logoColor=white)

> Jackson 3.x부터 패키지 경로가 `com.fasterxml.jackson.*` → `tools.jackson.*`로 변경됨. MySQL/JPA 의존성은 `build.gradle`에 주석 처리만 되어 있고 아직 활성화하지 않음(모든 상태는 서버 메모리에 존재).

## 패키지 구조

```
com.lbg0146.backend
├── 🃏 card                  # 카드/덱, 셔플
├── ♠️ hand                  # 족보 판정 (순수 로직)
├── 👤 player                # 플레이어 상태/액션
├── 🪑 room                  # 단일 테이블 상태
│   └── 🌐 controller        # REST 컨트롤러 / DTO
│       └── dto
├── ♟️ game                  # 베팅 라운드 / 사이드팟 / 핸드 진행
├── 🔌 websocket             # 실시간 연결 / 액션 프로토콜 / 브로드캐스트 / 턴·자동시작·머크 타이머
│   └── dto
└── ⚠️ exception             # 도메인 예외 계층
```

`card`~`game`은 Spring 의존이 없는 순수 도메인 로직, `room.controller`/`websocket`이 REST/WebSocket 연결 계층 — 역할별이 아닌 기능별(package-by-feature) 구성.

## 구현 내용

### 1. 카드/덱 (`card`)

`Suit`, `Rank`(랭크별 정수 값 보유), `Card`(record, `A♠` 형태의 `toString()`), `Deck`(52장 생성, `Collections.shuffle` 기반 셔플, 리스트 끝에서 제거하는 `draw()`로 O(1) 드로우).

### 2. 족보 판정 (`hand`)

**포트폴리오 관점에서 가장 핵심적인 순수 로직.** `HandEvaluator.evaluate(List<Card>)`는 카드 5장 이상(실전에서는 홀카드 2장 + 커뮤니티 5장 = 7장)을 받아:

1. 가능한 모든 5장 조합(최대 `C(7,5)=21`가지)을 생성
2. 각 조합을 랭크 카운팅 + 플러시/스트레이트 판정으로 평가
3. `EvaluatedHand`(등급 + tiebreaker 리스트)끼리 `Comparable`로 비교해 최댓값을 채택

성능보다 정확성/단순성을 우선한 완전 탐색 방식이다. `HandRank`는 9단계 enum이며, 로열플러시는 별도 값을 두지 않고 "에이스 하이 스트레이트 플러시"로 `STRAIGHT_FLUSH`에 통합했다(ordinal 비교 단순화). A-2-3-4-5 휠 스트레이트(에이스를 1로 취급)도 별도 분기로 처리한다. 동급 판정 시 tiebreaker를 앞에서부터 순서대로 비교해 스플릿 팟(동점) 여부까지 정확히 가른다.

### 3. 플레이어/방/게임 진행 (`player`, `room`, `game`)

- **`Player`**: `commitChips(amount)`가 칩 이동의 유일한 진입점 — 보유 칩보다 많이 요청하면 가진 만큼만 차감하고 자동으로 `ALL_IN` 전환. 스트리트별 베팅액(`currentRoundBet`, 라운드마다 리셋)과 핸드 전체 누적 기여액(`totalHandContribution`, 사이드팟 계산용)을 분리해서 관리한다. 이번 스트리트의 마지막 액션(`lastAction`, 라운드 전환 시 초기화)과 핸드 시작 시점 칩(`chipsAtHandStart`, 종료 후 손익 계산 기준)도 함께 추적한다.
- **`Room`**: 단일 테이블의 상태(좌석/커뮤니티 카드/팟/덱/페이즈/버튼 위치)를 담는다. 전원 폴드로 핸드가 조기 종료됐는지(`wonByFold`)와 가장 최근 쇼다운 결과(`lastShowdownResult`, 다음 핸드 시작 전까지 유지)도 여기서 들고 있는데, 둘 다 진행 로직 자체는 갖지 않고 `GameEngine`이 기록만 위임하는 상태 홀더다.
- **`BettingRound`**: 한 스트리트의 베팅 진행을 담당하는 상태 기계. `Deque<PendingActor>`로 액션 순서를 관리하며, **short all-in에 따른 레이즈 재오픈 규칙**을 정확히 구현한 것이 이 프로젝트의 핵심 기술 포인트다:
  - 정상 레이즈(최소 레이즈 폭 이상) → 이미 액션한 전원을 레이즈 권한과 함께 재소환(`reopenFully`)
  - 최소 레이즈 폭에 못 미치는 올인(short all-in) → `currentBet`만 올리고 `minimumRaise`는 갱신하지 않으며, 이미 액션한 플레이어는 콜/폴드만 다시 허용(`reopenCallFoldOnly`) — 레이즈 권한은 열리지 않음
  - 액션 검증을 큐에서 꺼내기 **전에** 수행하도록 설계해, 잘못된 액션 시도가 실제 턴 순서를 깨뜨리지 않게 함
  - BET/RAISE 금액은 `Room.BET_UNIT`(100) 단위로만 허용하고, 보유 칩 전부를 정확히 거는 경우(사실상 올인)만 예외로 허용한다
- **`PotCalculator`**: `totalHandContribution` 기준으로 기여 금액 구간을 나눠 메인팟/사이드팟을 계산한다. 예) A·D가 1700, B가 500(폴드), C가 700(올인)을 기여하면 → 700 구간까지는 A/B/C/D 전원 참여한 메인팟(2,600, 단 B는 폴드라 수령 자격 없음), 700 초과분은 A/D만 자격이 있는 사이드팟(2,000)으로 분리된다.
- **`GameEngine`**: 블라인드 포스팅 → 홀카드 딜 → 베팅 라운드 → (필요 시) 커뮤니티 카드 오픈 → 쇼다운까지 한 핸드 전체를 조율한다. 헤즈업(2인)은 버튼이 곧 스몰블라인드이자 첫 액션자라는 예외 규칙을 별도 처리하고, 베팅 가능한 플레이어가 1명 이하로 남으면(나머지 전원 올인) 새 베팅 라운드 없이 커뮤니티 카드만 순서대로 오픈하는 올인 런아웃도 지원한다. 칩이 있는 플레이어가 2명 미만이면 새 핸드 시작을 거부하고, 스플릿팟의 잔돈(나눠떨어지지 않는 1칩 단위)은 좌석 등록 순서가 아니라 **버튼 왼쪽에서 가장 가까운 승자부터** 배분한다(실제 포커의 odd chip rule).

### 4. REST API (`room.controller`)

REST는 "매 순간 진행형 상태가 아닌, 요청-응답으로 충분한 동작"만 담당한다.

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/room` | 방 상태 조회. `playerId` 쿼리 파라미터로 본인 시점(자기 홀카드만 공개) 조회, 생략 시 관전자 시점 |
| POST | `/api/room/players` | 방 참가. 요청 바디 `{"nickname": "..."}`, 서버가 UUID `playerId`를 발급해 응답 |
| POST | `/api/room/hands` | 새 핸드 시작. `playerId` 쿼리 파라미터를 주면 응답도 `GET /api/room`과 동일하게 그 사람 시점으로 개인화된다(안 주면 관전자 시점) |

응답 DTO(`RoomStateResponse`)는 `Card`/`Pot` 같은 도메인 객체를 직접 노출하지 않고 `CardView`/`PotView`/`PlayerView`로 감싼다(아래 "주요 설계 결정" 참고). 예외는 `@RestControllerAdvice`(`PokerExceptionHandler`)가 도메인 예외를 HTTP 상태 코드로 변환한다.

### 5. WebSocket 연결 (`websocket`)

STOMP 대신 **Raw WebSocket**(`TextWebSocketHandler`)을 `/ws` 경로에 등록했다. 실시간으로 계속 바뀌는 게임 진행 상태(베팅 액션, 커뮤니티 카드 공개)는 WebSocket이, 그때그때 요청하는 동작(참가, 핸드 시작)은 REST가 맡는 방식으로 역할을 나눴다. 연결 시 `?playerId=<uuid>` 쿼리 파라미터로 세션과 플레이어를 연결하며, 파라미터가 없으면 관전자로 연결되고(REST의 관전자 조회와 동일한 규칙), 존재하지 않는 `playerId`가 오면 연결 자체를 거부한다.

### 6. 게임 액션 메시지 프로토콜

클라이언트 → 서버, 서버 → 클라이언트 메시지 모두 `type` 필드로 구분되는 JSON 봉투를 사용한다(향후 CHAT 등 다른 메시지 종류 확장을 고려).

```json
// 클라이언트 → 서버
{ "type": "ACTION", "action": "BET", "amount": 100 }

// 서버 → 클라이언트
{ "type": "STATE", "state": { ...RoomStateResponse... } }
{ "type": "ERROR", "message": "차례가 아닙니다." }
```

`action`은 `CHECK` / `CALL` / `BET` / `RAISE` / `FOLD` / `ALL_IN` 중 하나이며, 실제 규칙 검증은 전부 `GameEngine`/`BettingRound`가 수행한다. `type`이 `ACTION`이 아니거나 관전자가 액션을 보내면 연결은 유지한 채 해당 세션에만 에러를 응답한다.

### 7. 브로드캐스트 (`RoomBroadcaster`)

**단순히 같은 메시지를 전원에게 뿌리는 방식이 아니다.** 홀카드 노출 규칙(본인 카드는 항상 공개, 타인 카드는 쇼다운에서 폴드하지 않은 경우에만 공개)이 요청자마다 다르기 때문에, 연결된 세션마다 **각자의 `playerId` 기준으로 상태를 다시 계산해서 개별 전송**한다. REST로 참가하거나 핸드를 시작했을 때도 WebSocket으로 연결된 클라이언트 전원에게 즉시 브로드캐스트된다.

### 8. 동시성 처리

`Room`/`BettingRound`의 내부 컬렉션은 스레드 세이프하지 않은데, REST와 WebSocket 양쪽에서 동시에 같은 `GameEngine` 싱글톤을 건드릴 수 있다. 이를 막기 위해:

- `GameEngine`의 상태를 바꾸는 모든 진입점(`startHand`, `applyAction`, `addPlayer`)을 `synchronized`로 보호
- 상태를 **읽기만** 하는 외부 코드(REST 상태 조회, WebSocket 브로드캐스트, 연결 시 검증)도 `GameEngine.withLock(Supplier<T>)`을 거치도록 통일해, 진행 중인 쓰기와 겹쳐 읽는 일이 없게 함
- 락은 `GameEngine` 인스턴스 단위라서, 실제 "누구 차례인지"는 `BettingRound`의 액션 큐가 이미 보장하고 있음 — 락의 역할은 순서 강제가 아니라 동시 접근으로 인한 자료구조 손상 방지

### 9. 쇼다운 결과 노출

실제 쇼다운(카드 비교)까지 간 경우, `GameEngine.resolveShowdown()`이 계산한 결과(`ShowdownResult`)를 `Room.lastShowdownResult`에 저장해뒀다가 `RoomStateResponse.showdownHands`로 노출한다. 폴드로 핸드가 끝난 경우(`wonByFold=true`)는 실제로 카드를 비교한 적이 없으므로 이 필드가 비어 있다.

```json
"showdownHands": [
  { "playerId": "...", "handRank": "TWO_PAIR", "bestFive": [ ...CardView... ], "isWinner": true },
  { "playerId": "...", "handRank": "ONE_PAIR", "bestFive": [ ...CardView... ], "isWinner": false }
]
```

`handRank`는 `HandEvaluator`가 판정한 9단계 enum 그대로 내려주고, 로열플러시 같은 표시상의 세분화나 한글 라벨링은 프론트엔드 책임으로 남겨뒀다(백엔드는 게임 규칙만, 표현은 클라이언트 책임이라는 원칙). 헤즈업 머크(13번 참고)로 아직 공개되지 않았거나 끝내 공개하지 않은 플레이어는 `handRank`/`bestFive`가 `null`로 내려간다 — `isWinner`만은 그와 무관하게 항상 실제 결과를 정확히 반영해서, 카드는 안 보여도 승자 판정/팟 지급은 정상 작동한다.

### 10. 턴 타이머 + 시간 초과 자동 폴드

액션 차례인 플레이어가 60초 안에 액션하지 않으면 자동으로 폴드 처리한다. `TurnTimerService`가 상태가 바뀔 때마다(`RoomBroadcaster.broadcastState()`) 지금 액션자가 실제로 바뀌었는지 확인해서 타이머를 다시 예약하고, 타이머가 만료되면 `GameEngine.autoFoldIfStillWaitingOn(BettingRound, playerId)`이 **예약 시점에 캡처해둔 `BettingRound` 인스턴스/액션자와 지금 상태가 정확히 같을 때만** 폴드를 적용한다. 이미 액션했거나 스트리트가 넘어갔으면(새 `BettingRound` 인스턴스로 교체됐으면) 조용히 무시되므로, 실제 액션과 타이머 만료가 동시에 들어와도 `synchronized` 메서드 안에서 원자적으로 처리되어 경쟁 상태가 없다.

### 11. 레디 시스템 + 자동 시작

`Player.ready`는 핸드 진행 중에도 자유롭게 토글할 수 있고, 다음 핸드가 끝나도 초기화되지 않는 영속 플래그다(이번 핸드에는 영향을 주지 않고 "다음 핸드 자동 시작"에만 반영). 파산(BUSTED)하지 않은 플레이어 전원이 레디하면 `AutoStartService`가 자동으로 다음 핸드를 시작한다 — 아직 한 번도 핸드를 시작한 적 없으면(방금 다 같이 입장한 경우) 1초, 핸드가 끝난 뒤 다음 핸드를 기다리는 경우엔 결과를 확인할 시간을 주기 위해 5초를 기다린다.

### 12. 게임 종료(GAME OVER) 감지

핸드가 완전히 종료된 시점(`phase`가 `null`이거나 `SHOWDOWN`, 진행 중인 베팅 라운드 없음)에 칩을 보유한 플레이어가 정확히 1명이면 `GameEngine.resolveWinnerId()`가 그 id를 반환한다. 핸드 도중 올인으로 일시적으로 `chips=0`인 플레이어가 있어도(아직 승부가 나지 않았으므로) 섣불리 게임 종료로 오판하지 않도록 판단 시점을 엄격히 제한했다.

### 13. 헤즈업 쇼다운 머크 / 카드 공개

실제 쇼다운(폴드 없이 카드 비교)에 도달했을 때 겨루는 인원이 정확히 2명이면, 곧바로 승자를 공개하지 않는다. 서버가 내부적으로 결과는 미리 계산해두되(`computeShowdownResult`), 무작위로 한 명은 자동 공개하고 나머지 한 명에게 공개/머크를 8초 안에 결정하게 한다(`HeadsUpRevealTimerService`, 시간 초과 시 자동 공개). **결정이 끝나야 비로소 팟이 실제로 지급되고 `phase`가 `SHOWDOWN`으로 확정된다** — 그 전까지는 12번(GAME OVER)이나 11번(자동 시작) 판정도 함께 보류된다("누가 이겼는지" 자체를 숨겨 긴장감을 주는 연출을 위해, `phase` 전환 자체를 결정 시점까지 늦춘 것). 폴드로 이긴 핸드의 승자도 같은 방식(`voluntarilyRevealedIds`)으로 다음 핸드 전까지 자기 카드를 자원 공개할 수 있다. 3명 이상이 쇼다운까지 가면 이 지연 없이 기존처럼 즉시 전원 공개한다(실제 포커의 "마지막 액션자부터 순서대로 공개/머크" 규칙까지는 아직 구현하지 않았다 — 머크가 실제로 팟을 포기시키는 규칙이라 캐주얼한 MVP엔 안 맞다고 판단해 보류함).

## 주요 설계 결정

- **기능별 패키지 구조**: controller/service/repository 같은 역할별 계층 대신 `card`/`hand`/`player`/`room`/`game`처럼 기능 단위로 나눴다. 도메인 로직(`card`~`game`)은 Spring을 참조하지 않아 순수 JUnit으로 검증할 수 있고, `room.controller`/`websocket`만 프레임워크 계층을 안다.
- **DTO로 도메인 객체 감싸기**: `Card`/`Pot`를 API 응답에 직접 노출하지 않는다. `Card`는 Jackson이 record 필드 그대로(`suit`, `rank`)만 직렬화해 `toString()`의 "A♠" 표현이 사라지는 문제가 있었고, `Pot.eligiblePlayerIds()`는 `Set`이라 응답마다 순서가 달라질 수 있었다. `CardView`(suit/rank/display 모두 포함), `PotView`(좌석 순서로 정렬된 리스트)로 이 문제를 해결했다.
- **`playerId`는 REST 계층의 임시 식별자**: 로그인/인증이 없는 지금 단계에서 서버가 발급하는 UUID일 뿐이고, 도메인 로직(`Room`/`GameEngine`)은 이 값이 어떻게 발급됐는지 모른다. 나중에 실제 인증이 들어와도 발급 방식만 바꾸면 되도록 설계했다.
- **최소한의 예외 계층**: 아래 "예외 처리" 참고.
- **동시성은 별도 실행자(Executor)나 메시지 큐 없이 단일 락으로 처리**: 지금은 방이 하나뿐인 MVP라 `ReentrantLock`의 타임아웃/공정성 옵션이나 액터 모델 같은 복잡한 구조가 필요 없다고 판단했다. 락이 `GameEngine` 인스턴스 단위이므로, 향후 방이 여러 개로 늘어나도(방마다 별도 `GameEngine` 인스턴스) 이 설계를 바꿀 필요가 없다.
- **REST 응답 개인화를 모든 진입점에 일관되게 적용**: 처음엔 `POST /api/room/hands`가 `playerId` 없이 관전자 시점으로만 응답해서, 그 즉시 이어지는 WebSocket 브로드캐스트(개인화됨)와 경쟁하며 순간적으로 본인 홀카드가 안 보이는 버그가 있었다. `GET /api/room`과 동일하게 `playerId` 쿼리 파라미터를 받아 개인화하는 것으로 통일해 해결했다 — REST 엔드포인트가 여러 개여도 "누가 요청했는지에 따라 응답이 달라지는" 규칙은 하나로 유지한다.
- **폴드 조기 종료 시 베팅 라운드 상태를 명시적으로 정리**: 전원 폴드로 핸드가 끝나면 `currentBettingRound`를 `null`로 비운다. 그대로 두면 이미 끝난 라운드의 `currentActorId`/`currentBet`이 응답에 남아, 아직 액션 안 한 플레이어 화면에 "내 차례"인 것처럼 잘못 보이는 문제가 있었다.
- **스케줄러 3종(턴 타이머/자동 시작/헤즈업 공개 결정)이 같은 패턴을 공유**: `TurnTimerService`/`AutoStartService`/`HeadsUpRevealTimerService` 모두 "상태 브로드캐스트마다 조건이 실제로 바뀌었을 때만 다시 예약하고, 타이머 만료 시 예약 시점의 스냅샷과 지금 상태를 비교해서 여전히 유효할 때만 실행"하는 동일한 구조다. `GameEngine`은 스케줄러의 존재 자체를 모르고(순수 도메인 로직만 노출), 이 서비스들도 `GameEngine` 외에는 아무것도 몰라서 순환 의존이 생기지 않는다.
- **쇼다운을 "계산"과 "지급" 두 단계로 분리**: 헤즈업 머크(13번)를 지원하려면 승자를 안 시점과 실제로 팟을 지급하는 시점이 달라야 한다. 기존엔 `resolveShowdown()` 하나가 계산과 지급을 동시에 했는데, 이를 `computeShowdownResult()`(순수 계산)와 `awardPots()`(칩 지급)로 쪼갠 뒤 `resolveShowdown()`은 둘을 이어서 호출하는 얇은 래퍼로 남겨서, 3명 이상 쇼다운/기존 테스트는 동작 변경 없이 그대로 통과한다.

## 예외 처리 구조

```
PokerException (RuntimeException)
├── InvalidActionException   # 현재 베팅 상태에서 허용되지 않는 액션 (차례 위반, 최소 레이즈 미달 등)
└── GameStateException       # 방/핸드가 요청을 처리할 수 없는 상태 (정원 초과, 최소 인원 미달, 잘못된 단계 전환 등)
```

새 타입을 만들지 않고 표준 예외로 충분한 곳은 그대로 뒀다 — `Deck.draw()`/`Room.moveButtonToNextSeat()`이 비정상 상태를 만났을 때는 `IllegalStateException`, `HandEvaluator.evaluate()`의 입력 검증과 `Room.findPlayer()`의 조회 실패는 `IllegalArgumentException`을 사용한다.

REST 계층(`PokerExceptionHandler`)의 매핑:

| 예외 | HTTP 상태 |
|---|---|
| `InvalidActionException` | 400 |
| `GameStateException` | 409 |
| `IllegalArgumentException` | 404 |
| `MethodArgumentNotValidException`(입력 검증 실패) | 400 |

WebSocket 계층은 연결을 끊지 않고, 문제를 일으킨 세션에만 `{"type":"ERROR", "message": "..."}`를 응답한다.

## 테스트 현황

JUnit 5 기준 총 **76개** 테스트, 전부 통과.

| 대상 | 파일 | 개수 |
|---|---|---|
| 카드/덱 | `DeckTest` | 4 |
| 족보 판정 | `HandEvaluatorTest` | 15 |
| 플레이어 | `PlayerTest` | 4 |
| 방 | `RoomTest` | 3 |
| 베팅 라운드 (short all-in, 100단위 검증 포함) | `BettingRoundTest` | 7 |
| 사이드팟 계산 | `PotCalculatorTest` | 2 |
| 핸드 오케스트레이션 (odd chip rule, zero-chip 방지, 턴 타이머, 레디/자동시작, 헤즈업 머크 포함) | `GameEngineTest` | 23 |
| 동시성 | `GameEngineConcurrencyTest` | 3 |
| REST API (쇼다운 노출, 폴드 종료 상태 정리, 헤즈업 공개 결정 흐름 포함) | `RoomControllerTest` | 10 |
| WebSocket 프로토콜 | `GameWebSocketHandlerTest` | 4 |
| Spring 컨텍스트 로딩 | `BackendApplicationTests` | 1 |

`GameEngineConcurrencyTest`는 정원 초과 동시 참가 방지, 다수의 동시 잘못된 액션 속에서 정상 액션이 정확히 한 번만 반영되는지, 반복적인 상태 읽기 중 핸드를 여러 번 시작해도 예외가 없는지를 검증한다.

## 아직 구현하지 않은 것 (의도적으로 미룸)

- **DB 연동**: MySQL/JPA 의존성은 `build.gradle`에 주석 처리만 되어 있다. 모든 상태는 서버 메모리에만 존재하며, 서버를 재시작하면 사라진다. 로그인/인증을 붙이는 시점에 실제로 필요해질 예정.
- **재접속(reconnect) 시 상태 복구**: 연결이 끊기면 세션이 그냥 해제될 뿐, 서버가 별도로 기억해두는 건 없다(프론트엔드가 재연결 시 새 WebSocket 연결로 최신 상태를 다시 받는 방식으로 대응). 다만 턴 타임아웃(위 10번)이 응답 없는 플레이어를 자동 폴드시키므로, 연결이 끊긴 사람 때문에 게임이 무한정 멈추는 문제 자체는 이미 해소되어 있다.
- **방 나가기(퇴장) 기능**: 지금은 탭을 닫아도 서버에는 좌석이 영구히 남는다. 정원(6명)이 차면 새 플레이어가 못 들어온다.
- **닉네임 중복 방지**: 같은 방에 같은 닉네임으로 여러 명이 들어올 수 있다. 화면에는 닉네임만 보이고(게임 시작 전엔 좌석 배지도 없음) 서로 다른 `playerId`인 두 사람을 구분할 방법이 없다.
- **로비/멀티룸/roomCode/인증**: 지금은 서버 전체에 고정된 단일 `Room` 하나뿐이다. 여러 방을 동시에 운영하는 기능, 로그인/인증은 전부 이후 단계다.
- **칩/블라인드 커스터마이징**: 시작 칩(30,000)과 블라인드(100/200)는 `Room`에 고정된 상수다. 방 생성 시 값을 정하는 기능은 멀티룸 도입 후 다룰 예정.
- **3명 이상 쇼다운의 순서대로 머크**: 지금 머크(13번)는 정확히 2명이 겨루는 경우만 지원한다. 실제 포커는 마지막 액션자부터 순서대로 공개/머크를 묻고 머크하면 실제로 팟을 포기하는데, 이 "진짜" 규칙까지는 캐주얼한 MVP에 안 맞다고 판단해 보류했다.
- **핸드 히스토리**: 방금 핸드가 어떻게 끝났는지 다시 볼 방법이 없다. DB 연동 이후로 미룸.
- **캐시게임 리바이 / 매치 재시작**: GAME OVER(12번) 이후엔 그냥 멈춘다. 칩을 다시 채워 재시작하는 캐시게임 모드나 새 매치 시작 기능은 없다.

## Claude Code와 함께 개발

이 백엔드는 [Claude Code](https://claude.com/claude-code)와 페어 프로그래밍 방식으로 개발했다. 매 기능마다 설계안을 먼저 제시받아 검토·승인한 뒤 구현하고, 구현 후에는 관련 테스트를 돌려 결과를 확인하는 과정을 반복했다 — 커밋 히스토리에 그 단위(기능 하나 = 커밋 하나)가 그대로 남아 있다.
