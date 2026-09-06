# CLAUDE.md (backend)

이 문서는 `backend/` 디렉터리(Java/Spring Boot 게임 서버) 관련 세부 내용을 담는다.
프로젝트 전체 개요, 게임 규칙, 협업 방식, Git 정책은 상위 [../CLAUDE.md](../CLAUDE.md) 참고.

## Stack

- Java 17 + Spring Boot 4.1.1, Gradle 빌드
- DB: MySQL 사용 예정 (build.gradle에 data-jpa/mysql-connector-j 의존성 미리 추가됨)이나 **아직 연결하지 않음**
  - `Room`은 DB에 매번 저장하는 엔티티가 아니라, 서버가 켜져 있는 동안 메모리에서 관리되는 상태 객체다
  - 게임 종료 후 기록이 필요해지면 그때 별도 히스토리 테이블 저장을 고려한다

## Architecture

com.lbg0146.backend
├── card         # Card, Deck, Suit, Rank, 셔플 로직
├── hand         # HandEvaluator(족보 판정), HandRank
├── player       # Player, PlayerStatus, PlayerAction
├── room         # Room, RoomService, RoomController(REST)
├── game         # GameEngine(라운드 진행/베팅 처리), GameState, BettingRound
└── websocket    # WebSocket 설정, 메시지 핸들러, 메시지 DTO

- REST: 방 생성/조회 등 매번 요청하는 것
- WebSocket: 게임 진행 중 실시간 상태 동기화 (베팅 액션, 커뮤니티 카드 공개, 쇼다운 결과)
- 같은 Room에 대한 액션은 반드시 순서대로(직렬화) 처리되어야 한다 — 동시 액션으로 인한 게임 상태 꼬임을 막는 것이 핵심 설계 포인트

## 빌드 순서 (현재 진행 상태를 이 순서에 맞춰 판단할 것)

1. 카드/덱 (셔플, 딜) — 순수 로직, 단위 테스트
2. 족보 판정(HandEvaluator) — 가장 복잡한 순수 로직, 단위 테스트로 검증
3. Room/Player 도메인 + 라운드 진행 로직 — 아직 통신 계층 없이 순수 로직으로
4. REST로 방 상태 조회 API
5. WebSocket 연결 + 최소 HTML 테스트 페이지 (echo 수준 확인)
6. 실제 게임 액션 메시지 프로토콜 (BET/CALL/RAISE/FOLD) 및 브로드캐스트
7. 방 단위 동시성 처리 (액션 직렬화)
8. 프론트엔드 연동, 이후 로비/멀티룸 확장 검토 (MVP 이후)

## Testing

- 카드/덱, 족보 판정, 라운드 진행 로직은 서버를 띄우지 않고 순수 JUnit으로 먼저 검증한다
- WebSocket/통신 계층은 로직이 검증된 이후에 붙인다

## 상세 요구사항

- 블라인드/바이인/인원수, 레이즈 최소 단위, 사이드팟, 쇼다운 공개 순서 등 세부 규칙은 `docs/requirements.md` 참고
