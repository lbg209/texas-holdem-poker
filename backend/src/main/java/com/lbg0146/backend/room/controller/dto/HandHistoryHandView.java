package com.lbg0146.backend.room.controller.dto;

import com.lbg0146.backend.hand.HandRank;

import java.util.List;

// holeCards/handRank/bestFive는 요청자 본인이거나 그 핸드에서 실제로 공개됐던 경우(쇼다운 자동
// 공개, 자원 공개)에만 채워지고, 머크했으면 전부 비어 있다(null 아님, 빈 값) — 실시간 쇼다운 응답과
// 동일한 공개 규칙이다.
public record HandHistoryHandView(
        String playerId,
        String nickname,
        List<CardView> holeCards,
        HandRank handRank,
        List<CardView> bestFive,
        boolean isWinner
) {
}
