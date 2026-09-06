package com.lbg0146.backend.hand;

import com.lbg0146.backend.card.Card;

import java.util.List;

public class EvaluatedHand implements Comparable<EvaluatedHand> {

    private final HandRank handRank;
    private final List<Integer> tiebreakers;
    private final List<Card> bestFive;

    public EvaluatedHand(HandRank handRank, List<Integer> tiebreakers, List<Card> bestFive) {
        this.handRank = handRank;
        this.tiebreakers = tiebreakers;
        this.bestFive = bestFive;
    }

    public HandRank getHandRank() {
        return handRank;
    }

    public List<Integer> getTiebreakers() {
        return tiebreakers;
    }

    public List<Card> getBestFive() {
        return bestFive;
    }

    // 족보 등급(handRank)을 먼저 비교하고, 등급이 같으면 tiebreakers를 앞에서부터
    // 순서대로 비교한다(값이 전부 같으면 0 = 무승부/스플릿 팟 대상).
    @Override
    public int compareTo(EvaluatedHand other) {
        int rankCompare = this.handRank.compareTo(other.handRank);
        if (rankCompare != 0) {
            return rankCompare;
        }
        for (int i = 0; i < tiebreakers.size(); i++) {
            int cmp = Integer.compare(tiebreakers.get(i), other.tiebreakers.get(i));
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }
}
