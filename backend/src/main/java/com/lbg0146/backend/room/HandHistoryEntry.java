package com.lbg0146.backend.room;

import com.lbg0146.backend.card.Card;
import com.lbg0146.backend.hand.EvaluatedHand;
import com.lbg0146.backend.player.Player;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// 핸드 히스토리 패널(방 안 한정, DB 저장 없이 메모리에만 유지)에 쌓이는 항목 하나. 폴드로 끝난
// 핸드(wonByFold=true)는 hands가 비어 있고 foldWinWinnerId/Nickname만 채워진다. 실제 쇼다운이면
// hands에 쇼다운까지 간(폴드하지 않은) 전원의 실제 홀카드+족보가 들어있는데, 이건 항상 "진실"이고
// — 공개 여부(머크 등) 판단은 여기 저장하지 않고, 읽는 시점에 requestingPlayerId +
// headsUpShowdown/revealedPlayerIds를 보고 HandHistoryMapper가 매번 다시 계산한다(실시간 조회
// 응답의 홀카드 공개 규칙과 완전히 동일한 방식).
public record HandHistoryEntry(
        int handNumber,
        List<Card> communityCards,
        List<PotEntry> pots,
        List<HandEntry> hands,
        boolean wonByFold,
        String foldWinWinnerId,
        String foldWinWinnerNickname,
        // 실제 쇼다운이 "헤즈업 공개/머크" 특수 흐름이었는지 — 3명 이상 쇼다운은 항상 전원 자동
        // 공개이므로 이 값과 무관하게 다 보이고, 헤즈업이었으면 revealedPlayerIds에 있는 사람만 보인다.
        boolean headsUpShowdown,
        Set<String> revealedPlayerIds
) {

    public record PotEntry(int amount, List<String> winnerIds) {
    }

    public record HandEntry(String playerId, String nickname, List<Card> holeCards, EvaluatedHand evaluatedHand, boolean isWinner) {
    }

    // GameEngine.onHandConcluded()에서 호출된다. room.getLastShowdownResult()/getPots() 등 핸드가
    // 막 끝난 직후의 Room 상태를 그대로 스냅샷으로 옮겨 담는다.
    public static HandHistoryEntry capture(int handNumber, Room room) {
        List<Card> communityCards = List.copyOf(room.getCommunityCards());

        if (room.isWonByFold()) {
            Player winner = room.findPlayer(room.getFoldWinWinnerId());
            List<PotEntry> pots = room.getPots().stream()
                    .map(pot -> new PotEntry(pot.amount(), List.copyOf(pot.eligiblePlayerIds())))
                    .toList();
            return new HandHistoryEntry(handNumber, communityCards, pots, List.of(), true,
                    winner.getId(), winner.getNickname(), false, Set.of());
        }

        var result = room.getLastShowdownResult();
        Set<String> winnerIds = result.potResults().stream()
                .filter(potResult -> potResult.pot().eligiblePlayerIds().size() > 1)
                .flatMap(potResult -> potResult.winners().stream())
                .map(Player::getId)
                .collect(Collectors.toSet());

        List<PotEntry> pots = result.potResults().stream()
                .map(potResult -> new PotEntry(potResult.pot().amount(),
                        potResult.winners().stream().map(Player::getId).toList()))
                .toList();

        List<HandEntry> hands = result.handsByPlayerId().entrySet().stream()
                .map(e -> {
                    Player player = room.findPlayer(e.getKey());
                    return new HandEntry(player.getId(), player.getNickname(), List.copyOf(player.getHoleCards()),
                            e.getValue(), winnerIds.contains(player.getId()));
                })
                .toList();

        return new HandHistoryEntry(handNumber, communityCards, pots, hands, false, null, null,
                room.isHeadsUpShowdown(), Set.copyOf(room.getVoluntarilyRevealedIds()));
    }
}
