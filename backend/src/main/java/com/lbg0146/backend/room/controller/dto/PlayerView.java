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
// autoFolded는 status가 FOLDED일 때만 의미 있으며, 직접 FOLD를 누른 게 아니라 턴 타임아웃으로
// 자동 폴드됐는지를 나타낸다.
// leaving은 "나가기"를 예약했는지 — true면 핸드가 끝나는 즉시(또는 핸드 진행 중이 아니면 바로)
// 방에서 제거된다.
// isOwner는 방장(방을 만든 뒤 가장 먼저 입장했거나, 그 방장이 나가서 승계받은 사람)인지 —
// 순수 표시용 배지 + 강퇴 버튼 노출 여부 판단에 쓰인다.
// seatIndex는 고정 좌석제의 물리적 좌석 번호(0~maxPlayers-1) — 프론트가 이 번호로 빈 좌석을
// 가려내고 빈자리 클릭 시 입장/이동 API에 그대로 실어 보낸다.
public record PlayerView(
        String id,
        String nickname,
        int chips,
        PlayerStatus status,
        int currentRoundBet,
        int totalHandContribution,
        PlayerAction lastAction,
        int netChipChange,
        List<CardView> holeCards,
        boolean ready,
        boolean autoFolded,
        boolean leaving,
        boolean isOwner,
        int seatIndex
) {
}
