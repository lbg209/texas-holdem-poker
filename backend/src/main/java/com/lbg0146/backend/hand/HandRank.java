package com.lbg0146.backend.hand;

// 선언 순서 자체가 강함의 순서다(ordinal 비교에 사용됨). 로열플러시는 별도 값 없이
// STRAIGHT_FLUSH 중 가장 높은 tiebreaker(에이스 하이)로 표현한다.
public enum HandRank {
    HIGH_CARD,
    ONE_PAIR,
    TWO_PAIR,
    THREE_OF_A_KIND,
    STRAIGHT,
    FLUSH,
    FULL_HOUSE,
    FOUR_OF_A_KIND,
    STRAIGHT_FLUSH
}
