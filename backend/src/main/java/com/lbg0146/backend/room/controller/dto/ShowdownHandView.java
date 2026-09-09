package com.lbg0146.backend.room.controller.dto;

import com.lbg0146.backend.hand.HandRank;

import java.util.List;

// 실제 쇼다운(카드 비교)까지 간 경우에만 채워진다. 폴드로 핸드가 끝난 경우는 대상이 아니다.
// handRank/bestFive는 헤즈업 쇼다운에서 이 플레이어가 아직 공개하지 않았거나(대기 중) 끝내
// 머크했으면 null이다 — isWinner는 그와 무관하게 항상 실제 결과를 정확히 반영한다(카드는 안
// 보여줘도 승리 배지/칩 이동 연출은 정상적으로 나와야 하므로).
public record ShowdownHandView(String playerId, HandRank handRank, List<CardView> bestFive, boolean isWinner) {
}
