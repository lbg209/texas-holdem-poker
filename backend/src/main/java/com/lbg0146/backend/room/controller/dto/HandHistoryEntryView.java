package com.lbg0146.backend.room.controller.dto;

import java.util.List;

// wonByFold=true면 hands가 비어 있고(실제 쇼다운이 없었으므로) foldWinWinnerId/Nickname만 채워진다.
public record HandHistoryEntryView(
        int handNumber,
        List<CardView> communityCards,
        List<HandHistoryPotView> pots,
        List<HandHistoryHandView> hands,
        boolean wonByFold,
        String foldWinWinnerId,
        String foldWinWinnerNickname
) {
}
