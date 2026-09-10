package com.lbg0146.backend.room.controller;

import com.lbg0146.backend.card.Card;
import com.lbg0146.backend.card.Rank;
import com.lbg0146.backend.card.Suit;
import com.lbg0146.backend.game.GameEngine;
import com.lbg0146.backend.game.ShowdownResult;
import com.lbg0146.backend.player.Player;
import com.lbg0146.backend.room.Phase;
import com.lbg0146.backend.room.Room;
import com.lbg0146.backend.room.controller.dto.RoomStateResponse;
import com.lbg0146.backend.room.controller.dto.ShowdownHandView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomStateMapperTest {

    // 실사용 중 발견된 버그의 회귀 테스트: 숏스택(a)이 800으로 올인, 빅스택(b)이 5000을 베팅해서
    // 콜 안 된 4200이 그대로 b에게 돌아가는 사이드팟이 생긴다. a가 실제 카드 비교(메인팟)로
    // 이겼는데도, eligible이 b 혼자뿐인 사이드팟까지 "승자" 판정에 섞이면 진 b도 WINNER로 표시된다.
    @Test
    void 콜_못_받은_초과_베팅을_돌려받는_사람은_승자로_표시되지_않는다() {
        Room room = new Room();
        Player a = new Player("a", "A", 10_000);
        Player b = new Player("b", "B", 10_000);
        room.addPlayer(a);
        room.addPlayer(b);
        GameEngine engine = new GameEngine(room);

        // a는 K하이 스트레이트 플러시(K-Q-J-10-9 클럽), b는 보드만으로 Q하이 스트레이트 플러시라
        // 둘 다 STRAIGHT_FLUSH지만 a가 더 높다.
        a.receiveHoleCard(new Card(Suit.CLUB, Rank.KING));
        a.receiveHoleCard(new Card(Suit.HEART, Rank.SIX));
        b.receiveHoleCard(new Card(Suit.HEART, Rank.TWO));
        b.receiveHoleCard(new Card(Suit.DIAMOND, Rank.THREE));
        room.getCommunityCards().addAll(List.of(
                new Card(Suit.CLUB, Rank.TEN),
                new Card(Suit.CLUB, Rank.QUEEN),
                new Card(Suit.CLUB, Rank.NINE),
                new Card(Suit.CLUB, Rank.JACK),
                new Card(Suit.CLUB, Rank.EIGHT)
        ));

        a.commitChips(800); // a는 800으로 올인
        b.commitChips(5000); // b는 5000을 베팅 -> 콜 안 된 4200은 사이드팟으로 b 혼자 eligible

        ShowdownResult result = engine.resolveShowdown();
        room.setLastShowdownResult(result);
        room.setPhase(Phase.SHOWDOWN);

        RoomStateResponse response = RoomStateMapper.toResponse(engine, "a", null, null, null, null);
        List<ShowdownHandView> hands = response.showdownHands();
        ShowdownHandView aHand = hands.stream().filter(h -> h.playerId().equals("a")).findFirst().orElseThrow();
        ShowdownHandView bHand = hands.stream().filter(h -> h.playerId().equals("b")).findFirst().orElseThrow();

        assertTrue(aHand.isWinner(), "메인팟을 실제로 이긴 a는 승자여야 한다");
        assertFalse(bHand.isWinner(), "콜 못 받은 초과 베팅을 돌려받았을 뿐인 b는 승자가 아니어야 한다");
    }

    // 통제 비교군: 사이드팟 없이(둘 다 정확히 같은 금액만 걸어서 팟이 하나뿐인 경우) 더 높은
    // 스트레이트 플러시(K하이)와 낮은 스트레이트 플러시(Q하이)를 비교했을 때도 진짜로 한 명만
    // 승자로 판정되는지 확인한다 — HandEvaluator의 tiebreak 비교 자체가 맞는지 검증.
    @Test
    void 사이드팟이_없어도_더_높은_스트레이트_플러시만_승자로_표시된다() {
        Room room = new Room();
        Player a = new Player("a", "A", 10_000);
        Player b = new Player("b", "B", 10_000);
        room.addPlayer(a);
        room.addPlayer(b);
        GameEngine engine = new GameEngine(room);

        a.receiveHoleCard(new Card(Suit.CLUB, Rank.KING));
        a.receiveHoleCard(new Card(Suit.HEART, Rank.SIX));
        b.receiveHoleCard(new Card(Suit.HEART, Rank.TWO));
        b.receiveHoleCard(new Card(Suit.DIAMOND, Rank.THREE));
        room.getCommunityCards().addAll(List.of(
                new Card(Suit.CLUB, Rank.TEN),
                new Card(Suit.CLUB, Rank.QUEEN),
                new Card(Suit.CLUB, Rank.NINE),
                new Card(Suit.CLUB, Rank.JACK),
                new Card(Suit.CLUB, Rank.EIGHT)
        ));

        a.commitChips(800);
        b.commitChips(800); // 둘 다 정확히 같은 금액 -> 사이드팟 없이 팟 하나

        ShowdownResult result = engine.resolveShowdown();
        room.setLastShowdownResult(result);
        room.setPhase(Phase.SHOWDOWN);

        assertEquals(1, result.potResults().size(), "사이드팟 없이 팟이 하나여야 한다");
        assertEquals(List.of(a), result.potResults().get(0).winners(), "K하이가 Q하이보다 높아야 한다");

        RoomStateResponse response = RoomStateMapper.toResponse(engine, "a", null, null, null, null);
        List<ShowdownHandView> hands = response.showdownHands();
        ShowdownHandView aHand = hands.stream().filter(h -> h.playerId().equals("a")).findFirst().orElseThrow();
        ShowdownHandView bHand = hands.stream().filter(h -> h.playerId().equals("b")).findFirst().orElseThrow();

        assertTrue(aHand.isWinner());
        assertFalse(bHand.isWinner());
    }
}
