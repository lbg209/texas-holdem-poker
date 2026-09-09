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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

// Room의 상태를 받아 한 핸드의 진행(블라인드→딜→베팅→쇼다운)을 조율한다.
// Room/BettingRound 내부 컬렉션이 스레드 세이프하지 않으므로, 이 인스턴스에 대한 모든 읽기/쓰기는
// synchronized 메서드 또는 withLock()을 거쳐야 한다 (7단계: 방 단위 동시성/직렬화).
public class GameEngine {

    private final Room room;
    private BettingRound currentBettingRound;
    // 헤즈업 쇼다운에서 미리 계산해둔(아직 칩 지급 전) 결과. 공개/머크 결정이 끝나면(또는 시간
    // 초과로 강제 처리되면) 이 값으로 finalizeHeadsUpShowdown이 실제 칩 지급을 수행한다.
    private ShowdownResult pendingHeadsUpShowdownResult;

    public GameEngine(Room room) {
        this.room = room;
    }

    public synchronized void addPlayer(Player player) {
        room.addPlayer(player);
    }

    // Room/BettingRound 상태를 읽기만 하는 외부 코드(REST 상태 조회, WS 브로드캐스트 등)가
    // 진행 중인 쓰기와 겹치지 않도록 같은 락을 태워서 실행한다.
    public synchronized <T> T withLock(Supplier<T> action) {
        return action.get();
    }

