package com.lbg0146.backend.game;

import com.lbg0146.backend.card.Card;
import com.lbg0146.backend.exception.GameStateException;
import com.lbg0146.backend.hand.EvaluatedHand;
import com.lbg0146.backend.hand.HandEvaluator;
import com.lbg0146.backend.player.Player;
import com.lbg0146.backend.player.PlayerAction;
import com.lbg0146.backend.player.PlayerStatus;
import com.lbg0146.backend.room.Phase;
import com.lbg0146.backend.room.Pot;
import com.lbg0146.backend.room.Room;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Room의 상태를 받아 한 핸드의 진행(블라인드→딜→베팅→쇼다운)을 조율한다.
public class GameEngine {

    private final Room room;
    private BettingRound currentBettingRound;

    public GameEngine(Room room) {
        this.room = room;
    }

    public void startHand() {
        if (room.getPlayers().size() < Room.MIN_PLAYERS) {
            throw new GameStateException("최소 " + Room.MIN_PLAYERS + "명이 필요합니다.");
        }

        room.getPlayers().forEach(Player::resetForNewHand);
        room.getCommunityCards().clear();
        room.setPots(new ArrayList<>());
        room.resetDeck();
        room.getDeck().shuffle();
        room.moveButtonToNextSeat();

        // 헤즈업(2인)은 버튼 자리가 곧 스몰블라인드이고, 프리플랍에서 버튼이 먼저 액션한다.
        boolean headsUp = room.getPlayers().size() == 2;
        postBlinds(headsUp);
        dealHoleCards();

        room.setPhase(Phase.PREFLOP);
        int preflopStartOffset = headsUp ? 0 : 3; // 버튼(0)->SB(1)->BB(2)->UTG(3)
        startNewBettingRound(preflopStartOffset, Room.BIG_BLIND, Room.BIG_BLIND);
    }

    private void postBlinds(boolean headsUp) {
        Player smallBlindPlayer = playerAtOffset(headsUp ? 0 : 1);
        Player bigBlindPlayer = playerAtOffset(headsUp ? 1 : 2);
        smallBlindPlayer.commitChips(Room.SMALL_BLIND);
        bigBlindPlayer.commitChips(Room.BIG_BLIND);
    }

    private void dealHoleCards() {
        for (int i = 0; i < 2; i++) {
            for (Player player : room.getPlayers()) {
                player.receiveHoleCard(room.getDeck().draw());
            }
        }
    }

    private Player playerAtOffset(int offset) {
        List<Player> players = room.getPlayers();
        int index = (room.getDealerButtonPosition() + offset) % players.size();
        return players.get(index);
    }

    private void startNewBettingRound(int startOffset, int currentBet, int minimumRaise) {
        List<Player> activePlayers = activePlayersFrom(startOffset);
        currentBettingRound = new BettingRound(room.getPlayers(), activePlayers, currentBet, minimumRaise);
    }

    // startOffset 좌석부터 시계방향으로 순회하며 아직 ACTIVE(폴드/올인 아님)인 플레이어만 순서대로 반환한다.
    private List<Player> activePlayersFrom(int startOffset) {
        List<Player> players = room.getPlayers();
        int seatCount = players.size();
        List<Player> ordered = new ArrayList<>();
        for (int i = 0; i < seatCount; i++) {
            Player player = players.get((startOffset + i) % seatCount);
            if (player.getStatus() == PlayerStatus.ACTIVE) {
                ordered.add(player);
            }
        }
        return ordered;
    }

    public void applyAction(String playerId, PlayerAction action, int amount) {
        Player actor = room.findPlayer(playerId);
        currentBettingRound.applyAction(actor, action, amount);

        long remaining = room.getPlayers().stream()
                .filter(p -> p.getStatus() != PlayerStatus.FOLDED)
                .count();
        if (remaining <= 1) {
            // 전원 폴드로 한 명만 남으면 쇼다운 없이 즉시 핸드를 종료한다.
            finishHandByFold();
            return;
        }

        if (currentBettingRound.isComplete()) {
            advancePhase();
        }
    }

    private void finishHandByFold() {
        room.setPots(PotCalculator.calculate(room.getPlayers()));
        Player winner = room.getPlayers().stream()
                .filter(p -> p.getStatus() != PlayerStatus.FOLDED)
                .findFirst()
                .orElseThrow();
        int totalPot = room.getPots().stream().mapToInt(Pot::amount).sum();
        winner.addChips(totalPot);
        room.setPhase(Phase.SHOWDOWN);
    }

    private void advancePhase() {
        boolean autoRunout = currentBettingRound.noFurtherBettingPossible();

        Phase next = switch (room.getPhase()) {
            case PREFLOP -> Phase.FLOP;
            case FLOP -> Phase.TURN;
            case TURN -> Phase.RIVER;
            case RIVER -> Phase.SHOWDOWN;
            case SHOWDOWN -> throw new GameStateException("이미 쇼다운 단계입니다.");
        };
        room.setPhase(next);
        room.getPlayers().forEach(Player::resetForNewRound);

        if (next == Phase.SHOWDOWN) {
            resolveShowdown();
            return;
        }

        dealCommunity(next == Phase.FLOP ? 3 : 1);

        if (autoRunout) {
            // 베팅 가능한 플레이어가 1명 이하로 남았으므로(나머지는 올인) 새 베팅 라운드 없이
            // 커뮤니티 카드만 계속 오픈하며 바로 다음 단계로 진행한다.
            advancePhase();
        } else {
            // 플랍 이후에는 버튼 다음 첫 활성 플레이어부터 시작한다.
            startNewBettingRound(1, 0, Room.BIG_BLIND);
        }
    }

    private void dealCommunity(int count) {
        for (int i = 0; i < count; i++) {
            room.getCommunityCards().add(room.getDeck().draw());
        }
    }

    public ShowdownResult resolveShowdown() {
        room.setPots(PotCalculator.calculate(room.getPlayers()));

        List<Player> inHand = room.getPlayers().stream()
                .filter(p -> p.getStatus() != PlayerStatus.FOLDED)
                .toList();

        Map<String, EvaluatedHand> handsByPlayerId = new HashMap<>();
        for (Player player : inHand) {
            List<Card> allCards = new ArrayList<>(player.getHoleCards());
            allCards.addAll(room.getCommunityCards());
            handsByPlayerId.put(player.getId(), HandEvaluator.evaluate(allCards));
        }

        List<ShowdownResult.PotResult> potResults = new ArrayList<>();
        for (Pot pot : room.getPots()) {
            List<Player> eligible = inHand.stream()
                    .filter(p -> pot.eligiblePlayerIds().contains(p.getId()))
                    .toList();

            EvaluatedHand best = eligible.stream()
                    .map(p -> handsByPlayerId.get(p.getId()))
                    .max(EvaluatedHand::compareTo)
                    .orElseThrow();

            List<Player> winners = eligible.stream()
                    .filter(p -> handsByPlayerId.get(p.getId()).compareTo(best) == 0)
                    .toList();

            // 정확히 나눠떨어지지 않는 잔여 칩은 좌석 순서상 앞쪽 승자부터 1개씩 배분한다.
            int share = pot.amount() / winners.size();
            int remainder = pot.amount() % winners.size();
            for (int i = 0; i < winners.size(); i++) {
                winners.get(i).addChips(share + (i < remainder ? 1 : 0));
            }

            potResults.add(new ShowdownResult.PotResult(pot, winners, best, share));
        }
        return new ShowdownResult(potResults);
    }

    public Room getRoom() {
        return room;
    }

    public BettingRound getCurrentBettingRound() {
        return currentBettingRound;
    }
}
