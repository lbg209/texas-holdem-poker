# Texas Hold'em Poker Backend

텍사스 홀덤 포커를 서버 권위(server-authoritative) 방식으로 진행하는 게임 서버. 카드 셔플/딜, 족보 판정, 베팅 라운드 진행, 사이드팟 계산, 쇼다운까지 모든 게임 규칙을 서버가 판단하고, 클라이언트는 REST/WebSocket으로 상태를 조회·조작만 한다.

현재는 **단일 고정 테이블 MVP**(로비/멀티룸/로그인 없음)이며, 프론트엔드는 아직 구현되지 않았다. 이 문서는 백엔드에서 지금까지 구현된 내용만 기준으로 작성한다.

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
├── 🔌 websocket             # 실시간 연결 / 액션 프로토콜 / 브로드캐스트
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

- **`Player`**: `commitChips(amount)`가 칩 이동의 유일한 진입점 — 보유 칩보다 많이 요청하면 가진 만큼만 차감하고 자동으로 `ALL_IN` 전환. 스트리트별 베팅액(`currentRoundBet`, 라운드마다 리셋)과 핸드 전체 누적 기여액(`totalHandContribution`, 사이드팟 계산용)을 분리해서 관리한다.
- **`Room`**: 단일 테이블의 상태(좌석/커뮤니티 카드/팟/덱/페이즈/버튼 위치)만 담고, 진행 로직은 갖지 않는다.
- **`BettingRound`**: 한 스트리트의 베팅 진행을 담당하는 상태 기계. `Deque<PendingActor>`로 액션 순서를 관리하며, **short all-in에 따른 레이즈 재오픈 규칙**을 정확히 구현한 것이 이 프로젝트의 핵심 기술 포인트다:
  - 정상 레이즈(최소 레이즈 폭 이상) → 이미 액션한 전원을 레이즈 권한과 함께 재소환(`reopenFully`)
  - 최소 레이즈 폭에 못 미치는 올인(short all-in) → `currentBet`만 올리고 `minimumRaise`는 갱신하지 않으며, 이미 액션한 플레이어는 콜/폴드만 다시 허용(`reopenCallFoldOnly`) — 레이즈 권한은 열리지 않음
  - 액션 검증을 큐에서 꺼내기 **전에** 수행하도록 설계해, 잘못된 액션 시도가 실제 턴 순서를 깨뜨리지 않게 함
- **`PotCalculator`**: `totalHandContribution` 기준으로 기여 금액 구간을 나눠 메인팟/사이드팟을 계산한다. 예) A·D가 1700, B가 500(폴드), C가 700(올인)을 기여하면 → 700 구간까지는 A/B/C/D 전원 참여한 메인팟(2,600, 단 B는 폴드라 수령 자격 없음), 700 초과분은 A/D만 자격이 있는 사이드팟(2,000)으로 분리된다.
- **`GameEngine`**: 블라인드 포스팅 → 홀카드 딜 → 베팅 라운드 → (필요 시) 커뮤니티 카드 오픈 → 쇼다운까지 한 핸드 전체를 조율한다. 헤즈업(2인)은 버튼이 곧 스몰블라인드이자 첫 액션자라는 예외 규칙을 별도 처리하고, 베팅 가능한 플레이어가 1명 이하로 남으면(나머지 전원 올인) 새 베팅 라운드 없이 커뮤니티 카드만 순서대로 오픈하는 올인 런아웃도 지원한다.

### 4. REST API (`room.controller`)

