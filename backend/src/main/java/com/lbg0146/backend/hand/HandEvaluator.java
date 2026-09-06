package com.lbg0146.backend.hand;

import com.lbg0146.backend.card.Card;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 카드 목록(홀 2장 + 커뮤니티 5장 등 5장 이상) 중 최적의 5장 조합으로 족보를 판정한다.
public class HandEvaluator {

    private HandEvaluator() {
    }

    // 가능한 5장 조합을 전부 평가해서 최댓값을 찾는다. 최대 C(7,5)=21가지라
    // 성능보다 정확성/단순성을 우선한 방식이다.
    public static EvaluatedHand evaluate(List<Card> cards) {
        if (cards.size() < 5) {
            throw new IllegalArgumentException("최소 5장이 필요합니다.");
        }

        EvaluatedHand best = null;
        for (List<Card> five : combinations(cards, 5)) {
            EvaluatedHand evaluated = evaluateFive(five);
            if (best == null || evaluated.compareTo(best) > 0) {
                best = evaluated;
            }
        }
        return best;
    }

    private static EvaluatedHand evaluateFive(List<Card> five) {
        List<Card> sorted = new ArrayList<>(five);
        sorted.sort(Comparator.comparingInt((Card c) -> c.rank().getValue()).reversed());

        boolean isFlush = sorted.stream().map(Card::suit).distinct().count() == 1;

        List<Integer> distinctValuesDesc = sorted.stream()
                .map(c -> c.rank().getValue())
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();

        Integer straightHigh = straightHighValue(distinctValuesDesc);
        boolean isStraight = straightHigh != null;

        Map<Integer, Integer> countByValue = new HashMap<>();
        for (Card card : sorted) {
            countByValue.merge(card.rank().getValue(), 1, Integer::sum);
        }

        List<Integer> valuesByCountThenRank = countByValue.entrySet().stream()
                .sorted((a, b) -> {
                    int countCompare = b.getValue() - a.getValue();
                    return countCompare != 0 ? countCompare : b.getKey() - a.getKey();
                })
                .map(Map.Entry::getKey)
                .toList();

        List<Integer> countsDesc = countByValue.values().stream()
                .sorted(Comparator.reverseOrder())
                .toList();

        // 아래로 갈수록 낮은 족보이므로, 높은 족보부터 순서대로 확인해서 처음 맞는 것을 채택한다.
        if (isStraight && isFlush) {
            return new EvaluatedHand(HandRank.STRAIGHT_FLUSH, List.of(straightHigh), sorted);
        }
        if (countsDesc.get(0) == 4) {
            return new EvaluatedHand(HandRank.FOUR_OF_A_KIND, valuesByCountThenRank, sorted);
        }
        if (countsDesc.get(0) == 3 && countsDesc.size() > 1 && countsDesc.get(1) == 2) {
            return new EvaluatedHand(HandRank.FULL_HOUSE, valuesByCountThenRank, sorted);
        }
        if (isFlush) {
            return new EvaluatedHand(HandRank.FLUSH, distinctValuesDesc, sorted);
        }
        if (isStraight) {
            return new EvaluatedHand(HandRank.STRAIGHT, List.of(straightHigh), sorted);
        }
        if (countsDesc.get(0) == 3) {
            return new EvaluatedHand(HandRank.THREE_OF_A_KIND, valuesByCountThenRank, sorted);
        }
        if (countsDesc.get(0) == 2 && countsDesc.size() > 1 && countsDesc.get(1) == 2) {
            return new EvaluatedHand(HandRank.TWO_PAIR, valuesByCountThenRank, sorted);
        }
        if (countsDesc.get(0) == 2) {
            return new EvaluatedHand(HandRank.ONE_PAIR, valuesByCountThenRank, sorted);
        }
        return new EvaluatedHand(HandRank.HIGH_CARD, distinctValuesDesc, sorted);
    }

    private static Integer straightHighValue(List<Integer> distinctValuesDesc) {
        if (distinctValuesDesc.size() != 5) {
            return null;
        }
        boolean consecutive = true;
        for (int i = 0; i < 4; i++) {
            if (distinctValuesDesc.get(i) - distinctValuesDesc.get(i + 1) != 1) {
                consecutive = false;
                break;
            }
        }
        if (consecutive) {
            return distinctValuesDesc.get(0);
        }
        // 휠(A-2-3-4-5): 에이스가 1로 취급되는 유일한 예외 케이스. 이 스트레이트의 최고값은
        // 에이스(14)가 아니라 5다.
        if (distinctValuesDesc.equals(List.of(14, 5, 4, 3, 2))) {
            return 5;
        }
        return null;
    }

    private static List<List<Card>> combinations(List<Card> cards, int k) {
        List<List<Card>> result = new ArrayList<>();
        combine(cards, k, 0, new ArrayList<>(), result);
        return result;
    }

    private static void combine(List<Card> cards, int k, int start, List<Card> current, List<List<Card>> result) {
        if (current.size() == k) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < cards.size(); i++) {
            current.add(cards.get(i));
            combine(cards, k, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }
}
