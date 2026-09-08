package com.lbg0146.backend.room.controller.dto;

import com.lbg0146.backend.player.PlayerAction;
import com.lbg0146.backend.player.PlayerStatus;

import java.util.List;

// holeCards는 본인이거나 쇼다운에서 공개된 경우에만 채워지고, 그 외엔 빈 리스트다.
// currentRoundBet은 이번 스트리트에서만 낸 금액(스트리트 전환 시 0으로 리셋),
// totalHandContribution은 이번 핸드 전체에서 낸 누적 금액(핸드가 끝날 때까지 유지)이다.
// lastAction은 이번 스트리트에서 마지막으로 취한 액션(currentRoundBet과 함께 스트리트 전환 시 null로 리셋)이다.
// netChipChange는 이번 핸드 시작 시점 대비 현재 칩 증감(chips - chipsAtHandStart)이다 — 핸드가 끝나기 전에는
// 베팅한 만큼 음수로 보이다가, 핸드가 끝나 팟을 받으면 최종 손익으로 확정된다.
public record PlayerView(
        String id,
        String nickname,
        int chips,
        PlayerStatus status,
        int currentRoundBet,
        int totalHandContribution,
        PlayerAction lastAction,
        int netChipChange,
        List<CardView> holeCards
) {
}
