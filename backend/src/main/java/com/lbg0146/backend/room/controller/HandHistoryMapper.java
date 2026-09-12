package com.lbg0146.backend.room.controller;

import com.lbg0146.backend.room.HandHistoryEntry;
import com.lbg0146.backend.room.Room;
import com.lbg0146.backend.room.controller.dto.CardView;
import com.lbg0146.backend.room.controller.dto.HandHistoryEntryView;
import com.lbg0146.backend.room.controller.dto.HandHistoryHandView;
import com.lbg0146.backend.room.controller.dto.HandHistoryPotView;

import java.util.List;

// Room이 들고 있는 HandHistoryEntry(항상 실제 값 — 머크 여부와 무관하게 전부 담겨 있음) 목록을,
// 요청자 관점의 공개 규칙을 적용해 응답 DTO로 바꾼다. 실시간 조회(RoomStateMapper)와 정확히 같은
// 규칙: 본인 카드는 항상 보이고, 남의 카드는 3명 이상 쇼다운(항상 전원 공개)이거나 실제로 공개됐던
// (voluntarilyRevealed) 경우에만 보인다 — 머크했으면 지난 핸드라도 영원히 안 보인다.
public class HandHistoryMapper {

    private HandHistoryMapper() {
    }

    public static List<HandHistoryEntryView> toResponse(Room room, String requestingPlayerId) {
        return room.getHandHistory().stream()
                .map(entry -> toEntryView(entry, requestingPlayerId))
                .toList();
    }

    private static HandHistoryEntryView toEntryView(HandHistoryEntry entry, String requestingPlayerId) {
        List<CardView> communityCards = entry.communityCards().stream().map(CardView::from).toList();
        List<HandHistoryPotView> pots = entry.pots().stream()
                .map(pot -> new HandHistoryPotView(pot.amount(), pot.winnerIds()))
                .toList();
        List<HandHistoryHandView> hands = entry.hands().stream()
                .map(hand -> toHandView(hand, entry, requestingPlayerId))
                .toList();

        return new HandHistoryEntryView(entry.handNumber(), communityCards, pots, hands, entry.wonByFold(),
                entry.foldWinWinnerId(), entry.foldWinWinnerNickname());
    }

    private static HandHistoryHandView toHandView(HandHistoryEntry.HandEntry hand, HandHistoryEntry entry,
            String requestingPlayerId) {
        boolean visible = hand.playerId().equals(requestingPlayerId)
                || !entry.headsUpShowdown()
                || entry.revealedPlayerIds().contains(hand.playerId());

        List<CardView> holeCards = visible ? hand.holeCards().stream().map(CardView::from).toList() : List.of();
        var handRank = visible ? hand.evaluatedHand().getHandRank() : null;
        List<CardView> bestFive = visible
                ? hand.evaluatedHand().getBestFive().stream().map(CardView::from).toList()
                : null;

        return new HandHistoryHandView(hand.playerId(), hand.nickname(), holeCards, handRank, bestFive, hand.isWinner());
    }
}
