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
        String currentActorId
) {
}
