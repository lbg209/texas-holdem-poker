package com.lbg0146.backend.hand;

import com.lbg0146.backend.card.Card;
import com.lbg0146.backend.card.Rank;
import com.lbg0146.backend.card.Suit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandEvaluatorTest {

    private static Card card(Suit suit, Rank rank) {
        return new Card(suit, rank);
    }

    @Test
    void 하이카드를_판정한다() {
        List<Card> cards = List.of(
                card(Suit.SPADE, Rank.ACE),
                card(Suit.HEART, Rank.KING),
                card(Suit.DIAMOND, Rank.NINE),
                card(Suit.CLUB, Rank.FIVE),
                card(Suit.SPADE, Rank.TWO)
        );

        EvaluatedHand result = HandEvaluator.evaluate(cards);

        assertEquals(HandRank.HIGH_CARD, result.getHandRank());
        assertEquals(List.of(14, 13, 9, 5, 2), result.getTiebreakers());
    }

    @Test
    void 원페어를_판정한다() {
        List<Card> cards = List.of(
                card(Suit.SPADE, Rank.ACE),
                card(Suit.HEART, Rank.ACE),
                card(Suit.DIAMOND, Rank.KING),
                card(Suit.CLUB, Rank.NINE),
                card(Suit.SPADE, Rank.FIVE)
        );

        EvaluatedHand result = HandEvaluator.evaluate(cards);

        assertEquals(HandRank.ONE_PAIR, result.getHandRank());
        assertEquals(List.of(14, 13, 9, 5), result.getTiebreakers());
    }

    @Test
    void 투페어를_판정한다() {
        List<Card> cards = List.of(
                card(Suit.SPADE, Rank.ACE),
                card(Suit.HEART, Rank.ACE),
                card(Suit.DIAMOND, Rank.KING),
                card(Suit.CLUB, Rank.KING),
                card(Suit.SPADE, Rank.NINE)
        );

        EvaluatedHand result = HandEvaluator.evaluate(cards);

        assertEquals(HandRank.TWO_PAIR, result.getHandRank());
        assertEquals(List.of(14, 13, 9), result.getTiebreakers());
    }

    @Test
    void 트리플을_판정한다() {
        List<Card> cards = List.of(
                card(Suit.SPADE, Rank.ACE),
                card(Suit.HEART, Rank.ACE),
                card(Suit.DIAMOND, Rank.ACE),
                card(Suit.CLUB, Rank.KING),
                card(Suit.SPADE, Rank.NINE)
        );

        EvaluatedHand result = HandEvaluator.evaluate(cards);

        assertEquals(HandRank.THREE_OF_A_KIND, result.getHandRank());
        assertEquals(List.of(14, 13, 9), result.getTiebreakers());
    }

    @Test
    void 스트레이트를_판정한다() {
        List<Card> cards = List.of(
                card(Suit.SPADE, Rank.NINE),
                card(Suit.HEART, Rank.EIGHT),
                card(Suit.DIAMOND, Rank.SEVEN),
                card(Suit.CLUB, Rank.SIX),
                card(Suit.SPADE, Rank.FIVE)
        );

        EvaluatedHand result = HandEvaluator.evaluate(cards);

        assertEquals(HandRank.STRAIGHT, result.getHandRank());
        assertEquals(List.of(9), result.getTiebreakers());
    }

    @Test
    void 에이스로_시작하는_로우_스트레이트_휠을_판정한다() {
        List<Card> cards = List.of(
                card(Suit.SPADE, Rank.ACE),
                card(Suit.HEART, Rank.TWO),
                card(Suit.DIAMOND, Rank.THREE),
                card(Suit.CLUB, Rank.FOUR),
                card(Suit.SPADE, Rank.FIVE)
        );

        EvaluatedHand wheel = HandEvaluator.evaluate(cards);

        assertEquals(HandRank.STRAIGHT, wheel.getHandRank());
        assertEquals(List.of(5), wheel.getTiebreakers());

        List<Card> sixHighStraight = List.of(
                card(Suit.SPADE, Rank.SIX),
                card(Suit.HEART, Rank.FIVE),
                card(Suit.DIAMOND, Rank.FOUR),
                card(Suit.CLUB, Rank.THREE),
                card(Suit.SPADE, Rank.TWO)
        );
        EvaluatedHand sixHigh = HandEvaluator.evaluate(sixHighStraight);

        assertTrue(sixHigh.compareTo(wheel) > 0, "6-하이 스트레이트가 휠(5-하이)보다 높아야 한다");
    }

    @Test
    void 플러시를_판정한다() {
        List<Card> cards = List.of(
                card(Suit.SPADE, Rank.ACE),
                card(Suit.SPADE, Rank.KING),
                card(Suit.SPADE, Rank.NINE),
                card(Suit.SPADE, Rank.FIVE),
                card(Suit.SPADE, Rank.TWO)
        );

        EvaluatedHand result = HandEvaluator.evaluate(cards);

        assertEquals(HandRank.FLUSH, result.getHandRank());
        assertEquals(List.of(14, 13, 9, 5, 2), result.getTiebreakers());
    }

    @Test
    void 풀하우스를_판정한다() {
        List<Card> cards = List.of(
                card(Suit.SPADE, Rank.ACE),
                card(Suit.HEART, Rank.ACE),
                card(Suit.DIAMOND, Rank.ACE),
                card(Suit.CLUB, Rank.KING),
                card(Suit.SPADE, Rank.KING)
        );

        EvaluatedHand result = HandEvaluator.evaluate(cards);

        assertEquals(HandRank.FULL_HOUSE, result.getHandRank());
        assertEquals(List.of(14, 13), result.getTiebreakers());
    }

    @Test
    void 포카드를_판정한다() {
        List<Card> cards = List.of(
                card(Suit.SPADE, Rank.ACE),
                card(Suit.HEART, Rank.ACE),
                card(Suit.DIAMOND, Rank.ACE),
                card(Suit.CLUB, Rank.ACE),
                card(Suit.SPADE, Rank.KING)
        );

        EvaluatedHand result = HandEvaluator.evaluate(cards);

        assertEquals(HandRank.FOUR_OF_A_KIND, result.getHandRank());
        assertEquals(List.of(14, 13), result.getTiebreakers());
    }

    @Test
    void 스트레이트플러시를_판정한다() {
        List<Card> cards = List.of(
                card(Suit.SPADE, Rank.NINE),
                card(Suit.SPADE, Rank.EIGHT),
                card(Suit.SPADE, Rank.SEVEN),
                card(Suit.SPADE, Rank.SIX),
                card(Suit.SPADE, Rank.FIVE)
        );

        EvaluatedHand result = HandEvaluator.evaluate(cards);

        assertEquals(HandRank.STRAIGHT_FLUSH, result.getHandRank());
        assertEquals(List.of(9), result.getTiebreakers());
    }

    @Test
    void 로열플러시는_에이스하이_스트레이트플러시로_판정된다() {
        List<Card> cards = List.of(
                card(Suit.SPADE, Rank.ACE),
                card(Suit.SPADE, Rank.KING),
                card(Suit.SPADE, Rank.QUEEN),
                card(Suit.SPADE, Rank.JACK),
                card(Suit.SPADE, Rank.TEN)
        );

        EvaluatedHand result = HandEvaluator.evaluate(cards);

        assertEquals(HandRank.STRAIGHT_FLUSH, result.getHandRank());
        assertEquals(List.of(14), result.getTiebreakers());
    }

    @Test
    void 일곱장중_최적의_다섯장을_골라_풀하우스를_완성한다() {
        // 홀카드: K K, 커뮤니티: K 9 9 4 2 -> 트리플 K + 페어 9 = 풀하우스
        List<Card> sevenCards = List.of(
                card(Suit.SPADE, Rank.KING),
                card(Suit.HEART, Rank.KING),
                card(Suit.DIAMOND, Rank.KING),
                card(Suit.CLUB, Rank.NINE),
                card(Suit.SPADE, Rank.NINE),
                card(Suit.HEART, Rank.FOUR),
                card(Suit.DIAMOND, Rank.TWO)
        );

        EvaluatedHand result = HandEvaluator.evaluate(sevenCards);

        assertEquals(HandRank.FULL_HOUSE, result.getHandRank());
        assertEquals(List.of(13, 9), result.getTiebreakers());
        assertEquals(5, result.getBestFive().size());
    }

    @Test
    void 같은_원페어라도_키커로_승부가_갈린다() {
        List<Card> higherKicker = List.of(
                card(Suit.SPADE, Rank.ACE),
                card(Suit.HEART, Rank.ACE),
                card(Suit.DIAMOND, Rank.KING),
                card(Suit.CLUB, Rank.NINE),
                card(Suit.SPADE, Rank.FIVE)
        );
        List<Card> lowerKicker = List.of(
                card(Suit.SPADE, Rank.ACE),
                card(Suit.CLUB, Rank.ACE),
                card(Suit.HEART, Rank.KING),
                card(Suit.DIAMOND, Rank.NINE),
                card(Suit.CLUB, Rank.FOUR)
        );

        EvaluatedHand higher = HandEvaluator.evaluate(higherKicker);
        EvaluatedHand lower = HandEvaluator.evaluate(lowerKicker);

        assertTrue(higher.compareTo(lower) > 0);
    }

    @Test
    void 완전히_동일한_핸드는_무승부다() {
        List<Card> handA = List.of(
                card(Suit.SPADE, Rank.ACE),
                card(Suit.HEART, Rank.KING),
                card(Suit.DIAMOND, Rank.NINE),
                card(Suit.CLUB, Rank.FIVE),
                card(Suit.SPADE, Rank.TWO)
        );
        List<Card> handB = List.of(
                card(Suit.SPADE, Rank.ACE),
                card(Suit.HEART, Rank.KING),
                card(Suit.DIAMOND, Rank.NINE),
                card(Suit.CLUB, Rank.FIVE),
                card(Suit.SPADE, Rank.TWO)
        );

        EvaluatedHand a = HandEvaluator.evaluate(handA);
        EvaluatedHand b = HandEvaluator.evaluate(handB);

        assertEquals(0, a.compareTo(b));
    }

    @Test
    void 족보_등급간_우선순위가_지켜진다() {
        EvaluatedHand straight = HandEvaluator.evaluate(List.of(
                card(Suit.SPADE, Rank.NINE), card(Suit.HEART, Rank.EIGHT),
                card(Suit.DIAMOND, Rank.SEVEN), card(Suit.CLUB, Rank.SIX), card(Suit.SPADE, Rank.FIVE)
        ));
        EvaluatedHand flush = HandEvaluator.evaluate(List.of(
                card(Suit.SPADE, Rank.ACE), card(Suit.SPADE, Rank.KING),
                card(Suit.SPADE, Rank.NINE), card(Suit.SPADE, Rank.FIVE), card(Suit.SPADE, Rank.TWO)
        ));
        EvaluatedHand fullHouse = HandEvaluator.evaluate(List.of(
                card(Suit.SPADE, Rank.ACE), card(Suit.HEART, Rank.ACE), card(Suit.DIAMOND, Rank.ACE),
                card(Suit.CLUB, Rank.KING), card(Suit.SPADE, Rank.KING)
        ));

        assertTrue(flush.compareTo(straight) > 0, "플러시는 스트레이트보다 높아야 한다");
        assertTrue(fullHouse.compareTo(flush) > 0, "풀하우스는 플러시보다 높아야 한다");
    }
}
