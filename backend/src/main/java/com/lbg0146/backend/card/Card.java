package com.lbg0146.backend.card;

public record Card(Suit suit, Rank rank) {

    @Override
    public String toString() {
        return rankSymbol() + suitSymbol();
    }

    private String rankSymbol() {
        return switch (rank) {
            case TWO -> "2";
            case THREE -> "3";
            case FOUR -> "4";
            case FIVE -> "5";
            case SIX -> "6";
            case SEVEN -> "7";
            case EIGHT -> "8";
            case NINE -> "9";
            case TEN -> "10";
            case JACK -> "J";
            case QUEEN -> "Q";
            case KING -> "K";
            case ACE -> "A";
        };
    }

    private String suitSymbol() {
        return switch (suit) {
            case SPADE -> "♠";
            case HEART -> "♥";
            case DIAMOND -> "♦";
            case CLUB -> "♣";
        };
    }
}
