package com.lbg0146.backend.game;

import com.lbg0146.backend.hand.EvaluatedHand;
import com.lbg0146.backend.player.Player;
import com.lbg0146.backend.room.Pot;

import java.util.List;
import java.util.Map;

// handsByPlayerId: 폴드하지 않고 쇼다운까지 간 전원의 족보(승패와 무관하게 전부 포함).
public record ShowdownResult(List<PotResult> potResults, Map<String, EvaluatedHand> handsByPlayerId) {

    public record PotResult(Pot pot, List<Player> winners, EvaluatedHand winningHand, int amountPerWinner) {
    }
}
