package com.lbg0146.backend.card;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckTest {

    @Test
    void 새_덱은_중복_없이_52장이다() {
        Deck deck = new Deck();
        assertEquals(52, deck.remainingCount());

        Set<Card> uniqueCards = new HashSet<>();
        while (deck.remainingCount() > 0) {
            uniqueCards.add(deck.draw());
        }
        assertEquals(52, uniqueCards.size());
    }

    @Test
    void 셔플_후에도_52장이_유지된다() {
        Deck deck = new Deck();
        deck.shuffle(new Random(42));
        assertEquals(52, deck.remainingCount());

        Set<Card> uniqueCards = new HashSet<>();
        while (deck.remainingCount() > 0) {
            uniqueCards.add(deck.draw());
        }
        assertEquals(52, uniqueCards.size());
    }

    @Test
    void draw할_때마다_카드_수가_감소하고_중복되지_않는다() {
        Deck deck = new Deck();
        deck.shuffle(new Random(1));

        Set<Card> drawn = new HashSet<>();
        int expectedCount = 52;
        for (int i = 0; i < 52; i++) {
            assertEquals(expectedCount, deck.remainingCount());
            Card card = deck.draw();
            assertTrue(drawn.add(card), "중복된 카드가 뽑히면 안 된다: " + card);
            expectedCount--;
        }
        assertEquals(0, deck.remainingCount());
    }

    @Test
    void 카드가_없을_때_draw하면_예외가_발생한다() {
        Deck deck = new Deck();
        for (int i = 0; i < 52; i++) {
            deck.draw();
        }
        assertThrows(IllegalStateException.class, deck::draw);
    }
}
