package com.lbg0146.backend.room.controller.dto;

import com.lbg0146.backend.card.Card;

// suit/rank는 프론트에서 카드 이미지 등을 구조적으로 다룰 때, display는 그대로 보여줄 때 쓴다.
public record CardView(String suit, String rank, String display) {

    public static CardView from(Card card) {
        return new CardView(card.suit().name(), card.rank().name(), card.toString());
    }
}
