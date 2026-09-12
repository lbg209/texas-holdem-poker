package com.lbg0146.backend.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerTest {

    @Test
    void 칩을_커밋하면_라운드기여금과_핸드기여금이_함께_늘어난다() {
        Player player = new Player("p1", "A", 1000);

        player.commitChips(300);

        assertEquals(700, player.getChips());
        assertEquals(300, player.getCurrentRoundBet());
        assertEquals(300, player.getTotalHandContribution());
        assertEquals(PlayerStatus.ACTIVE, player.getStatus());
    }

    @Test
    void 가진_칩보다_많이_커밋하면_가진_만큼만_내고_올인된다() {
        Player player = new Player("p1", "A", 300);

        player.commitChips(1000);

        assertEquals(0, player.getChips());
        assertEquals(300, player.getCurrentRoundBet());
        assertEquals(PlayerStatus.ALL_IN, player.getStatus());
    }

    @Test
    void 새_라운드가_시작되면_라운드기여금만_리셋되고_핸드기여금은_유지된다() {
        Player player = new Player("p1", "A", 1000);
        player.commitChips(300);

        player.resetForNewRound();

        assertEquals(0, player.getCurrentRoundBet());
        assertEquals(300, player.getTotalHandContribution());
    }

    @Test
    void 새_핸드가_시작되면_상태와_기여금이_전부_초기화된다() {
        Player player = new Player("p1", "A", 1000);
        player.commitChips(300);
        player.fold();

        player.resetForNewHand();

        assertEquals(PlayerStatus.ACTIVE, player.getStatus());
        assertEquals(0, player.getCurrentRoundBet());
        assertEquals(0, player.getTotalHandContribution());
        assertEquals(0, player.getHoleCards().size());
    }

    @Test
    void 레디_안한_상태로_충분히_지났으면_강퇴_가능하다() {
        Player player = new Player("p1", "A", 1000);

        // 생성 직후(레디 안 한 상태)부터 시간이 흐른 것으로 간주 — delayMillis를 0으로 주면
        // 방금 생성됐어도 바로 충족된다.
        assertTrue(player.isKickEligible(0));
    }

    @Test
    void 레디_안한_지_충분히_안_지났으면_강퇴_불가능하다() {
        Player player = new Player("p1", "A", 1000);

        assertFalse(player.isKickEligible(10_000_000));
    }

    @Test
    void 레디하면_강퇴_대상에서_제외된다() {
        Player player = new Player("p1", "A", 1000);

        player.setReady(true);

        assertFalse(player.isKickEligible(0));
    }

    @Test
    void 레디를_다시_끄면_그_시점부터_다시_강퇴_대상이_된다() {
        Player player = new Player("p1", "A", 1000);
        player.setReady(true);

        player.setReady(false);

        assertTrue(player.isKickEligible(0));
        assertFalse(player.isKickEligible(10_000_000));
    }
}