REST는 "매 순간 진행형 상태가 아닌, 요청-응답으로 충분한 동작"만 담당한다.

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/room` | 방 상태 조회. `playerId` 쿼리 파라미터로 본인 시점(자기 홀카드만 공개) 조회, 생략 시 관전자 시점 |
| POST | `/api/room/players` | 방 참가. 요청 바디 `{"nickname": "..."}`, 서버가 UUID `playerId`를 발급해 응답 |
| POST | `/api/room/hands` | 새 핸드 시작 |

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

## 주요 설계 결정

- **기능별 패키지 구조**: controller/service/repository 같은 역할별 계층 대신 `card`/`hand`/`player`/`room`/`game`처럼 기능 단위로 나눴다. 도메인 로직(`card`~`game`)은 Spring을 참조하지 않아 순수 JUnit으로 검증할 수 있고, `room.controller`/`websocket`만 프레임워크 계층을 안다.
- **DTO로 도메인 객체 감싸기**: `Card`/`Pot`를 API 응답에 직접 노출하지 않는다. `Card`는 Jackson이 record 필드 그대로(`suit`, `rank`)만 직렬화해 `toString()`의 "A♠" 표현이 사라지는 문제가 있었고, `Pot.eligiblePlayerIds()`는 `Set`이라 응답마다 순서가 달라질 수 있었다. `CardView`(suit/rank/display 모두 포함), `PotView`(좌석 순서로 정렬된 리스트)로 이 문제를 해결했다.
- **`playerId`는 REST 계층의 임시 식별자**: 로그인/인증이 없는 지금 단계에서 서버가 발급하는 UUID일 뿐이고, 도메인 로직(`Room`/`GameEngine`)은 이 값이 어떻게 발급됐는지 모른다. 나중에 실제 인증이 들어와도 발급 방식만 바꾸면 되도록 설계했다.
- **최소한의 예외 계층**: 아래 "예외 처리" 참고.
- **동시성은 별도 실행자(Executor)나 메시지 큐 없이 단일 락으로 처리**: 지금은 방이 하나뿐인 MVP라 `ReentrantLock`의 타임아웃/공정성 옵션이나 액터 모델 같은 복잡한 구조가 필요 없다고 판단했다. 락이 `GameEngine` 인스턴스 단위이므로, 향후 방이 여러 개로 늘어나도(방마다 별도 `GameEngine` 인스턴스) 이 설계를 바꿀 필요가 없다.

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

JUnit 5 기준 총 **55개** 테스트, 전부 통과.

| 대상 | 파일 | 개수 |
|---|---|---|
| 카드/덱 | `DeckTest` | 4 |
| 족보 판정 | `HandEvaluatorTest` | 15 |
| 플레이어 | `PlayerTest` | 4 |
| 방 | `RoomTest` | 3 |
| 베팅 라운드 (short all-in 포함) | `BettingRoundTest` | 7 |
| 사이드팟 계산 | `PotCalculatorTest` | 2 |
| 핸드 오케스트레이션 | `GameEngineTest` | 6 |
| 동시성 | `GameEngineConcurrencyTest` | 3 |
| REST API | `RoomControllerTest` | 6 |
| WebSocket 프로토콜 | `GameWebSocketHandlerTest` | 4 |
| Spring 컨텍스트 로딩 | `BackendApplicationTests` | 1 |

`GameEngineConcurrencyTest`는 정원 초과 동시 참가 방지, 다수의 동시 잘못된 액션 속에서 정상 액션이 정확히 한 번만 반영되는지, 반복적인 상태 읽기 중 핸드를 여러 번 시작해도 예외가 없는지를 검증한다.

## 아직 구현하지 않은 것 (의도적으로 미룸)

- **프론트엔드**: 전혀 구현되지 않았다. `src/main/resources/static/ws-test.html`은 WebSocket 프로토콜을 수동으로 확인하기 위한 개발용 테스트 페이지일 뿐, 실제 UI가 아니다.
- **DB 연동**: MySQL/JPA 의존성은 `build.gradle`에 주석 처리만 되어 있다. 모든 상태는 서버 메모리에만 존재하며, 서버를 재시작하면 사라진다. 핸드 히스토리 저장이 실제로 필요해지는 시점에 붙일 예정.
- **쇼다운 승자/족보 명시 메시지**: 쇼다운 결과(칩 이동)는 `RoomStateResponse`의 칩 개수 변화로 간접적으로만 드러나고, "누가 어떤 족보로 이겼는지"를 명시하는 별도 메시지는 아직 없다.
- **재접속(reconnect) 처리**: 연결이 끊기면 세션이 그냥 해제될 뿐, 재접속 시 상태 복구 로직은 없다.
- **로비/멀티룸/roomCode/인증**: 지금은 서버 전체에 고정된 단일 `Room` 하나뿐이다. 여러 방을 동시에 운영하는 기능, 로그인/인증은 전부 이후 단계다.
