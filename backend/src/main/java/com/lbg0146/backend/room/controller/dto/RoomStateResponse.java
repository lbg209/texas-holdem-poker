package com.lbg0146.backend.room.controller.dto;

import com.lbg0146.backend.room.Phase;

import java.util.List;

public record RoomStateResponse(
        Phase phase,
        List<CardView> communityCards,
        List<PotView> pots,
        List<PlayerView> players,
        int dealerButtonPosition,
        Integer currentBet,
        Integer minimumRaise,
        String currentActorId,
        List<ShowdownHandView> showdownHands,
        String winnerId,
        // winnerId와 같은 시점에 고정된 닉네임 — 승자가 GAME OVER 카운트다운 도중 "나가기"로 방을
        // 빠져도 players 목록에서 다시 찾을 필요 없이 정확한 닉네임을 그대로 보여줄 수 있다.
        String winnerNickname,
        Long turnDeadlineAtMillis,
        Long nextHandAtMillis,
        String foldWinWinnerId,
        // 헤즈업 쇼다운에서 공개/머크를 결정해야 하는 사람의 id. 아직 결정 전(대기 중)에만 non-null.
        String headsUpDeciderPlayerId,
        Long headsUpRevealDeadlineAtMillis,
        // 이번 핸드에서 카드가 보이기로 확정된 사람들의 id 목록(폴드승 자원 공개 + 헤즈업 자동/자원/강제 공개).
        List<String> revealedPlayerIds,
        // GAME OVER(winnerId non-null) 상태에서 방이 자동으로 초기화되는 시각. GAME OVER가 아니면 null.
        Long gameOverResetAtMillis,
        // 이번 방의 설정값(시작 칩/스몰·빅블라인드/최대 인원). 방이 비어있을 때 "방 만들기"로 바꿀 수
        // 있고, 그 전까지는 Room의 기본값이다.
        int startingChips,
        int smallBlind,
        int bigBlind,
        int maxPlayers,
        // 방 정체성(로비/좌측 정보 패널 표시용). 비밀번호는 절대 포함하지 않는다.
        String roomCode,
        String name,
        boolean isPrivate
) {
}
