package com.lbg0146.backend.room.controller.dto;

import com.lbg0146.backend.player.Player;
import com.lbg0146.backend.room.Pot;

import java.util.List;

public record PotView(int amount, List<String> eligiblePlayerIds) {

    // Pot.eligiblePlayerIds()는 Set이라 순서가 보장되지 않으므로, 좌석 순서(seatOrder) 기준으로 정렬해서 내려준다.
    public static PotView from(Pot pot, List<Player> seatOrder) {
        List<String> ordered = seatOrder.stream()
                .map(Player::getId)
                .filter(id -> pot.eligiblePlayerIds().contains(id))
                .toList();
        return new PotView(pot.amount(), ordered);
    }
}
