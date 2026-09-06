package com.lbg0146.backend.room.controller;

import com.lbg0146.backend.game.BettingRound;
import com.lbg0146.backend.game.GameEngine;
import com.lbg0146.backend.player.Player;
import com.lbg0146.backend.player.PlayerStatus;
import com.lbg0146.backend.room.Phase;
import com.lbg0146.backend.room.Room;
import com.lbg0146.backend.room.controller.dto.CardView;
import com.lbg0146.backend.room.controller.dto.PlayerView;
import com.lbg0146.backend.room.controller.dto.PotView;
import com.lbg0146.backend.room.controller.dto.RoomStateResponse;

import java.util.List;

// 도메인 상태(Room/GameEngine)를 API 응답 DTO로 변환한다. 홀카드 노출 규칙이 여기 모여 있다.
public class RoomStateMapper {

    private RoomStateMapper() {
    }

    public static RoomStateResponse toResponse(GameEngine engine, String requestingPlayerId) {
        Room room = engine.getRoom();
        BettingRound round = engine.getCurrentBettingRound();

        List<PlayerView> players = room.getPlayers().stream()
                .map(player -> toPlayerView(player, room, requestingPlayerId))
                .toList();
        List<CardView> communityCards = room.getCommunityCards().stream()
                .map(CardView::from)
                .toList();
        List<PotView> pots = room.getPots().stream()
                .map(pot -> PotView.from(pot, room.getPlayers()))
                .toList();

        return new RoomStateResponse(
                room.getPhase(),
                communityCards,
                pots,
                players,
                room.getDealerButtonPosition(),
                round == null ? null : round.getCurrentBet(),
                round == null ? null : round.getMinimumRaise(),
                round == null ? null : round.getCurrentActorId().orElse(null)
        );
    }

    // 본인이거나, 쇼다운 단계에서 폴드하지 않은 플레이어(공개된 것으로 간주)일 때만 홀카드를 보여준다.
    private static PlayerView toPlayerView(Player player, Room room, String requestingPlayerId) {
        boolean isSelf = player.getId().equals(requestingPlayerId);
        boolean revealedAtShowdown = room.getPhase() == Phase.SHOWDOWN && player.getStatus() != PlayerStatus.FOLDED;

        List<CardView> holeCards = (isSelf || revealedAtShowdown)
                ? player.getHoleCards().stream().map(CardView::from).toList()
                : List.of();

        return new PlayerView(player.getId(), player.getNickname(), player.getChips(),
                player.getStatus(), player.getCurrentRoundBet(), holeCards);
    }
}
