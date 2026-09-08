package com.lbg0146.backend.room.controller.dto;

import com.lbg0146.backend.hand.HandRank;

import java.util.List;

// 실제 쇼다운(카드 비교)까지 간 경우에만 채워진다. 폴드로 핸드가 끝난 경우는 대상이 아니다.
public record ShowdownHandView(String playerId, HandRank handRank, List<CardView> bestFive, boolean isWinner) {
}
