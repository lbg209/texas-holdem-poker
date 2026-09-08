package com.lbg0146.backend.game;

import com.lbg0146.backend.card.Card;
import com.lbg0146.backend.card.Rank;
import com.lbg0146.backend.card.Suit;
import com.lbg0146.backend.exception.GameStateException;
import com.lbg0146.backend.exception.InvalidActionException;
import com.lbg0146.backend.player.Player;
import com.lbg0146.backend.player.PlayerAction;
import com.lbg0146.backend.room.Phase;
import com.lbg0146.backend.room.Room;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameEngineTest {

    @Test
    void 최소_인원_미만이면_핸드를_시작할_수_없다() {
        Room room = new Room();
        room.addPlayer(new Player("p1", "P1", Room.STARTING_CHIPS));
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

        engine.startHand(); // 버튼=a, SB=b(50), BB=c(100), 프리플랍 첫 액션=a(UTG)

        engine.applyAction("a", PlayerAction.FOLD, 0);
        engine.applyAction("b", PlayerAction.FOLD, 0);

        assertEquals(Phase.SHOWDOWN, room.getPhase());
        assertEquals(1000 - 100 + 150, room.findPlayer("c").getChips());
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
}
