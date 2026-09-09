package com.lbg0146.backend.room.controller.dto;

import com.lbg0146.backend.room.Phase;

import java.util.List;

public record RoomStateResponse(
        Phase phase,
        List<CardView> communityCards,
        List<PotView> pots,
        List<PlayerView> players,
        int dealerButtonPosition,
        Integer currentBet,
        Integer minimumRaise,
        String currentActorId,
        List<ShowdownHandView> showdownHands,
        String winnerId,
        Long turnDeadlineAtMillis,
        Long nextHandAtMillis,
        String foldWinWinnerId,
        // 헤즈업 쇼다운에서 공개/머크를 결정해야 하는 사람의 id. 아직 결정 전(대기 중)에만 non-null.
        String headsUpDeciderPlayerId,
        Long headsUpRevealDeadlineAtMillis,
        // 이번 핸드에서 카드가 보이기로 확정된 사람들의 id 목록(폴드승 자원 공개 + 헤즈업 자동/자원/강제 공개).
        List<String> revealedPlayerIds
) {
}
