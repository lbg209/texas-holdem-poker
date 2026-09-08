package com.lbg0146.backend.room.controller;

import com.lbg0146.backend.game.BettingRound;
import com.lbg0146.backend.game.GameEngine;
import com.lbg0146.backend.game.PotCalculator;
import com.lbg0146.backend.game.ShowdownResult;
import com.lbg0146.backend.hand.EvaluatedHand;
import com.lbg0146.backend.player.Player;
import com.lbg0146.backend.player.PlayerStatus;
import com.lbg0146.backend.room.Phase;
import com.lbg0146.backend.room.Room;
import com.lbg0146.backend.room.controller.dto.CardView;
import com.lbg0146.backend.room.controller.dto.PlayerView;
import com.lbg0146.backend.room.controller.dto.PotView;
import com.lbg0146.backend.room.controller.dto.RoomStateResponse;
import com.lbg0146.backend.room.controller.dto.ShowdownHandView;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        // room.pots는 핸드 종료 시 실제 정산에만 쓰이는 필드라 베팅 중엔 비어 있다. 조회 응답은
        // 항상 그 시점 기준의 실시간 팟을 보여줘야 하므로, GameEngine의 정산 로직과 별개로
        // totalHandContribution을 기준 삼아 매번 다시 계산한다(핸드 종료 후에도 같은 값이 나온다).
        List<PotView> pots = PotCalculator.calculate(room.getPlayers()).stream()
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
                round == null ? null : round.getCurrentActorId().orElse(null),
                toShowdownHands(room)
        );
    }

    // 실제 쇼다운(카드 비교)까지 간 경우에만 채운다 — 폴드로 끝난 핸드는 대상이 아니다.
    private static List<ShowdownHandView> toShowdownHands(Room room) {
        ShowdownResult result = room.getLastShowdownResult();
        if (room.getPhase() != Phase.SHOWDOWN || room.isWonByFold() || result == null) {
            return null;
        }

        Set<String> winnerIds = result.potResults().stream()
                .flatMap(potResult -> potResult.winners().stream())
                .map(Player::getId)
                .collect(Collectors.toSet());

        return room.getPlayers().stream()
                .filter(player -> result.handsByPlayerId().containsKey(player.getId()))
                .map(player -> {
                    EvaluatedHand hand = result.handsByPlayerId().get(player.getId());
                    List<CardView> bestFive = hand.getBestFive().stream().map(CardView::from).toList();
                    return new ShowdownHandView(player.getId(), hand.getHandRank(), bestFive,
                            winnerIds.contains(player.getId()));
                })
                .toList();
    }

    // 본인이거나, 실제 쇼다운(카드 비교)에서 폴드하지 않은 플레이어(공개된 것으로 간주)일 때만 홀카드를 보여준다.
    // 전원 폴드로 조기 종료된 경우(wonByFold)는 카드를 비교한 적이 없으므로 공개하지 않는다.
    private static PlayerView toPlayerView(Player player, Room room, String requestingPlayerId) {
        boolean isSelf = player.getId().equals(requestingPlayerId);
        boolean revealedAtShowdown = room.getPhase() == Phase.SHOWDOWN
                && !room.isWonByFold()
                && player.getStatus() != PlayerStatus.FOLDED;

        List<CardView> holeCards = (isSelf || revealedAtShowdown)
                ? player.getHoleCards().stream().map(CardView::from).toList()
                : List.of();

        return new PlayerView(player.getId(), player.getNickname(), player.getChips(),
                player.getStatus(), player.getCurrentRoundBet(), player.getTotalHandContribution(),
                player.getLastAction(), player.getChips() - player.getChipsAtHandStart(), holeCards);
    }
}
