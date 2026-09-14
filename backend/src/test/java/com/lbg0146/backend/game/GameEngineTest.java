package com.lbg0146.backend.game;

import com.lbg0146.backend.card.Card;
import com.lbg0146.backend.card.Rank;
import com.lbg0146.backend.card.Suit;
import com.lbg0146.backend.exception.GameStateException;
import com.lbg0146.backend.exception.InvalidActionException;
import com.lbg0146.backend.player.Player;
import com.lbg0146.backend.player.PlayerAction;
import com.lbg0146.backend.player.PlayerStatus;
import com.lbg0146.backend.room.HandHistoryEntry;
import com.lbg0146.backend.room.Phase;
import com.lbg0146.backend.room.Pot;
import com.lbg0146.backend.room.Room;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameEngineTest {

    @Test
    void 최소_인원_미만이면_핸드를_시작할_수_없다() {
        Room room = new Room();
        room.addPlayer(new Player("p1", "P1", Room.STARTING_CHIPS));
        GameEngine engine = new GameEngine(room);

        assertThrows(GameStateException.class, engine::startHand);
    }

    @Test
    void 칩이_있는_플레이어가_2명_미만이면_핸드를_시작할_수_없다() {
        Room room = new Room();
        room.addPlayer(new Player("p1", "P1", Room.STARTING_CHIPS));
        room.addPlayer(new Player("p2", "P2", 0)); // 이미 칩을 다 잃은 플레이어
        GameEngine engine = new GameEngine(room);

        assertThrows(GameStateException.class, engine::startHand);
    }

    @Test
    void 핸드를_시작하면_블라인드가_걷히고_홀카드가_2장씩_지급된다() {
        Room room = new Room();
        room.addPlayer(new Player("p1", "P1", Room.STARTING_CHIPS));
        room.addPlayer(new Player("p2", "P2", Room.STARTING_CHIPS));
        room.addPlayer(new Player("p3", "P3", Room.STARTING_CHIPS));
        GameEngine engine = new GameEngine(room);

        engine.startHand();

        assertEquals(Phase.PREFLOP, room.getPhase());
        // 버튼=p1(seat0), SB=p2, BB=p3
        assertEquals(Room.SMALL_BLIND, room.getPlayers().get(1).getCurrentRoundBet());
        assertEquals(Room.BIG_BLIND, room.getPlayers().get(2).getCurrentRoundBet());
        room.getPlayers().forEach(p -> assertEquals(2, p.getHoleCards().size()));
        assertEquals(52 - 3 * 2, room.getDeck().remainingCount());
        assertEquals(Room.BIG_BLIND, engine.getCurrentBettingRound().getCurrentBet());
    }

    @Test
    void 방_설정을_바꾸면_핸드도_그_블라인드로_진행된다() {
        Room room = new Room();
        GameEngine engine = new GameEngine(room);
        engine.configureRoom(50_000, 1000); // 빅블라인드 1000 -> 스몰블라인드 500
        room.addPlayer(new Player("p1", "P1", room.getStartingChips()));
        room.addPlayer(new Player("p2", "P2", room.getStartingChips()));

        engine.startHand(); // 헤즈업: 버튼=p1(SB), BB=p2

        assertEquals(500, room.getPlayers().get(0).getCurrentRoundBet());
        assertEquals(1000, room.getPlayers().get(1).getCurrentRoundBet());
        assertEquals(1000, engine.getCurrentBettingRound().getCurrentBet());
    }

    @Test
    void 보유_칩을_초과하는_RAISE는_거부된다() {
        Room room = new Room();
        room.addPlayer(new Player("p1", "P1", 1000));
        room.addPlayer(new Player("p2", "P2", 300));
        GameEngine engine = new GameEngine(room);

        engine.startHand(); // 헤즈업: 버튼=p1(SB,50), BB=p2(100), 프리플랍 첫 액션=p1
        engine.applyAction("p1", PlayerAction.CALL, 0); // p1이 100으로 콜, p2 차례

        // p2는 currentRoundBet 100 + 남은 칩 200 = 최대 300까지만 RAISE 가능한데 1000을 요청
        assertThrows(InvalidActionException.class, () -> engine.applyAction("p2", PlayerAction.RAISE, 1000));
    }

    @Test
    void 헤즈업에서는_버튼이_스몰블라인드이고_먼저_액션한다() {
        Room room = new Room();
        room.addPlayer(new Player("p1", "P1", Room.STARTING_CHIPS));
        room.addPlayer(new Player("p2", "P2", Room.STARTING_CHIPS));
        GameEngine engine = new GameEngine(room);

        engine.startHand();

        assertEquals(Room.SMALL_BLIND, room.getPlayers().get(0).getCurrentRoundBet());
        assertEquals(Room.BIG_BLIND, room.getPlayers().get(1).getCurrentRoundBet());
        // 순서를 어기면 거부되어야 한다 (p1이 먼저)
        assertThrows(InvalidActionException.class, () -> engine.applyAction("p2", PlayerAction.CALL, 0));
    }

    @Test
    void 전원_폴드로_한_명만_남으면_즉시_핸드가_종료되고_팟을_받는다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", 1000));
        room.addPlayer(new Player("b", "B", 1000));
        room.addPlayer(new Player("c", "C", 1000));
        GameEngine engine = new GameEngine(room);

        engine.startHand(); // 버튼=a, SB=b, BB=c, 프리플랍 첫 액션=a(UTG)

        engine.applyAction("a", PlayerAction.FOLD, 0);
        engine.applyAction("b", PlayerAction.FOLD, 0);

        assertEquals(Phase.SHOWDOWN, room.getPhase());
        // c는 BB를 내고 남은 칩(1000-BIG_BLIND)에 팟 전체(SB+BB)를 더 받는다.
        assertEquals(1000 - Room.BIG_BLIND + Room.SMALL_BLIND + Room.BIG_BLIND, room.findPlayer("c").getChips());
        assertTrue(room.isWonByFold());
        // 베팅 라운드가 도중에 끝났으므로, 아직 액션 안 한 c가 조회 응답에서 "내 차례"로 잘못 남으면 안 된다.
        assertNull(engine.getCurrentBettingRound());
    }

    @Test
    void 핸드_도중_입장한_플레이어는_폴드승_인원_집계에서_제외된다() {
        Room room = new Room();
        room.addPlayer(new Player("p1", "P1", Room.STARTING_CHIPS));
        room.addPlayer(new Player("p2", "P2", Room.STARTING_CHIPS));
        room.addPlayer(new Player("p3", "P3", Room.STARTING_CHIPS));
        GameEngine engine = new GameEngine(room);

        engine.startHand(); // 버튼=p1, SB=p2, BB=p3, 프리플랍 첫 액션=p1
        engine.applyAction("p1", PlayerAction.CALL, 0);
        engine.applyAction("p2", PlayerAction.CALL, 0);
        engine.applyAction("p3", PlayerAction.CHECK, 0);
        assertEquals(Phase.FLOP, room.getPhase());

        // 핸드가 진행되는 도중 새로 입장 — 이번 핸드는 딜을 받지 않았다.
        room.addPlayer(new Player("p4", "P4", Room.STARTING_CHIPS));

        // p2(플랍 첫 액션자)와 p3가 폴드하면, 원래 이 핸드에 있던 p1 혼자 남아 즉시 폴드승으로
        // 끝나야 한다 — p4가 "아직 안 죽은 사람"으로 잘못 집계되어 턴/리버까지 계속 진행되면 버그다.
        engine.applyAction("p2", PlayerAction.FOLD, 0);
        engine.applyAction("p3", PlayerAction.FOLD, 0);

        assertEquals(Phase.SHOWDOWN, room.getPhase());
        assertTrue(room.isWonByFold());
        assertEquals(3, room.getCommunityCards().size()); // 턴/리버로 안 밀리고 플랍에서 바로 종료돼야 함
        assertTrue(room.findPlayer("p4").getHoleCards().isEmpty());
    }

    @Test
    void 쇼다운에서_더_높은_족보가_팟_전체를_가져간다() {
        Room room = new Room();
        Player a = new Player("a", "A", 10_000);
        Player b = new Player("b", "B", 10_000);
        room.addPlayer(a);
        room.addPlayer(b);
        GameEngine engine = new GameEngine(room);

        a.receiveHoleCard(new Card(Suit.SPADE, Rank.ACE));
        a.receiveHoleCard(new Card(Suit.SPADE, Rank.KING));
        b.receiveHoleCard(new Card(Suit.HEART, Rank.TWO));
        b.receiveHoleCard(new Card(Suit.CLUB, Rank.THREE));
        room.getCommunityCards().addAll(List.of(
                new Card(Suit.SPADE, Rank.QUEEN),
                new Card(Suit.SPADE, Rank.JACK),
                new Card(Suit.SPADE, Rank.TEN),
                new Card(Suit.DIAMOND, Rank.FOUR),
                new Card(Suit.CLUB, Rank.FIVE)
        ));
        a.commitChips(1000);
        b.commitChips(1000);

        ShowdownResult result = engine.resolveShowdown();

        assertEquals(1, result.potResults().size());
        ShowdownResult.PotResult potResult = result.potResults().get(0);
        assertEquals(List.of(a), potResult.winners());
        assertEquals(2000, potResult.pot().amount());
        assertEquals(11_000, a.getChips());
        assertEquals(9_000, b.getChips());
    }

    @Test
    void 쇼다운에서_동점이면_팟을_균등하게_나눈다() {
        Room room = new Room();
        Player a = new Player("a", "A", 10_000);
        Player b = new Player("b", "B", 10_000);
        room.addPlayer(a);
        room.addPlayer(b);
        GameEngine engine = new GameEngine(room);

        // 둘 다 커뮤니티 카드로만 하이카드 승부 (동일한 5장)
        a.receiveHoleCard(new Card(Suit.CLUB, Rank.TWO));
        a.receiveHoleCard(new Card(Suit.CLUB, Rank.THREE));
        b.receiveHoleCard(new Card(Suit.DIAMOND, Rank.TWO));
        b.receiveHoleCard(new Card(Suit.DIAMOND, Rank.THREE));
        room.getCommunityCards().addAll(List.of(
                new Card(Suit.SPADE, Rank.ACE),
                new Card(Suit.SPADE, Rank.KING),
                new Card(Suit.SPADE, Rank.NINE),
                new Card(Suit.SPADE, Rank.FIVE),
                new Card(Suit.HEART, Rank.FOUR)
        ));
        a.commitChips(1000);
        b.commitChips(1000);

        ShowdownResult result = engine.resolveShowdown();

        ShowdownResult.PotResult potResult = result.potResults().get(0);
        assertEquals(2, potResult.winners().size());
        assertEquals(10_000, a.getChips());
        assertEquals(10_000, b.getChips());
    }

    @Test
    void 스플릿팟의_잔돈은_버튼_왼쪽에서_가장_가까운_승자부터_배분된다() {
        Room room = new Room();
        Player a = new Player("a", "A", 10_000);
        Player b = new Player("b", "B", 10_000);
        Player c = new Player("c", "C", 10_000);
        Player d = new Player("d", "D", 10_000);
        room.addPlayer(a);
        room.addPlayer(b);
        room.addPlayer(c);
        room.addPlayer(d);
        room.moveButtonToNextSeat(); // 버튼 = a(0번 좌석) -> 왼쪽부터 b, c, d 순

        GameEngine engine = new GameEngine(room);

        // a, b, c는 커뮤니티 카드로만 동일한 하이카드 승부(3파전 동점). d는 콜 후 폴드해서
        // 팟에는 기여했지만 승부에는 끼지 못하는 조건을 만들어 배당액이 3으로 안 나눠떨어지게 한다.
        a.receiveHoleCard(new Card(Suit.CLUB, Rank.TWO));
        a.receiveHoleCard(new Card(Suit.CLUB, Rank.THREE));
        b.receiveHoleCard(new Card(Suit.DIAMOND, Rank.TWO));
        b.receiveHoleCard(new Card(Suit.DIAMOND, Rank.THREE));
        c.receiveHoleCard(new Card(Suit.HEART, Rank.TWO));
        c.receiveHoleCard(new Card(Suit.HEART, Rank.THREE));
        room.getCommunityCards().addAll(List.of(
                new Card(Suit.SPADE, Rank.ACE),
                new Card(Suit.SPADE, Rank.KING),
                new Card(Suit.SPADE, Rank.NINE),
                new Card(Suit.SPADE, Rank.FIVE),
                new Card(Suit.HEART, Rank.FOUR)
        ));
        a.commitChips(1000);
        b.commitChips(1000);
        c.commitChips(1000);
        d.commitChips(500);
        d.fold();

        engine.resolveShowdown();

        // 팟 3500(=1000*3+500)이 a/b/c 3명에게 2000+1500 두 팟으로 나뉘어 배당되는데,
        // 정확히 안 나눠떨어지는 잔돈(2000짜리 팟에서 2개)은 버튼(a) 왼쪽부터 b, c 순으로 먼저 받는다.
        assertEquals(10_000 - 1000 + 1166, a.getChips(), "버튼은 잔돈을 가장 나중에 받는다");
        assertEquals(10_000 - 1000 + 1167, b.getChips());
        assertEquals(10_000 - 1000 + 1167, c.getChips());
    }

    @Test
    void 예약된_라운드와_액션자가_여전히_유효하면_자동_폴드된다() {
        Room room = new Room();
        room.addPlayer(new Player("p1", "P1", Room.STARTING_CHIPS));
        room.addPlayer(new Player("p2", "P2", Room.STARTING_CHIPS));
        GameEngine engine = new GameEngine(room);
        engine.startHand(); // 헤즈업: 첫 액션자 = p1(버튼/SB)

        BettingRound roundAtScheduleTime = engine.getCurrentBettingRound();
        boolean folded = engine.autoFoldIfStillWaitingOn(roundAtScheduleTime, "p1");

        assertTrue(folded);
        assertEquals(PlayerStatus.FOLDED, room.findPlayer("p1").getStatus());
        assertTrue(room.findPlayer("p1").isAutoFolded(), "타임아웃으로 인한 폴드는 autoFolded로 표시되어야 한다");
    }

    @Test
    void 직접_폴드한_경우는_autoFolded로_표시되지_않는다() {
        Room room = new Room();
        room.addPlayer(new Player("p1", "P1", Room.STARTING_CHIPS));
        room.addPlayer(new Player("p2", "P2", Room.STARTING_CHIPS));
        room.addPlayer(new Player("p3", "P3", Room.STARTING_CHIPS));
        GameEngine engine = new GameEngine(room);
        engine.startHand();

        String actorId = engine.getCurrentBettingRound().getCurrentActorId().orElseThrow();
        engine.applyAction(actorId, PlayerAction.FOLD, 0);

        assertEquals(PlayerStatus.FOLDED, room.findPlayer(actorId).getStatus());
        assertFalse(room.findPlayer(actorId).isAutoFolded());
    }

    @Test
    void 그_사이_이미_액션했으면_자동_폴드를_무시한다() {
        Room room = new Room();
        room.addPlayer(new Player("p1", "P1", Room.STARTING_CHIPS));
        room.addPlayer(new Player("p2", "P2", Room.STARTING_CHIPS));
        GameEngine engine = new GameEngine(room);
        engine.startHand();

        BettingRound roundAtScheduleTime = engine.getCurrentBettingRound();
        engine.applyAction("p1", PlayerAction.CALL, 0); // p1이 이미 액션함 -> 차례가 p2로 넘어감

        boolean folded = engine.autoFoldIfStillWaitingOn(roundAtScheduleTime, "p1");

        assertFalse(folded, "이미 액션한 사람에 대한 타이머는 무시되어야 한다");
        assertEquals(PlayerStatus.ACTIVE, room.findPlayer("p1").getStatus());
    }

    @Test
    void 그_사이_새_스트리트로_넘어갔으면_자동_폴드를_무시한다() {
        Room room = new Room();
        room.addPlayer(new Player("p1", "P1", Room.STARTING_CHIPS));
        room.addPlayer(new Player("p2", "P2", Room.STARTING_CHIPS));
        GameEngine engine = new GameEngine(room);
        engine.startHand();

        BettingRound preflopRound = engine.getCurrentBettingRound();
        engine.applyAction("p1", PlayerAction.CALL, 0);
        engine.applyAction("p2", PlayerAction.CHECK, 0); // 프리플랍 종료 -> 플랍으로 새 BettingRound 생성

        boolean folded = engine.autoFoldIfStillWaitingOn(preflopRound, "p2");

        assertFalse(folded, "이미 지나간 스트리트의(오래된 BettingRound 인스턴스) 타이머는 무시되어야 한다");
    }

    @Test
    void 파산한_플레이어는_전원_레디_판정에서_제외된다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", 1000));
        room.addPlayer(new Player("b", "B", 0)); // 시작부터 칩이 없음 -> 첫 핸드에서 바로 BUSTED로 전환됨
        room.addPlayer(new Player("c", "C", 1000));
        GameEngine engine = new GameEngine(room);

        engine.startHand(); // 버튼=a, SB=b는 파산이라 건너뛰고 실제로는 a/c만 참여
        assertEquals(PlayerStatus.BUSTED, room.findPlayer("b").getStatus());
        engine.applyAction(engine.getCurrentBettingRound().getCurrentActorId().orElseThrow(), PlayerAction.FOLD, 0);
        assertEquals(Phase.SHOWDOWN, room.getPhase());

        // b(파산)는 레디를 안 했지만, a/c만 레디하면 전원 레디로 판정되어야 한다.
        engine.setReady("a", true);
        engine.setReady("c", true);

        assertTrue(engine.isWaitingForNextHandWithEveryoneReady());
    }

    @Test
    void 레디하지_않은_생존_플레이어가_있으면_자동_시작_대상이_아니다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", 1000));
        room.addPlayer(new Player("b", "B", 1000));
        GameEngine engine = new GameEngine(room);

        engine.startHand();
        engine.applyAction(engine.getCurrentBettingRound().getCurrentActorId().orElseThrow(), PlayerAction.FOLD, 0);
        assertEquals(Phase.SHOWDOWN, room.getPhase());

        engine.setReady("a", true); // b는 레디 안 함

        assertFalse(engine.isWaitingForNextHandWithEveryoneReady());
        assertFalse(engine.autoStartIfStillReady());
    }

    @Test
    void 아직_한_번도_핸드를_시작한_적_없어도_전원_레디면_자동_시작_대상이다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", 1000));
        room.addPlayer(new Player("b", "B", 1000));
        GameEngine engine = new GameEngine(room);
        // phase가 아직 null인 상태 — 첫 핸드조차 시작 전이라도 전원 레디면 자동 시작 대상이어야 한다.

        engine.setReady("a", true);
        engine.setReady("b", true);

        assertTrue(engine.isWaitingForNextHandWithEveryoneReady());
        assertTrue(engine.autoStartIfStillReady());
        assertEquals(Phase.PREFLOP, room.getPhase());
    }

    @Test
    void 전원_레디면_자동_시작이_실제로_새_핸드를_시작한다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", 1000));
        room.addPlayer(new Player("b", "B", 1000));
        GameEngine engine = new GameEngine(room);

        engine.startHand();
        engine.applyAction(engine.getCurrentBettingRound().getCurrentActorId().orElseThrow(), PlayerAction.FOLD, 0);
        engine.setReady("a", true);
        engine.setReady("b", true);

        boolean started = engine.autoStartIfStillReady();

        assertTrue(started);
        assertEquals(Phase.PREFLOP, room.getPhase(), "자동 시작으로 다음 핸드가 실제로 시작되어야 한다");
    }

    @Test
    void 그_사이_레디를_취소했으면_자동_시작을_무시한다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", 1000));
        room.addPlayer(new Player("b", "B", 1000));
        GameEngine engine = new GameEngine(room);

        engine.startHand();
        engine.applyAction(engine.getCurrentBettingRound().getCurrentActorId().orElseThrow(), PlayerAction.FOLD, 0);
        engine.setReady("a", true);
        engine.setReady("b", true);
        engine.setReady("b", false); // 카운트다운 도중 b가 취소

        assertFalse(engine.autoStartIfStillReady(), "예약 시점 이후 조건이 깨졌으면 자동 시작하면 안 된다");
    }

    @Test
    void 폴드승_승자는_자기_카드를_자원해서_공개할_수_있다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", 1000));
        room.addPlayer(new Player("b", "B", 1000));
        GameEngine engine = new GameEngine(room);

        engine.startHand();
        String winnerId = engine.getCurrentBettingRound().getCurrentActorId().orElseThrow().equals("a") ? "b" : "a";
        engine.applyAction(engine.getCurrentBettingRound().getCurrentActorId().orElseThrow(), PlayerAction.FOLD, 0);

        assertFalse(room.getVoluntarilyRevealedIds().contains(winnerId));
        engine.revealFoldWinHand(winnerId);
        assertTrue(room.getVoluntarilyRevealedIds().contains(winnerId));
    }

    @Test
    void 폴드승_승자가_아니면_카드_공개가_거부된다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", 1000));
        room.addPlayer(new Player("b", "B", 1000));
        GameEngine engine = new GameEngine(room);

        engine.startHand();
        String actorId = engine.getCurrentBettingRound().getCurrentActorId().orElseThrow();
        String loserId = actorId; // 지금 액션할 사람이 폴드하니, 그 사람이 패자다
        engine.applyAction(actorId, PlayerAction.FOLD, 0);

        assertThrows(GameStateException.class, () -> engine.revealFoldWinHand(loserId));
    }

    // 리버까지 체크/콜로만 진행해서 헤즈업 쇼다운(정확히 2명)에 도달시킨다.
    private void checkHeadsUpHandToRiver(GameEngine engine, String buttonId, String otherId) {
        engine.applyAction(buttonId, PlayerAction.CALL, 0);
        engine.applyAction(otherId, PlayerAction.CHECK, 0);
        for (int street = 0; street < 3; street++) {
            engine.applyAction(otherId, PlayerAction.CHECK, 0);
            engine.applyAction(buttonId, PlayerAction.CHECK, 0);
        }
    }

    // 실사용 중 발견된 버그의 회귀 테스트: 헤즈업 쇼다운에서 누가 먼저 공개될지가 예전엔 완전
    // 무작위였다. 실제 포커 규칙대로 "마지막 베팅 라운드의 마지막 공격자가 먼저 공개, 아무도
    // 베팅 안 했으면 포지션상 먼저 액션하는 사람이 먼저 공개"로 바꿨다 — 아래 세 테스트가 각각의
    // 경우를 검증한다.
    @Test
    void 마지막_베팅_라운드에서_벳한_사람이_먼저_공개된다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", 10_000));
        room.addPlayer(new Player("b", "B", 10_000));
        GameEngine engine = new GameEngine(room);
        engine.startHand(); // 버튼=a(SB), b(BB) — 포스트플랍 첫 액션자는 b.

        engine.applyAction("a", PlayerAction.CALL, 0);
        engine.applyAction("b", PlayerAction.CHECK, 0); // preflop -> FLOP
        for (int street = 0; street < 2; street++) {
            engine.applyAction("b", PlayerAction.CHECK, 0);
            engine.applyAction("a", PlayerAction.CHECK, 0);
        }
        // 리버에서 b가 벳, a가 콜 — b가 이번 라운드의(유일한) 공격자다.
        engine.applyAction("b", PlayerAction.BET, 200);
        engine.applyAction("a", PlayerAction.CALL, 0); // 리버 종료 -> beginShowdown()

        assertEquals(Phase.RIVER, room.getPhase(), "헤즈업 결정 전까지는 phase가 아직 SHOWDOWN으로 확정되지 않는다");
        assertTrue(room.getVoluntarilyRevealedIds().contains("b"), "리버에서 벳한 b가 먼저 공개돼야 한다");
        assertEquals("a", room.getHeadsUpDeciderPlayerId());
    }

    @Test
    void 전원_체크로_넘어가면_포지션상_먼저_액션하는_사람이_먼저_공개된다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", 10_000));
        room.addPlayer(new Player("b", "B", 10_000));
        GameEngine engine = new GameEngine(room);
        engine.startHand(); // 버튼=a(SB) — 포스트플랍 첫 액션자는 b(버튼이 아닌 쪽).

        checkHeadsUpHandToRiver(engine, "a", "b"); // 프리플랍 콜/체크, 이후 전부 체크로 진행

        assertTrue(room.getVoluntarilyRevealedIds().contains("b"), "아무도 안 베팅했으니 포스트플랍 첫 액션자(b)가 먼저 공개돼야 한다");
        assertEquals("a", room.getHeadsUpDeciderPlayerId());
    }

    @Test
    void 프리플랍에만_레이즈가_있고_이후_전부_체크면_그_레이즈어가_아니라_포지션_기준으로_공개된다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", 10_000));
        room.addPlayer(new Player("b", "B", 10_000));
        GameEngine engine = new GameEngine(room);
        engine.startHand(); // 버튼=a(SB), b(BB)

        // 프리플랍: a가 레이즈, b가 콜 — 이번 핸드의 유일한 공격자는 a지만, 마지막 라운드(리버)에서는
        // 공격이 없다. 실제 룰은 "마지막 베팅 라운드" 기준이라, 프리플랍 레이즈는 무효가 돼야 한다.
        engine.applyAction("a", PlayerAction.RAISE, 400);
        engine.applyAction("b", PlayerAction.CALL, 0); // preflop -> FLOP
        for (int street = 0; street < 3; street++) {
            engine.applyAction("b", PlayerAction.CHECK, 0);
            engine.applyAction("a", PlayerAction.CHECK, 0);
        }

        assertTrue(room.getVoluntarilyRevealedIds().contains("b"), "프리플랍 레이즈어(a)가 아니라 포지션 기준(b)이어야 한다");
        assertEquals("a", room.getHeadsUpDeciderPlayerId());
    }

    @Test
    void 헤즈업_쇼다운은_결정_전까지_팟이_지급되지_않는다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", 10_000));
        room.addPlayer(new Player("b", "B", 10_000));
        GameEngine engine = new GameEngine(room);
        engine.startHand(); // 버튼=a(SB), b(BB)

        checkHeadsUpHandToRiver(engine, "a", "b");

        assertEquals(Phase.RIVER, room.getPhase(), "헤즈업 쇼다운 결정 전까지는 phase가 아직 SHOWDOWN으로 확정되지 않는다");
        assertNotNull(room.getHeadsUpDeciderPlayerId());
        assertNull(room.getLastShowdownResult());
        assertNull(engine.resolveWinnerId(), "결정 전까지는 게임 종료(우승자) 판정도 하면 안 된다");
        // 블라인드(+빅블라인드 자리인 b는 앤티도)만 걷힌 상태 그대로, 팟은 아직 지급되지 않았다.
        assertEquals(10_000 - Room.BIG_BLIND, room.findPlayer("a").getChips());
        assertEquals(10_000 - Room.BIG_BLIND - room.getAnte(), room.findPlayer("b").getChips());
    }

    @Test
    void 헤즈업_쇼다운에서_머크해도_칩은_어딘가로_사라지거나_늘어나지_않는다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", 10_000));
        room.addPlayer(new Player("b", "B", 10_000));
        GameEngine engine = new GameEngine(room);
        engine.startHand();
        checkHeadsUpHandToRiver(engine, "a", "b");

        String deciderId = room.getHeadsUpDeciderPlayerId();

        engine.decideHeadsUpReveal(deciderId, false); // 머크(공개 안 함)

        assertEquals(Phase.SHOWDOWN, room.getPhase());
        assertNull(room.getHeadsUpDeciderPlayerId(), "결정이 끝났으므로 더 이상 대기 상태가 아니다");
        assertFalse(room.getVoluntarilyRevealedIds().contains(deciderId), "머크했으므로 공개 목록에 없어야 한다");
        assertNotNull(room.getLastShowdownResult());
        // 머크한 사람이 실제로 이기고 있었으면 이제 상대방에게 전부 넘어간다(아래 별도 테스트가
        // 정확한 승자를 확정적으로 검증한다) — 여기서는 실제 카드가 랜덤이라 "누가 받는지"까지는
        // 확인할 수 없고, 시작 칩 총합(20,000)이 그대로 두 사람에게 나뉘어 있는지(칩이 사라지거나
        // 늘어나지 않는지)만 확인한다.
        int totalChipsAfter = room.findPlayer("a").getChips() + room.findPlayer("b").getChips();
        assertEquals(20_000, totalChipsAfter);
    }

    // 실사용 중 발견된 버그의 회귀 테스트: 결정자가 실제로는 이기고 있는데(트리플 등) 머크를
    // 선택하면, 예전에는 머크와 무관하게 항상 실제 승자에게 그대로 지급됐다. 실제 포커 규칙
    // ("테이블에 보여주지 않은 패는 죽은 패")대로 그 몫이 전부 상대방에게 가야 한다.
    @Test
    void 이기고_있어도_머크하면_팟은_전부_상대방에게_간다() {
        Room room = new Room();
        Player a = new Player("a", "A", 10_000);
        Player b = new Player("b", "B", 10_000);
        room.addPlayer(a);
        room.addPlayer(b);
        GameEngine engine = new GameEngine(room);
        engine.startHand(); // 버튼=a(SB) — 포스트플랍 첫 액션자는 b라서 b가 자동 공개, a가 결정자.

        engine.applyAction("a", PlayerAction.CALL, 0);
        engine.applyAction("b", PlayerAction.CHECK, 0);
        for (int street = 0; street < 2; street++) {
            engine.applyAction("b", PlayerAction.CHECK, 0);
            engine.applyAction("a", PlayerAction.CHECK, 0);
        }
        // 커뮤니티 카드가 이미 5장 다 딜링된 리버 직전 상태 — a가 스페이드 플러시로 확실히
        // 이기도록 카드를 덮어써서 승부를 확정적으로 만든다.
        a.getHoleCards().clear();
        a.getHoleCards().add(new Card(Suit.SPADE, Rank.ACE));
        a.getHoleCards().add(new Card(Suit.SPADE, Rank.KING));
        b.getHoleCards().clear();
        b.getHoleCards().add(new Card(Suit.HEART, Rank.TWO));
        b.getHoleCards().add(new Card(Suit.CLUB, Rank.THREE));
        room.getCommunityCards().clear();
        room.getCommunityCards().addAll(List.of(
                new Card(Suit.SPADE, Rank.QUEEN),
                new Card(Suit.SPADE, Rank.JACK),
                new Card(Suit.SPADE, Rank.TEN),
                new Card(Suit.DIAMOND, Rank.FOUR),
                new Card(Suit.CLUB, Rank.FIVE)
        ));

        engine.applyAction("b", PlayerAction.CHECK, 0);
        engine.applyAction("a", PlayerAction.CHECK, 0); // 리버 종료 -> beginShowdown()

        assertEquals("a", room.getHeadsUpDeciderPlayerId(), "이 세팅에서는 a(버튼)가 결정자여야 한다");

        engine.decideHeadsUpReveal("a", false); // a가 실제로는 이기고 있지만 머크

        ShowdownResult.PotResult potResult = room.getLastShowdownResult().potResults().get(0);
        assertEquals(List.of(b), potResult.winners(), "머크했으므로 실제로 더 좋은 패(a)가 아니라 상대(b)가 가져가야 한다");
        assertEquals(9_800, a.getChips(), "머크한 a는 자기가 낸 몫만 잃고 더는 받지 못한다");
        assertEquals(10_200, b.getChips(), "b가 팟 전체를 가져간다");

        // 핸드 히스토리에도 머크 이후의(포기 반영된) 실제 결과가 그대로 기록돼야 한다.
        HandHistoryEntry historyEntry = room.getHandHistory().get(0);
        assertEquals(1, historyEntry.handNumber());
        assertEquals(List.of("b"), historyEntry.pots().get(0).winnerIds(), "히스토리도 머크 반영 후(b 승) 결과여야 한다");
    }

    @Test
    void 핸드가_끝나면_히스토리에_항목이_하나_쌓인다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", 1000));
        room.addPlayer(new Player("b", "B", 1000));
        GameEngine engine = new GameEngine(room);
        engine.startHand();

        engine.applyAction("a", PlayerAction.FOLD, 0); // b 혼자 남아 폴드승

        List<HandHistoryEntry> history = room.getHandHistory();
        assertEquals(1, history.size());
        assertEquals(1, history.get(0).handNumber());
        assertTrue(history.get(0).wonByFold());
        assertEquals("b", history.get(0).foldWinWinnerId());
        assertEquals("B", history.get(0).foldWinWinnerNickname());
    }

    @Test
    void 앤티_레벨에서_핸드를_시작하면_빅블라인드가_앤티를_추가로_낸다() {
        Room room = new Room();
        Player a = new Player("a", "A", 50_000);
        Player b = new Player("b", "B", 50_000);
        room.addPlayer(a);
        room.addPlayer(b);
        GameEngine engine = new GameEngine(room);
        for (int i = 0; i < 75; i++) {
            room.recordHandCompletedForBlindLevel(); // 마지막 블라인드 레벨(500/1000)까지 미리 진행
        }

        engine.startHand(); // 버튼=a(헤즈업 SB), b=빅블라인드 — b가 빅블라인드+앤티를 둘 다 낸다.

        assertEquals(1000, room.getAnte());
        assertEquals(50_000 - 500, a.getChips(), "a는 스몰블라인드(500)만 낸다");
        assertEquals(50_000 - 1000 - 1000, b.getChips(), "b는 빅블라인드(1000)+앤티(1000)를 낸다");
        assertEquals(1000, b.getCurrentRoundBet(), "앤티는 currentRoundBet(콜 기준액)에 안 들어가고 빅블라인드만 반영돼야 한다");

        // a가 콜해서 두 사람의 totalHandContribution이 같아진(1000/1000) 뒤에도 — 실사용 중 발견된
        // 버그의 회귀 테스트: 앤티 때문에 b의 totalHandContribution만 더 커 보여서, 실시간 팟 조회
        // (RoomStateMapper와 같은 방식)가 앤티를 가짜 사이드팟으로 나눠 b한테만 몰아주지 않고,
        // 정상적으로 팟 하나에 다 합쳐 보여줘야 한다.
        engine.applyAction("a", PlayerAction.CALL, 0);

        List<Pot> livePots = PotCalculator.calculate(room.getPlayers(), room.getAnteCollectedThisHand());
        assertEquals(1, livePots.size(), "기여금이 같아졌으므로(1000/1000) 앤티가 가짜 사이드팟을 만들면 안 된다");
        assertEquals(1000 + 1000 + 1000, livePots.get(0).amount(), "a의 콜(1000)+b의 빅블라인드(1000)+앤티(1000)");
    }

    @Test
    void 결정자가_시간_초과되면_강제로_공개_처리된다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", 10_000));
        room.addPlayer(new Player("b", "B", 10_000));
        GameEngine engine = new GameEngine(room);
        engine.startHand();
        checkHeadsUpHandToRiver(engine, "a", "b");

        String deciderId = room.getHeadsUpDeciderPlayerId();
        boolean forced = engine.forceHeadsUpRevealIfStillPending(deciderId);

        assertTrue(forced);
        assertEquals(Phase.SHOWDOWN, room.getPhase());
        assertTrue(room.getVoluntarilyRevealedIds().contains(deciderId), "시간 초과면 강제로 공개 처리되어야 한다");
    }

    @Test
    void 이미_결정했으면_시간_초과_강제_처리를_무시한다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", 10_000));
        room.addPlayer(new Player("b", "B", 10_000));
        GameEngine engine = new GameEngine(room);
        engine.startHand();
        checkHeadsUpHandToRiver(engine, "a", "b");

        String deciderId = room.getHeadsUpDeciderPlayerId();
        engine.decideHeadsUpReveal(deciderId, false);

        boolean forced = engine.forceHeadsUpRevealIfStillPending(deciderId);

        assertFalse(forced, "이미 결정이 끝났으면 뒤늦은 시간 초과 처리는 무시해야 한다");
    }

    @Test
    void 완전한_유휴_상태면_나가기_요청이_즉시_처리된다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", Room.STARTING_CHIPS));
        room.addPlayer(new Player("b", "B", Room.STARTING_CHIPS));
        GameEngine engine = new GameEngine(room); // 아직 핸드를 한 번도 시작한 적 없음(phase == null)

        engine.requestLeave("a", true);

        assertEquals(1, room.getPlayers().size());
        assertThrows(IllegalArgumentException.class, () -> room.findPlayer("a"));
    }

    // 실사용 중 발견된 버그의 회귀 테스트: 쇼다운 공개 애니메이션이 재생되는 도중(백엔드 기준으로는
    // 이미 phase == SHOWDOWN) 나가기를 눌러도, 예전에는 "핸드 진행 중이 아니다 = 즉시 제거"로
    // 취급되어 결과 화면을 볼 틈도 없이 바로 튕겨나갔다. 이제는 SHOWDOWN 상태에서는 항상 유예를 둔다.
    @Test
    void 핸드가_끝난_직후_SHOWDOWN_상태에서_나가기를_눌러도_즉시_제거되지_않는다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", 1000));
        room.addPlayer(new Player("b", "B", 1000));
        GameEngine engine = new GameEngine(room);
        engine.startHand();
        engine.applyAction("a", PlayerAction.FOLD, 0); // b 혼자 남아 폴드승, 핸드 종료(phase == SHOWDOWN)

        assertEquals(Phase.SHOWDOWN, room.getPhase());

        engine.requestLeave("a", true); // 핸드가 끝난 뒤에(쇼다운 공개 중에) 나가기를 누름

        assertEquals(2, room.getPlayers().size(), "SHOWDOWN 상태라도 즉시 제거하지 않고 유예를 둬야 한다");
        assertTrue(engine.hasPendingLeaveDuringShowdown());

        engine.processPendingLeaves();

        assertEquals(1, room.getPlayers().size());
    }

    // 실사용 중 발견된 버그의 회귀 테스트: STOP으로 자동 진행이 멈춰서 프론트가 이미 "게임 준비 중"
    // 대기 화면으로 돌아간 뒤에 나가기를 눌러도, phase가 여전히 SHOWDOWN이라는 이유만으로 3초 유예를
    // 또 거는 문제가 있었다. 결과 화면을 충분히 오래(RESULT_VIEW_GRACE_MILLIS) 보여준 뒤라면 더 이상
    // 보호할 화면이 없으므로 즉시 제거돼야 한다.
    @Test
    void 결과_유예_시간이_지나면_SHOWDOWN_상태에서도_나가기가_즉시_처리된다() throws InterruptedException {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", 1000));
        room.addPlayer(new Player("b", "B", 1000));
        GameEngine engine = new GameEngine(room);
        engine.startHand();
        engine.applyAction("a", PlayerAction.FOLD, 0); // b 혼자 남아 폴드승, 핸드 종료(phase == SHOWDOWN)

        assertEquals(Phase.SHOWDOWN, room.getPhase());

        Thread.sleep(5_600); // RESULT_VIEW_GRACE_MILLIS(5.5초) 경과를 흉내낸다.
        engine.requestLeave("a", true);

        assertEquals(1, room.getPlayers().size(), "결과 화면을 이미 충분히 보여줬으니 3초 유예 없이 바로 제거돼야 한다");
    }

    @Test
    void 나가기를_누르면_레디가_자동으로_꺼진다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", 1000));
        room.addPlayer(new Player("b", "B", 1000));
        room.addPlayer(new Player("c", "C", 1000));
        GameEngine engine = new GameEngine(room);
        engine.startHand();
        room.findPlayer("a").setReady(true);

        engine.requestLeave("a", true);

        assertFalse(room.findPlayer("a").isReady(), "나가기를 누르면 자동 시작 방지를 위해 레디가 꺼져야 한다");
    }

    @Test
    void 핸드_진행_중_나가기_요청은_핸드가_끝나도_3초_유예_후에_처리된다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", 1000));
        room.addPlayer(new Player("b", "B", 1000));
        room.addPlayer(new Player("c", "C", 1000));
        GameEngine engine = new GameEngine(room);
        engine.startHand(); // 버튼=a, SB=b, BB=c, 프리플랍 첫 액션=a(UTG)

        engine.requestLeave("a", true);
        assertEquals(3, room.getPlayers().size(), "핸드 진행 중이므로 아직 제거되면 안 된다");

        engine.applyAction("a", PlayerAction.FOLD, 0);
        engine.applyAction("b", PlayerAction.FOLD, 0); // c 혼자 남아 폴드승

        assertEquals(Phase.SHOWDOWN, room.getPhase());
        assertEquals(3, room.getPlayers().size(),
                "핸드가 막 끝났어도 결과를 볼 시간을 줘야 하므로 즉시 제거되면 안 된다");
        assertTrue(engine.hasPendingLeaveDuringShowdown(), "LeaveProcessingTimerService가 3초 뒤 처리할 대상이 있어야 한다");

        engine.processPendingLeaves(); // 3초 타이머가 만료된 상황을 흉내낸다.

        assertEquals(2, room.getPlayers().size(), "유예 시간이 지났으니 나가기 예약된 a가 제거되어야 한다");
        assertThrows(IllegalArgumentException.class, () -> room.findPlayer("a"));
        assertFalse(engine.hasPendingLeaveDuringShowdown());
    }

    @Test
    void 삼인_이상_쇼다운으로_핸드가_끝나도_나가기_예약된_사람이_유예_후_제거된다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", 10_000));
        room.addPlayer(new Player("b", "B", 10_000));
        room.addPlayer(new Player("c", "C", 10_000));
        GameEngine engine = new GameEngine(room);
        engine.startHand(); // 버튼=a, SB=b, BB=c

        engine.requestLeave("b", true);

        engine.applyAction("a", PlayerAction.CALL, 0);
        engine.applyAction("b", PlayerAction.CALL, 0);
        engine.applyAction("c", PlayerAction.CHECK, 0);
        for (int street = 0; street < 3; street++) {
            engine.applyAction("b", PlayerAction.CHECK, 0);
            engine.applyAction("c", PlayerAction.CHECK, 0);
            engine.applyAction("a", PlayerAction.CHECK, 0);
        }

        assertEquals(Phase.SHOWDOWN, room.getPhase());
        assertEquals(3, room.getPlayers().size(), "쇼다운 공개를 볼 시간을 줘야 하므로 즉시 제거되면 안 된다");

        engine.processPendingLeaves();

        assertEquals(2, room.getPlayers().size());
        assertThrows(IllegalArgumentException.class, () -> room.findPlayer("b"));
    }

    @Test
    void 나가기를_취소하면_핸드가_끝나도_제거되지_않는다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", Room.STARTING_CHIPS));
        room.addPlayer(new Player("b", "B", Room.STARTING_CHIPS));
        room.addPlayer(new Player("c", "C", Room.STARTING_CHIPS));
        GameEngine engine = new GameEngine(room);
        engine.startHand();

        engine.requestLeave("a", true);
        engine.requestLeave("a", false); // 취소

        engine.applyAction("a", PlayerAction.FOLD, 0);
        engine.applyAction("b", PlayerAction.FOLD, 0);

        assertEquals(Phase.SHOWDOWN, room.getPhase());
        assertEquals(3, room.getPlayers().size(), "나가기를 취소했으므로 핸드가 끝나도 남아있어야 한다");
        assertFalse(room.findPlayer("a").isLeaving());
    }

    @Test
    void GAME_OVER_상태가_아니면_초기화_요청은_무시된다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", Room.STARTING_CHIPS));
        room.addPlayer(new Player("b", "B", Room.STARTING_CHIPS));
        GameEngine engine = new GameEngine(room);

        boolean reset = engine.resetAfterGameOverIfStillOver();

        assertFalse(reset);
        assertEquals(2, room.getPlayers().size());
    }

    // GAME OVER는 이제 핸드가 실제로 끝나는 시점(checkGameOver)에만 판정되어 고정된다 — 그래서
    // 테스트도 direct Room 구성이 아니라 실제 핸드(폴드승)를 끝까지 진행시켜야 한다. b의 칩을
    // resetChips(0)으로 미리 바닥냈다가(실제로는 올인 패배로 도달하는 상황을 단순화한 것) 폴드로
    // 핸드를 끝내면, finishHandByFold 안에서 checkGameOver가 이를 감지한다.
    private void playHandUntilBFoldsAndBusts(GameEngine engine, Player b) {
        engine.startHand(); // 헤즈업: 버튼=a(SB), 첫 액션=a
        b.resetChips(0);
        engine.applyAction("a", PlayerAction.CALL, 0);
        engine.applyAction("b", PlayerAction.FOLD, 0);
    }

    @Test
    void GAME_OVER_상태면_내보내지_않고_전원_칩과_레디가_초기화된다() {
        Room room = new Room();
        Player a = new Player("a", "A", 10_000);
        Player b = new Player("b", "B", 10_000);
        room.addPlayer(a);
        room.addPlayer(b);
        GameEngine engine = new GameEngine(room);
        playHandUntilBFoldsAndBusts(engine, b);
        a.setReady(true);

        assertEquals("a", engine.resolveWinnerId());

        boolean reset = engine.resetAfterGameOverIfStillOver();

        assertTrue(reset);
        assertEquals(2, room.getPlayers().size(), "내보내지 않고 그대로 남아있어야 한다");
        assertEquals(Room.STARTING_CHIPS, a.getChips());
        assertEquals(Room.STARTING_CHIPS, b.getChips());
        assertEquals(PlayerStatus.ACTIVE, b.getStatus(), "파산했던 사람도 칩을 받아 다시 ACTIVE여야 한다");
        assertFalse(a.isReady(), "리매치를 기다리려면 다시 레디해야 한다 — 이전 레디는 유지되면 안 된다");
        assertNull(room.getPhase());
        assertEquals(-1, room.getDealerButtonPosition());
        assertNull(engine.resolveWinnerId(), "리셋했으니 더 이상 GAME OVER가 아니다");
    }

    // 실사용 중 발견된 버그의 회귀 테스트: GAME OVER 카운트다운 도중 한 명이 "나가기"로 방을 빠져서
    // 인원이 MIN_PLAYERS 밑으로 줄어도, 이미 확정된 GAME OVER 판정 자체는 흔들리면 안 된다
    // (그 전엔 resolveWinnerId를 매번 다시 계산해서, 인원이 줄면 null이 되어 카운트다운이 멋대로
    // 취소되고, 다시 들어오면 처음부터 다시 시작되는 버그가 있었다).
    @Test
    void GAME_OVER_이후_한_명이_나가서_인원이_줄어도_판정은_유지된다() {
        Room room = new Room();
        Player a = new Player("a", "A", 10_000);
        Player b = new Player("b", "B", 10_000);
        room.addPlayer(a);
        room.addPlayer(b);
        GameEngine engine = new GameEngine(room);
        playHandUntilBFoldsAndBusts(engine, b);

        assertEquals("a", engine.resolveWinnerId());

        engine.requestLeave("b", true); // b가 나가기를 예약한다(핸드가 끝난 직후라 3초 유예 대상).
        engine.processPendingLeaves(); // 유예 시간이 지난 상황을 흉내낸다 -> 인원이 1명으로 줄어든다.

        assertEquals(1, room.getPlayers().size());
        assertEquals("a", engine.resolveWinnerId(), "인원이 줄어도 GAME OVER 판정은 그대로 유지되어야 한다");
    }

    // 실사용 중 발견된 버그의 회귀 테스트: GAME OVER 카운트다운 도중 승자 본인이 "나가기"로 방을
    // 빠지면, players 목록에서 다시 닉네임을 찾을 수 없게 된다 — 그래서 닉네임 자체도 고정값으로
    // 따로 들고 있어야 한다.
    @Test
    void GAME_OVER_이후_승자가_나가도_닉네임_판정은_유지된다() {
        Room room = new Room();
        Player a = new Player("a", "A닉네임", 10_000);
        Player b = new Player("b", "B", 10_000);
        room.addPlayer(a);
        room.addPlayer(b);
        GameEngine engine = new GameEngine(room);
        playHandUntilBFoldsAndBusts(engine, b);

        assertEquals("A닉네임", engine.resolveWinnerNickname());

        engine.requestLeave("a", true); // 승자 본인이 나가기를 예약해도(패자 b는 여전히 자리에 남아있음)
        engine.processPendingLeaves(); // 유예 시간이 지난 상황을 흉내낸다.

        assertEquals(1, room.getPlayers().size());
        assertEquals("a", engine.resolveWinnerId(), "승자 id 판정은 유지되어야 한다");
        assertEquals("A닉네임", engine.resolveWinnerNickname(), "승자가 나가도 닉네임은 고정값으로 유지되어야 한다");
    }

    @Test
    void 방장이_아니면_강퇴할_수_없다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", Room.STARTING_CHIPS)); // a가 방장
        room.addPlayer(new Player("b", "B", Room.STARTING_CHIPS));
        room.addPlayer(new Player("c", "C", Room.STARTING_CHIPS));
        GameEngine engine = new GameEngine(room);

        assertThrows(GameStateException.class, () -> engine.kickPlayer("b", "c"));
    }

    @Test
    void 자기_자신은_강퇴할_수_없다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", Room.STARTING_CHIPS)); // a가 방장
        room.addPlayer(new Player("b", "B", Room.STARTING_CHIPS));
        GameEngine engine = new GameEngine(room);

        assertThrows(GameStateException.class, () -> engine.kickPlayer("a", "a"));
    }

    @Test
    void 핸드_진행_중에는_강퇴할_수_없다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", Room.STARTING_CHIPS)); // a가 방장
        room.addPlayer(new Player("b", "B", Room.STARTING_CHIPS));
        room.addPlayer(new Player("c", "C", Room.STARTING_CHIPS));
        GameEngine engine = new GameEngine(room);
        engine.startHand();

        assertThrows(GameStateException.class, () -> engine.kickPlayer("a", "b"));
    }

    @Test
    void 레디한_사람은_강퇴할_수_없다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", Room.STARTING_CHIPS)); // a가 방장
        room.addPlayer(new Player("b", "B", Room.STARTING_CHIPS));
        GameEngine engine = new GameEngine(room);
        engine.setReady("b", true);

        assertThrows(GameStateException.class, () -> engine.kickPlayer("a", "b"));
    }

    @Test
    void 레디_안한_지_얼마_안_지났으면_강퇴가_거부된다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", Room.STARTING_CHIPS)); // a가 방장
        room.addPlayer(new Player("b", "B", Room.STARTING_CHIPS)); // 방금 입장(레디 안 함)
        GameEngine engine = new GameEngine(room);

        assertThrows(GameStateException.class, () -> engine.kickPlayer("a", "b"));
        assertEquals(2, room.getPlayers().size());
    }

    @Test
    void 레디_안한_채_충분히_기다리면_방장이_강퇴할_수_있다() throws InterruptedException {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", Room.STARTING_CHIPS)); // a가 방장
        room.addPlayer(new Player("b", "B", Room.STARTING_CHIPS)); // 방금 입장(레디 안 함)
        GameEngine engine = new GameEngine(room);

        Thread.sleep(3_100); // KICK_DELAY_MILLIS(3초) 경과를 흉내낸다.
        engine.kickPlayer("a", "b");

        assertEquals(1, room.getPlayers().size());
        assertThrows(IllegalArgumentException.class, () -> room.findPlayer("b"));
    }

    // 실사용 중 발견된 버그의 회귀 테스트: 핸드가 진행되는 동안(레디 여부가 아무 의미 없는 시간)에
    // 레디 안 한 상태로 이미 3초 이상 지나 있었다면, 핸드가 막 끝나자마자(반응할 틈도 없이) 곧바로
    // 강퇴 가능해지는 문제가 있었다. 핸드가 끝나는 시점(onHandConcluded)에 강퇴 유예 시계가 다시
    // 리셋되어야 한다.
    @Test
    void 핸드가_진행되는_동안_레디_안한_시간이_길어도_핸드가_막_끝난_직후엔_바로_강퇴되지_않는다() throws InterruptedException {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", Room.STARTING_CHIPS)); // a가 방장
        room.addPlayer(new Player("b", "B", Room.STARTING_CHIPS));
        room.addPlayer(new Player("c", "C", Room.STARTING_CHIPS)); // 레디 안 한 채로 쭉 유지됨
        GameEngine engine = new GameEngine(room);

        // c가 레디 안 한 상태로 이미 3초 넘게 지난 뒤에 핸드가 시작되고 끝나는 상황을 흉내낸다.
        Thread.sleep(3_100);
        engine.startHand();
        engine.applyAction("a", PlayerAction.FOLD, 0);
        engine.applyAction("b", PlayerAction.FOLD, 0); // c 혼자 남아 폴드승, 핸드 종료(phase == SHOWDOWN)

        assertThrows(GameStateException.class, () -> engine.kickPlayer("a", "c"),
                "핸드가 막 끝난 직후에는 레디 안 한 시간이 얼마였든 유예를 다시 줘야 한다");

        Thread.sleep(3_100);
        engine.kickPlayer("a", "c");

        assertEquals(2, room.getPlayers().size());
    }
}
