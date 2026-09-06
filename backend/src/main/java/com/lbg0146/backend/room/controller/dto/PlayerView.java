package com.lbg0146.backend.room.controller.dto;

import com.lbg0146.backend.player.PlayerStatus;

import java.util.List;

// holeCards는 본인이거나 쇼다운에서 공개된 경우에만 채워지고, 그 외엔 빈 리스트다.
public record PlayerView(
        String id,
        String nickname,
        int chips,
        PlayerStatus status,
        int currentRoundBet,
        List<CardView> holeCards
) {
}
