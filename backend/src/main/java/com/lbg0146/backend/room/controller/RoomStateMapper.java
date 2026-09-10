package com.lbg0146.backend.room.controller;

import com.lbg0146.backend.game.BettingRound;
import com.lbg0146.backend.game.GameEngine;
import com.lbg0146.backend.game.PotCalculator;
import com.lbg0146.backend.game.ShowdownResult;
import com.lbg0146.backend.hand.EvaluatedHand;
import com.lbg0146.backend.hand.HandRank;
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

    public static RoomStateResponse toResponse(GameEngine engine, String requestingPlayerId, Long turnDeadlineAtMillis,
            Long nextHandAtMillis, Long headsUpRevealDeadlineAtMillis, Long gameOverResetAtMillis) {
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
                toShowdownHands(room, requestingPlayerId),
                engine.resolveWinnerId(),
                engine.resolveWinnerNickname(),
                turnDeadlineAtMillis,
                nextHandAtMillis,
                room.getFoldWinWinnerId(),
                room.getHeadsUpDeciderPlayerId(),
                headsUpRevealDeadlineAtMillis,
                List.copyOf(room.getVoluntarilyRevealedIds()),
                gameOverResetAtMillis,
                room.getStartingChips(),
                room.getSmallBlind(),
                room.getBigBlind(),
                room.getMaxPlayers(),
                room.getRoomCode(),
                room.getName(),
                room.isPrivate()
        );
    }

    // 실제 쇼다운(카드 비교)까지 간 경우에만 채운다 — 폴드로 끝난 핸드는 대상이 아니다. 헤즈업
    // 쇼다운에서 아직 공개하지 않은(또는 끝내 머크한) 사람은 handRank/bestFive를 null로 감춘다 —
    // isWinner만 항상 정확한 값(칩을 실제로 받았는지)을 유지해서, 카드는 안 보여도 승리 배지/칩
    // 이동 연출은 정상적으로 작동한다.
    private static List<ShowdownHandView> toShowdownHands(Room room, String requestingPlayerId) {
        ShowdownResult result = room.getLastShowdownResult();
        if (room.getPhase() != Phase.SHOWDOWN || room.isWonByFold() || result == null) {
            return null;
        }

        // eligible이 1명뿐인 팟(상대가 콜 못 해서 아무도 못 다투고 자기 초과 베팅을 그냥 돌려받는
        // 경우)은 실제 카드 비교로 이긴 게 아니므로 WINNER 판정에서 제외한다. 그렇지 않으면 진짜
        // 쇼다운(메인팟)에서 진 사람이, 자기 돈만 돌아온 사이드팟 때문에 승자로 잘못 표시된다.
        Set<String> winnerIds = result.potResults().stream()
                .filter(potResult -> potResult.pot().eligiblePlayerIds().size() > 1)
                .flatMap(potResult -> potResult.winners().stream())
                .map(Player::getId)
                .collect(Collectors.toSet());

        return room.getPlayers().stream()
                .filter(player -> result.handsByPlayerId().containsKey(player.getId()))
                .map(player -> {
                    boolean visible = player.getId().equals(requestingPlayerId)
                            || !room.isHeadsUpShowdown()
                            || room.getVoluntarilyRevealedIds().contains(player.getId());
                    EvaluatedHand hand = result.handsByPlayerId().get(player.getId());
                    HandRank handRank = visible ? hand.getHandRank() : null;
                    List<CardView> bestFive = visible ? hand.getBestFive().stream().map(CardView::from).toList() : null;
                    return new ShowdownHandView(player.getId(), handRank, bestFive, winnerIds.contains(player.getId()));
                })
                .toList();
    }

    // 본인이거나, 실제 쇼다운(카드 비교)에서 폴드하지 않은 플레이어(3명 이상 쇼다운은 기존처럼
    // 전원 자동 공개)이거나, voluntarilyRevealedIds에 포함된 경우(폴드승 승자의 자원 공개, 헤즈업
    // 쇼다운에서 무작위로 뽑혀 자동 공개된 사람, 헤즈업 결정자가 공개를 선택했거나 시간 초과로
    // 강제 공개된 경우)에만 홀카드를 보여준다. 헤즈업 쇼다운은 "폴드 안 하면 전원 공개" 규칙 대신
    // voluntarilyRevealedIds로만 공개 여부가 갈린다(그래야 결정자가 머크를 선택하면 실제로 숨겨진다).
    private static PlayerView toPlayerView(Player player, Room room, String requestingPlayerId) {
        boolean isSelf = player.getId().equals(requestingPlayerId);
        boolean revealedAtShowdown = room.getPhase() == Phase.SHOWDOWN
                && !room.isWonByFold()
                && !room.isHeadsUpShowdown()
                && player.getStatus() != PlayerStatus.FOLDED;
        boolean voluntarilyRevealed = room.getVoluntarilyRevealedIds().contains(player.getId());

        List<CardView> holeCards = (isSelf || revealedAtShowdown || voluntarilyRevealed)
                ? player.getHoleCards().stream().map(CardView::from).toList()
                : List.of();

        return new PlayerView(player.getId(), player.getNickname(), player.getChips(),
                player.getStatus(), player.getCurrentRoundBet(), player.getTotalHandContribution(),
                player.getLastAction(), player.getChips() - player.getChipsAtHandStart(), holeCards,
                player.isReady(), player.isAutoFolded(), player.isLeaving());
    }
}
