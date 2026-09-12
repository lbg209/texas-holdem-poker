package com.lbg0146.backend.room.controller;

import com.lbg0146.backend.card.Card;
import com.lbg0146.backend.card.Rank;
import com.lbg0146.backend.card.Suit;
import com.lbg0146.backend.hand.EvaluatedHand;
import com.lbg0146.backend.hand.HandRank;
import com.lbg0146.backend.room.HandHistoryEntry;
import com.lbg0146.backend.room.Room;
import com.lbg0146.backend.room.controller.dto.HandHistoryEntryView;
import com.lbg0146.backend.room.controller.dto.HandHistoryHandView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandHistoryMapperTest {

    private static EvaluatedHand dummyHand(HandRank rank) {
        return new EvaluatedHand(rank, List.of(14, 13, 12, 11, 10), List.of(
                new Card(Suit.SPADE, Rank.ACE), new Card(Suit.SPADE, Rank.KING),
                new Card(Suit.SPADE, Rank.QUEEN), new Card(Suit.SPADE, Rank.JACK), new Card(Suit.SPADE, Rank.TEN)));
    }

    private static HandHistoryHandView findHand(HandHistoryEntryView view, String playerId) {
        return view.hands().stream().filter(h -> h.playerId().equals(playerId)).findFirst().orElseThrow();
    }

    @Test
    void 헤즈업에서_머크한_상대의_카드는_다른_사람에게_영원히_안_보인다() {
        HandHistoryEntry.HandEntry a = new HandHistoryEntry.HandEntry("a", "A",
                List.of(new Card(Suit.SPADE, Rank.ACE), new Card(Suit.SPADE, Rank.KING)),
                dummyHand(HandRank.FLUSH), true);
        HandHistoryEntry.HandEntry b = new HandHistoryEntry.HandEntry("b", "B",
                List.of(new Card(Suit.HEART, Rank.TWO), new Card(Suit.CLUB, Rank.THREE)),
                dummyHand(HandRank.HIGH_CARD), false);
        // 헤즈업이고 a만 공개됨(b는 머크) — 실제로는 a가 이겼지만(isWinner=true), b는 자기 카드를 숨겼다.
        HandHistoryEntry entry = new HandHistoryEntry(1, List.of(), List.of(), List.of(a, b), false, null, null,
                true, Set.of("a"));
        Room room = new Room();
        room.addHandHistoryEntry(entry);

        HandHistoryHandView bFromA = findHand(HandHistoryMapper.toResponse(room, "a").get(0), "b");
        assertTrue(bFromA.holeCards().isEmpty(), "머크한 카드는 상대에게도 안 보여야 한다");
        assertNull(bFromA.handRank());
        assertNull(bFromA.bestFive());

        HandHistoryHandView bFromB = findHand(HandHistoryMapper.toResponse(room, "b").get(0), "b");
        assertFalse(bFromB.holeCards().isEmpty(), "머크했어도 본인 카드는 본인에게는 항상 보여야 한다");

        HandHistoryHandView aFromB = findHand(HandHistoryMapper.toResponse(room, "b").get(0), "a");
        assertFalse(aFromB.holeCards().isEmpty(), "공개된 상대(a) 카드는 그대로 보여야 한다");
    }

    @Test
    void 삼인_이상_쇼다운은_공개_목록과_무관하게_전원_보인다() {
        HandHistoryEntry.HandEntry a = new HandHistoryEntry.HandEntry("a", "A",
                List.of(new Card(Suit.SPADE, Rank.ACE), new Card(Suit.SPADE, Rank.KING)),
                dummyHand(HandRank.FLUSH), true);
        HandHistoryEntry.HandEntry b = new HandHistoryEntry.HandEntry("b", "B",
                List.of(new Card(Suit.HEART, Rank.TWO), new Card(Suit.CLUB, Rank.THREE)),
                dummyHand(HandRank.HIGH_CARD), false);
        // headsUpShowdown=false, revealedPlayerIds도 비어있음 — 그래도 3인 이상 쇼다운은 전원 공개.
        HandHistoryEntry entry = new HandHistoryEntry(2, List.of(), List.of(), List.of(a, b), false, null, null,
                false, Set.of());
        Room room = new Room();
        room.addHandHistoryEntry(entry);

        HandHistoryHandView bFromSpectator = findHand(HandHistoryMapper.toResponse(room, "누구든").get(0), "b");
        assertFalse(bFromSpectator.holeCards().isEmpty());
    }

    @Test
    void 폴드로_끝난_핸드는_hands가_비고_승자_정보만_담긴다() {
        HandHistoryEntry entry = new HandHistoryEntry(3, List.of(),
                List.of(new HandHistoryEntry.PotEntry(500, List.of("a"))), List.of(), true, "a", "A", false, Set.of());
        Room room = new Room();
        room.addHandHistoryEntry(entry);

        HandHistoryEntryView view = HandHistoryMapper.toResponse(room, "b").get(0);
        assertTrue(view.wonByFold());
        assertTrue(view.hands().isEmpty());
        assertEquals("a", view.foldWinWinnerId());
        assertEquals("A", view.foldWinWinnerNickname());
        assertEquals(500, view.pots().get(0).amount());
    }
}
