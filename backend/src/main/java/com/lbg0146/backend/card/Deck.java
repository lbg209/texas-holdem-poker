package com.lbg0146.backend.card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class Deck {

    private final List<Card> cards = new ArrayList<>();

    public Deck() {
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(new Card(suit, rank));
            }
        }
    }

    public void shuffle() {
        shuffle(new Random());
    }

    public void shuffle(Random random) {
        Collections.shuffle(cards, random);
    }

    public Card draw() {
        if (cards.isEmpty()) {
            throw new IllegalStateException("덱에 남은 카드가 없습니다.");
        }
        // 맨 끝(리스트의 마지막 인덱스)에서 제거해야 나머지 카드를 앞으로 당길 필요가 없어 O(1)이다.
        return cards.remove(cards.size() - 1);
    }

    public int remainingCount() {
        return cards.size();
    }
}
