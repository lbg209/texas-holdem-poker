package com.lbg0146.backend.game;

import com.lbg0146.backend.hand.EvaluatedHand;
import com.lbg0146.backend.player.Player;
import com.lbg0146.backend.room.Pot;

import java.util.List;

public record ShowdownResult(List<PotResult> potResults) {

    public record PotResult(Pot pot, List<Player> winners, EvaluatedHand winningHand, int amountPerWinner) {
    }
}
