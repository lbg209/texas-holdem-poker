package com.lbg0146.backend.game;

import com.lbg0146.backend.exception.InvalidActionException;
import com.lbg0146.backend.player.Player;
import com.lbg0146.backend.player.PlayerAction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BettingRoundTest {

    @Test
    void 전원_체크로_라운드가_종료된다() {
        Player a = new Player("A", "A", 5000);
        Player b = new Player("B", "B", 5000);
        List<Player> seats = List.of(a, b);
        BettingRound round = new BettingRound(seats, seats, 0, 100);

        round.applyAction(a, PlayerAction.CHECK, 0);
        round.applyAction(b, PlayerAction.CHECK, 0);

        assertTrue(round.isComplete());
    }

    @Test
    void 정상_레이즈는_이미_액션한_플레이어를_다시_불러온다() {
        Player a = new Player("A", "A", 5000);
        Player b = new Player("B", "B", 5000);
        Player c = new Player("C", "C", 5000);
        Player d = new Player("D", "D", 5000);
        List<Player> seats = List.of(a, b, c, d);
        BettingRound round = new BettingRound(seats, seats, 0, 100);

        round.applyAction(a, PlayerAction.BET, 300);
        round.applyAction(b, PlayerAction.CALL, 0);
        round.applyAction(c, PlayerAction.RAISE, 900); // 900 >= 300+300

        assertEquals(900, round.getCurrentBet());
        assertEquals(600, round.getMinimumRaise());

        // 재오픈 순서: C 다음 좌석부터 -> D, A, B
        round.applyAction(d, PlayerAction.CALL, 0);
        round.applyAction(a, PlayerAction.CALL, 0); // 이미 액션했지만 정상 레이즈라 다시 불려옴
        round.applyAction(b, PlayerAction.CALL, 0);

        assertTrue(round.isComplete());
    }

    @Test
    void short_all_in은_currentBet만_올리고_이미_액션한_플레이어에게_콜_폴드만_허용한다() {
        Player a = new Player("A", "A", 5000);
        Player b = new Player("B", "B", 5000);
        Player c = new Player("C", "C", 700); // 숏스택
        Player d = new Player("D", "D", 5000);
        List<Player> seats = List.of(a, b, c, d);
        BettingRound round = new BettingRound(seats, seats, 0, 100);

        round.applyAction(a, PlayerAction.BET, 500); // currentBet=500, minimumRaise=500
        round.applyAction(b, PlayerAction.CALL, 0);
        round.applyAction(c, PlayerAction.ALL_IN, 0); // 700, raiseSize=200 < 500 -> short all-in

        assertEquals(700, round.getCurrentBet());
        assertEquals(500, round.getMinimumRaise(), "short all-in은 minimumRaise를 갱신하지 않는다");

        // 아직 액션 전이었던 D는 정상적으로 새 currentBet(700)에 반응한다
        round.applyAction(d, PlayerAction.CALL, 0);

        // 이미 액션을 마쳤던 A는 RAISE를 할 수 없다 (레이즈 권한이 다시 열리지 않음)
        assertThrows(InvalidActionException.class, () -> round.applyAction(a, PlayerAction.RAISE, 1200));
        // 대신 CALL/FOLD는 가능하다
        round.applyAction(a, PlayerAction.CALL, 0);

        round.applyAction(b, PlayerAction.FOLD, 0);

        assertTrue(round.isComplete());
        assertEquals(4300, a.getChips()); // 5000 - 700
        assertEquals(4300, d.getChips()); // 5000 - 700
        assertEquals(0, c.getChips());
        assertEquals(4500, b.getChips()); // 500만 내고 폴드, 추가 200은 내지 않음
    }

    @Test
    void short_all_in_이후_정상_레이즈가_나오면_완전히_재오픈된다() {
        Player a = new Player("A", "A", 5000);
        Player b = new Player("B", "B", 5000);
        Player c = new Player("C", "C", 700);
        Player d = new Player("D", "D", 5000);
        List<Player> seats = List.of(a, b, c, d);
        BettingRound round = new BettingRound(seats, seats, 0, 100);

        round.applyAction(a, PlayerAction.BET, 500);
        round.applyAction(b, PlayerAction.CALL, 0);
        round.applyAction(c, PlayerAction.ALL_IN, 0); // currentBet=700, minimumRaise=500(불변)

        // D는 원래 레이즈 권한이 있었으므로 정상 레이즈 가능 (700+500=1200 이상)
        round.applyAction(d, PlayerAction.RAISE, 1200);
        assertEquals(1200, round.getCurrentBet());
        assertEquals(500, round.getMinimumRaise());

        // 이제 A, B는 short all-in 때와 달리 완전히 재오픈되어 RAISE도 가능해야 한다
        assertDoesNotThrow(() -> round.applyAction(a, PlayerAction.RAISE, 1800));
        // A의 재레이즈로 D도 다시 반응해야 한다 (완전 재오픈이므로 D 포함)
        round.applyAction(b, PlayerAction.CALL, 0);
        round.applyAction(d, PlayerAction.CALL, 0);

        assertTrue(round.isComplete());
    }

    @Test
    void 차례가_아닌_플레이어의_액션은_거부된다() {
        Player a = new Player("A", "A", 5000);
        Player b = new Player("B", "B", 5000);
        List<Player> seats = List.of(a, b);
        BettingRound round = new BettingRound(seats, seats, 0, 100);

        assertThrows(InvalidActionException.class, () -> round.applyAction(b, PlayerAction.CHECK, 0));
    }

    @Test
    void 액션_가능한_플레이어가_1명_이하로_남으면_런아웃_대상이다() {
        Player a = new Player("A", "A", 5000);
        Player b = new Player("B", "B", 5000);
        Player c = new Player("C", "C", 500);
        List<Player> seats = List.of(a, b, c);
        BettingRound round = new BettingRound(seats, seats, 0, 100);

        round.applyAction(a, PlayerAction.ALL_IN, 0); // 5000, 정상 레이즈 크기로 재오픈
        round.applyAction(b, PlayerAction.ALL_IN, 0); // 5000, 콜 수준(currentBet과 동일) -> 재오픈 없음
        round.applyAction(c, PlayerAction.ALL_IN, 0); // 500 < currentBet -> 콜도 안 되는 올인

        assertTrue(round.isComplete());
        assertTrue(round.noFurtherBettingPossible());
    }

    @Test
    void 두명_이상_액션_가능하면_런아웃_대상이_아니다() {
        Player a = new Player("A", "A", 5000);
        Player b = new Player("B", "B", 5000);
        List<Player> seats = List.of(a, b);
        BettingRound round = new BettingRound(seats, seats, 0, 100);

        round.applyAction(a, PlayerAction.CHECK, 0);
        round.applyAction(b, PlayerAction.CHECK, 0);

        assertFalse(round.noFurtherBettingPossible());
    }
}