    public synchronized void startHand() {
        if (room.getPlayers().size() < Room.MIN_PLAYERS) {
            throw new GameStateException("최소 " + Room.MIN_PLAYERS + "명이 필요합니다.");
        }
        long playersWithChips = room.getPlayers().stream().filter(p -> p.getChips() > 0).count();
        if (playersWithChips < Room.MIN_PLAYERS) {
            throw new GameStateException("칩이 있는 플레이어가 " + Room.MIN_PLAYERS + "명 미만이라 시작할 수 없습니다.");
        }

        room.getPlayers().forEach(Player::resetForNewHand);
        room.getCommunityCards().clear();
        room.setPots(new ArrayList<>());
        room.setWonByFold(false);
        room.setFoldWinWinnerId(null);
        room.getVoluntarilyRevealedIds().clear();
        room.setHeadsUpShowdown(false);
        room.setHeadsUpDeciderPlayerId(null);
        pendingHeadsUpShowdownResult = null;
        room.setLastShowdownResult(null);
        room.resetDeck();
        room.getDeck().shuffle();
        room.moveButtonToNextSeat();

        // 헤즈업(실제로 이번 핸드에 참여하는, 즉 파산하지 않은 인원이 2명)은 버튼 자리가 곧
        // 스몰블라인드이고, 프리플랍에서 버튼이 먼저 액션한다. 좌석에 앉은 총원이 아니라 파산하지
        // 않은 인원 기준으로 판단해야, 파산자가 섞여 있어도 실제 대결 인원 기준으로 정확히 헤즈업
        // 규칙이 적용된다.
        boolean headsUp = playersWithChips == 2;
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
                // 파산한 플레이어는 이번 핸드에 참여하지 않으므로 홀카드를 받지 않는다.
                if (player.getStatus() != PlayerStatus.BUSTED) {
                    player.receiveHoleCard(room.getDeck().draw());
                }
            }
        }
    }

    // 버튼 좌석 기준 offset번째 "파산하지 않은" 좌석을 반환한다. offset=0은 버튼 자신(항상 파산하지
    // 않은 좌석 — moveButtonToNextSeat가 보장), offset=1은 그다음 파산하지 않은 좌석, ... 순서로
    // 파산한 좌석은 건너뛰고 센다. 그래야 파산자가 섞여 있어도 스몰/빅블라인드가 실제 참여 인원
    // 기준으로 정확히 배정된다.
    private Player playerAtOffset(int offset) {
        List<Player> players = room.getPlayers();
        int seatCount = players.size();
        int index = room.getDealerButtonPosition();
        int count = 0;
        while (true) {
            if (players.get(index).getStatus() != PlayerStatus.BUSTED) {
                if (count == offset) {
                    return players.get(index);
                }
                count++;
            }
            index = (index + 1) % seatCount;
        }
    }

    // 버튼 왼쪽(다음 좌석)부터 시계방향으로 몇 번째 자리인지. 버튼 본인이 가장 마지막(맨 뒤) 순위가 된다.
    private int distanceFromButton(Player player) {
        List<Player> seats = room.getPlayers();
        int seatIndex = seats.indexOf(player);
        int button = room.getDealerButtonPosition();
        return (seatIndex - button - 1 + seats.size()) % seats.size();
    }

    private void startNewBettingRound(int startOffset, int currentBet, int minimumRaise) {
        List<Player> activePlayers = activePlayersFrom(startOffset);
        currentBettingRound = new BettingRound(room.getPlayers(), activePlayers, currentBet, minimumRaise);
    }

    // 버튼 좌석 기준 startOffset번째 자리부터 시계방향으로 순회하며 아직 ACTIVE(폴드/올인/파산
    // 아님)인 플레이어만 순서대로 반환한다. startOffset은 playerAtOffset과 동일하게 "버튼으로부터
    // 몇 자리 뒤인지"를 뜻하므로, 실제 좌석 인덱스로 변환하려면 버튼 위치를 더해야 한다 — 이걸
    // 빠뜨리면 버튼이 0번 좌석이 아닌 두 번째 핸드부터 첫 액션자가 잘못 계산된다.
    private List<Player> activePlayersFrom(int startOffset) {
        List<Player> players = room.getPlayers();
        int seatCount = players.size();
        int base = room.getDealerButtonPosition() + startOffset;
        List<Player> ordered = new ArrayList<>();
        for (int i = 0; i < seatCount; i++) {
            Player player = players.get((base + i) % seatCount);
            if (player.getStatus() == PlayerStatus.ACTIVE) {
                ordered.add(player);
            }
        }
        return ordered;
    }

    // 턴 타이머가 만료됐을 때 호출된다. 타이머를 예약한 시점의 BettingRound 인스턴스/액션자와
    // 지금 실제로 진행 중인 것이 정확히 같을 때만 폴드를 적용한다 — 그 사이 이미 액션했거나
    // (다음 액션자로 넘어갔거나) 핸드/스트리트가 이미 끝났으면(새 BettingRound 인스턴스로
    // 교체되었거나 null이 됐으면) 아무 일도 하지 않는다. 이 판단과 폴드 적용을 같은 synchronized
    // 메서드 안에서 원자적으로 처리해, 실제 액션과 타이머 만료가 동시에 들어와도 경쟁 상태가 없다.
    public synchronized boolean autoFoldIfStillWaitingOn(BettingRound expectedRound, String expectedPlayerId) {
        if (currentBettingRound != expectedRound) {
            return false;
        }
        boolean stillWaiting = currentBettingRound.getCurrentActorId()
                .map(expectedPlayerId::equals)
                .orElse(false);
        if (!stillWaiting) {
            return false;
        }
        applyAction(expectedPlayerId, PlayerAction.FOLD, 0);
        return true;
    }

    public synchronized void applyAction(String playerId, PlayerAction action, int amount) {
        Player actor = room.findPlayer(playerId);
        currentBettingRound.applyAction(actor, action, amount);

        long remaining = room.getPlayers().stream()
                .filter(p -> p.getStatus() != PlayerStatus.FOLDED && p.getStatus() != PlayerStatus.BUSTED)
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
                .filter(p -> p.getStatus() != PlayerStatus.FOLDED && p.getStatus() != PlayerStatus.BUSTED)
                .findFirst()
                .orElseThrow();
        int totalPot = room.getPots().stream().mapToInt(Pot::amount).sum();
        winner.addChips(totalPot);
        room.setPhase(Phase.SHOWDOWN);
        room.setWonByFold(true);
        room.setFoldWinWinnerId(winner.getId());
        // 베팅 라운드가 도중에 조기 종료된 것이라 큐에 다음 액션자가 남아있을 수 있다.
        // 그대로 두면 조회 응답에 이미 끝난 핸드의 currentActorId/currentBet이 남아,
        // 그 사람 화면에 "아직 내 차례"인 것처럼 액션 버튼이 계속 보이게 된다.
        currentBettingRound = null;
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
        room.getPlayers().forEach(Player::resetForNewRound);

        if (next == Phase.SHOWDOWN) {
            // phase를 여기서 바로 SHOWDOWN으로 바꾸지 않는다 — 헤즈업이면 공개/머크 결정이 끝나야
            // (beginShowdown/finalizeHeadsUpShowdown 참고) 비로소 SHOWDOWN으로 확정된다. 그 전까지는
            // GAME OVER 판정(resolveWinnerId)이나 레디 자동시작(isWaitingForNextHandWithEveryoneReady)
            // 둘 다 "핸드가 아직 안 끝남"으로 보게 하기 위한 의도적인 지연이다.
            beginShowdown();
            return;
        }

        room.setPhase(next);
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

    // 쇼다운에 도달했을 때 호출된다. 정확히 2명이 겨루면(헤즈업) 승자를 미리 계산만 해두고,
    // 무작위로 한 명은 자동 공개, 나머지 한 명에게는 공개/머크 결정을 맡긴다 — 그 결정이 끝나야
    // 실제로 팟이 지급되고 핸드가 종료 처리된다("누가 이겼는지" 자체를 그때까지 숨겨서 긴장감을
    // 준다). 3명 이상이 남았으면(순서대로 머크를 묻는 실제 규칙까지는 이번엔 구현하지 않고) 기존
    // 그대로 즉시 전원 공개하고 확정한다.
    private void beginShowdown() {
        List<Player> inHand = room.getPlayers().stream()
                .filter(p -> p.getStatus() != PlayerStatus.FOLDED && p.getStatus() != PlayerStatus.BUSTED)
                .toList();

        if (inHand.size() == 2) {
            ShowdownResult result = computeShowdownResult(inHand);
            Player a = inHand.get(0);
            Player b = inHand.get(1);
            boolean aShownFirst = ThreadLocalRandom.current().nextBoolean();
            Player autoShown = aShownFirst ? a : b;
            Player decider = aShownFirst ? b : a;

            room.setHeadsUpShowdown(true);
            room.getVoluntarilyRevealedIds().add(autoShown.getId());
            room.setHeadsUpDeciderPlayerId(decider.getId());
            pendingHeadsUpShowdownResult = result;
            return;
        }

        room.setLastShowdownResult(resolveShowdown());
        room.setPhase(Phase.SHOWDOWN);
    }

    // 홀카드+커뮤니티 카드로 족보를 평가하고 팟별 승자를 정하기만 한다 — 칩 지급은 하지 않는다
    // (awardPots가 별도로 담당). resolveShowdown()(3명 이상/기존 테스트 경로)과 헤즈업 지연 흐름이
    // 이 계산 로직을 공유한다.
    private ShowdownResult computeShowdownResult(List<Player> inHand) {
        List<Pot> pots = PotCalculator.calculate(room.getPlayers());

        Map<String, EvaluatedHand> handsByPlayerId = new HashMap<>();
        for (Player player : inHand) {
            List<Card> allCards = new ArrayList<>(player.getHoleCards());
            allCards.addAll(room.getCommunityCards());
            handsByPlayerId.put(player.getId(), HandEvaluator.evaluate(allCards));
        }

        List<ShowdownResult.PotResult> potResults = new ArrayList<>();
        for (Pot pot : pots) {
            List<Player> eligible = inHand.stream()
                    .filter(p -> pot.eligiblePlayerIds().contains(p.getId()))
                    .toList();

            EvaluatedHand best = eligible.stream()
                    .map(p -> handsByPlayerId.get(p.getId()))
                    .max(EvaluatedHand::compareTo)
                    .orElseThrow();

            // 정확히 나눠떨어지지 않는 잔여 칩(odd chip)은 버튼 왼쪽에서 가장 가까운 승자부터
            // 시계방향으로 1개씩 배분한다(실제 포커의 "odd chip rule") — 순서는 여기서 미리
            // 정해두고, 실제 지급(awardPots)에서 이 순서 그대로 나눠준다.
            List<Player> winners = eligible.stream()
                    .filter(p -> handsByPlayerId.get(p.getId()).compareTo(best) == 0)
                    .sorted(Comparator.comparingInt(this::distanceFromButton))
                    .toList();

            int share = pot.amount() / winners.size();
            potResults.add(new ShowdownResult.PotResult(pot, winners, best, share));
        }
        return new ShowdownResult(potResults, handsByPlayerId);
    }

    // computeShowdownResult가 정한 승자들에게 실제로 칩을 지급하고 room.pots를 확정한다.
    private void awardPots(ShowdownResult result) {
        room.setPots(result.potResults().stream().map(ShowdownResult.PotResult::pot).toList());
        for (ShowdownResult.PotResult potResult : result.potResults()) {
            List<Player> winners = potResult.winners();
            int remainder = potResult.pot().amount() % winners.size();
            for (int i = 0; i < winners.size(); i++) {
                winners.get(i).addChips(potResult.amountPerWinner() + (i < remainder ? 1 : 0));
            }
        }
    }

    // 계산과 지급을 한 번에 하는 기존 공개 API — 3명 이상 쇼다운(beginShowdown)과 기존 테스트가
    // 그대로 쓴다. 헤즈업 지연 흐름만 compute/award를 분리해서 그 사이에 결정을 기다린다.
    public ShowdownResult resolveShowdown() {
        List<Player> inHand = room.getPlayers().stream()
                .filter(p -> p.getStatus() != PlayerStatus.FOLDED && p.getStatus() != PlayerStatus.BUSTED)
                .toList();
        ShowdownResult result = computeShowdownResult(inHand);
        awardPots(result);
        return result;
    }

    // 헤즈업 쇼다운에서 결정자가 공개/머크를 직접 선택했을 때 호출된다.
    public synchronized void decideHeadsUpReveal(String playerId, boolean reveal) {
        if (pendingHeadsUpShowdownResult == null || !playerId.equals(room.getHeadsUpDeciderPlayerId())) {
            throw new GameStateException("지금은 결정할 수 있는 상황이 아닙니다.");
        }
        if (reveal) {
            room.getVoluntarilyRevealedIds().add(playerId);
        }
        finalizeHeadsUpShowdown();
    }

    // 공개/머크 결정 타이머가 만료됐을 때 호출된다. 예약 시점의 결정자와 지금도 같고 아직
    // 미결정 상태일 때만 강제로 공개 처리한다(그 사이 이미 결정했으면 아무 일도 하지 않는다).
    public synchronized boolean forceHeadsUpRevealIfStillPending(String expectedDeciderId) {
        if (pendingHeadsUpShowdownResult == null || !expectedDeciderId.equals(room.getHeadsUpDeciderPlayerId())) {
            return false;
        }
        room.getVoluntarilyRevealedIds().add(expectedDeciderId);
        finalizeHeadsUpShowdown();
        return true;
    }

    private void finalizeHeadsUpShowdown() {
        awardPots(pendingHeadsUpShowdownResult);
        room.setLastShowdownResult(pendingHeadsUpShowdownResult);
        room.setPhase(Phase.SHOWDOWN);
        room.setHeadsUpDeciderPlayerId(null);
        pendingHeadsUpShowdownResult = null;
    }

    // 생존(칩 보유) 플레이어가 정확히 1명일 때 그 id를, 아니면 null을 반환한다. 핸드가 완전히
    // 종료된 시점(진행 중인 베팅 라운드 없이 칩 정산까지 끝남)에만 판단한다 — 핸드 도중 올인으로
    // 일시적으로 chips=0인 플레이어가 있어도 섣불리 게임 종료로 오판하지 않기 위함. 좌석에
    // MIN_PLAYERS도 안 찼으면(아직 대결이 시작조차 안 함) 판단하지 않는다. RoomStateMapper(API
    // 응답 노출)와 isWaitingForNextHandWithEveryoneReady(자동 시작 판단)가 공유하는 단일 소스다.
    public synchronized String resolveWinnerId() {
        if (room.getPlayers().size() < Room.MIN_PLAYERS) {
            return null;
        }
        boolean handConcluded = room.getPhase() == null || room.getPhase() == Phase.SHOWDOWN;
        if (!handConcluded) {
            return null;
        }
        List<Player> withChips = room.getPlayers().stream()
                .filter(p -> p.getChips() > 0)
                .toList();
        return withChips.size() == 1 ? withChips.get(0).getId() : null;
    }

    public synchronized void setReady(String playerId, boolean ready) {
        room.findPlayer(playerId).setReady(ready);
    }

    // 핸드가 진행 중이지 않은(아직 첫 핸드를 시작 전이거나, 직전 핸드가 끝나 다음 핸드를
    // 기다리는) 상태에서 파산하지 않은 플레이어 전원이 레디했는지. resolveWinnerId와 동일한
    // "핸드가 진행 중이지 않다"는 기준(phase가 null이거나 SHOWDOWN)을 써야, 방에 아무도 핸드를
    // 시작한 적 없는 첫 순간에도(phase==null) 전원 레디 시 자동 시작이 걸린다.
    // 매치가 이미 끝났으면(resolveWinnerId != null) 자동 시작 대상이 아니다.
    public synchronized boolean isWaitingForNextHandWithEveryoneReady() {
        boolean noHandInProgress = room.getPhase() == null || room.getPhase() == Phase.SHOWDOWN;
        if (!noHandInProgress) {
            return false;
        }
        if (resolveWinnerId() != null) {
            return false;
        }
        List<Player> contenders = room.getPlayers().stream()
                .filter(p -> p.getStatus() != PlayerStatus.BUSTED)
                .toList();
        return contenders.size() >= Room.MIN_PLAYERS && contenders.stream().allMatch(Player::isReady);
    }

    // 아직 한 번도 핸드를 시작한 적 없는 상태인지(phase==null). AutoStartService가 첫 핸드는
    // 짧게, 이후 핸드 사이 대기는 더 길게 서로 다른 지연 시간을 적용하기 위해 구분해서 쓴다.
    public synchronized boolean isBeforeFirstHand() {
        return room.getPhase() == null;
    }

    // 자동 시작 타이머가 만료됐을 때 호출된다. 예약했던 조건이 지금도 여전히 유효한지 다시 확인한
    // 뒤에만 새 핸드를 시작한다 — 그 사이 누군가 레디를 껐거나 이미 다음 핸드가 시작됐으면 무시한다.
    public synchronized boolean autoStartIfStillReady() {
        if (!isWaitingForNextHandWithEveryoneReady()) {
            return false;
        }
        startHand();
        return true;
    }

    // 폴드로 종료된 핸드의 승자가 자원해서 자기 카드를 공개한다. 승자 본인만, 그 핸드가 폴드로
    // 끝난 경우에만 호출할 수 있다.
    public synchronized void revealFoldWinHand(String playerId) {
        if (!room.isWonByFold() || !playerId.equals(room.getFoldWinWinnerId())) {
            throw new GameStateException("지금은 카드를 공개할 수 있는 상황이 아닙니다.");
        }
        room.getVoluntarilyRevealedIds().add(playerId);
    }

    public Room getRoom() {
        return room;
    }

    public BettingRound getCurrentBettingRound() {
        return currentBettingRound;
    }
}
