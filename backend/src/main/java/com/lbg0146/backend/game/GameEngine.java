package com.lbg0146.backend.game;

import com.lbg0146.backend.card.Card;
import com.lbg0146.backend.exception.GameStateException;
import com.lbg0146.backend.hand.EvaluatedHand;
import com.lbg0146.backend.hand.HandEvaluator;
import com.lbg0146.backend.player.Player;
import com.lbg0146.backend.player.PlayerAction;
import com.lbg0146.backend.player.PlayerStatus;
import com.lbg0146.backend.room.HandHistoryEntry;
import com.lbg0146.backend.room.Phase;
import com.lbg0146.backend.room.Pot;
import com.lbg0146.backend.room.Room;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

// Room의 상태를 받아 한 핸드의 진행(블라인드→딜→베팅→쇼다운)을 조율한다.
// Room/BettingRound 내부 컬렉션이 스레드 세이프하지 않으므로, 이 인스턴스에 대한 모든 읽기/쓰기는
// synchronized 메서드 또는 withLock()을 거쳐야 한다 (7단계: 방 단위 동시성/직렬화).
public class GameEngine {

    // 방장이 레디 안 한 플레이어를 강퇴할 수 있으려면, 레디 안 한 상태로 이 시간 이상 지나야 한다.
    // 남은 시간은 UI에 노출하지 않는다 — 방장이 너무 일찍 시도하면 그냥 실패로 처리한다.
    private static final long KICK_DELAY_MILLIS = 3_000;
    // 좌석을 옮긴 뒤 다시 옮기려면 이 시간 이상 지나야 한다 — 한 사람이 빠르게 연타로 자리를
    // 옮기는 것만 막는 가벼운 스팸 방지용이다(서로 다른 두 사람이 같은 빈자리를 동시에 누르는
    // 진짜 동시성 문제는 이 값과 무관하게, Room.moveSeat가 synchronized 안에서 점유 여부를
    // 다시 검증하는 것으로 막힌다).
    private static final long SEAT_MOVE_THROTTLE_MILLIS = 1_000;
    // "나가기"를 눌렀을 때 결과 화면을 보호해줘야 하는 최소 시간 — 프론트 useShowBoard.ts의
    // RESULT_HOLD_MS(5_500)와 반드시 같은 값을 유지해야 한다. 핸드가 끝난 지 이만큼 지났으면
    // 프론트는 이미 "게임 준비 중" 대기 화면으로 돌아간 뒤라 더 이상 보호할 결과가 없으므로,
    // requestLeave()가 3초 유예(LeaveProcessingTimerService) 없이 즉시 내보내도 된다.
    private static final long RESULT_VIEW_GRACE_MILLIS = 5_500;

    private final Room room;
    private BettingRound currentBettingRound;
    // 헤즈업 쇼다운에서 미리 계산해둔(아직 칩 지급 전) 결과. 공개/머크 결정이 끝나면(또는 시간
    // 초과로 강제 처리되면) 이 값으로 finalizeHeadsUpShowdown이 실제 칩 지급을 수행한다.
    private ShowdownResult pendingHeadsUpShowdownResult;
    // 이번 베팅 라운드(스트리트)에서 마지막으로 벳/레이즈(또는 베팅액을 올린 올인)한 사람의 id.
    // 새 베팅 라운드가 시작될 때마다 null로 리셋된다 — "쇼다운 시점의 값"은 곧 "마지막으로 실제
    // 베팅이 있었던 라운드의 마지막 공격자"가 된다(그 이후 라운드가 전부 체크로 넘어갔거나 베팅
    // 없이 올인 런아웃됐어도, 값 자체는 그 마지막 공격 시점 그대로 유지되므로).
    private String lastAggressorId;

    public GameEngine(Room room) {
        this.room = room;
    }

    public synchronized void addPlayer(Player player) {
        room.addPlayer(player);
    }

    // 고정 좌석제 — 사용자가 클릭한 좌석에 명시적으로 앉힌다(Room.addPlayer(player, seatIndex) 참고).
    public synchronized void addPlayer(Player player, int seatIndex) {
        room.addPlayer(player, seatIndex);
    }

    // 방 설정(시작 칩/빅블라인드)을 바꾼다. 방이 비어있을 때만 허용된다(Room.configure 참고).
    public synchronized void configureRoom(int startingChips, int bigBlind) {
        room.configure(startingChips, bigBlind);
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
        room.resetAnteCollected();
        room.resetDeck();
        room.getDeck().shuffle();
        room.moveButtonToNextSeat();
        // 판 수 기준 블라인드 상승 반영 — 지금까지 끝난 핸드 수(handsSinceBlindReset)로 이번
        // 핸드에 적용할 블라인드/앤티를 다시 계산한다.
        room.applyBlindLevel();

        // 헤즈업(실제로 이번 핸드에 참여하는, 즉 파산하지 않은 인원이 2명)은 버튼 자리가 곧
        // 스몰블라인드이고, 프리플랍에서 버튼이 먼저 액션한다. 좌석에 앉은 총원이 아니라 파산하지
        // 않은 인원 기준으로 판단해야, 파산자가 섞여 있어도 실제 대결 인원 기준으로 정확히 헤즈업
        // 규칙이 적용된다.
        boolean headsUp = playersWithChips == 2;
        postBlinds(headsUp);
        dealHoleCards();

        room.setPhase(Phase.PREFLOP);
        int preflopStartOffset = headsUp ? 0 : 3; // 버튼(0)->SB(1)->BB(2)->UTG(3)
        startNewBettingRound(preflopStartOffset, room.getBigBlind(), room.getBigBlind());
    }

    private void postBlinds(boolean headsUp) {
        Player smallBlindPlayer = playerAtOffset(headsUp ? 0 : 1);
        Player bigBlindPlayer = playerAtOffset(headsUp ? 1 : 2);
        smallBlindPlayer.commitChips(room.getSmallBlind());
        bigBlindPlayer.commitChips(room.getBigBlind());
        // 레벨 1부터 앤티도 계속 걷는다(실제 대회 관례) — "빅블라인드 앤티" 방식이라 빅블라인드
        // 자리에 앉은 사람이 자기 빅블라인드에 더해 앤티도 혼자 낸다(버튼이 아니다 — 이름과 달리
        // 버튼이 낸다고 착각해서 처음에 잘못 구현했던 걸 바로잡음). 실제로 낸 금액은 특정 플레이어
        // 소유가 아니라 팟 전체의 "죽은 돈"이라 Room에 따로 누적해뒀다가 PotCalculator가 메인팟에
        // 더한다(Player.payAnte 참고 — totalHandContribution에는 안 들어감).
        if (room.getAnte() > 0) {
            room.addAnteCollected(bigBlindPlayer.payAnte(room.getAnte()));
        }
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
        // 새 스트리트가 시작되면 "이번 라운드의 마지막 공격자"를 새로 추적한다 — 지난 스트리트의
        // 레이즈는 쇼다운 공개 순서 판단에서 더 이상 유효하지 않다(실제 룰: 마지막 베팅 라운드 기준).
        lastAggressorId = null;
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
        room.findPlayer(expectedPlayerId).markAutoFolded();
        return true;
    }

    public synchronized void applyAction(String playerId, PlayerAction action, int amount) {
        Player actor = room.findPlayer(playerId);
        int betBefore = currentBettingRound.getCurrentBet();
        currentBettingRound.applyAction(actor, action, amount);
        // BET/RAISE/ALL_IN 중 실제로 맞춰야 할 베팅액을 끌어올린 경우만 "공격"으로 취급한다 —
        // 콜하는 수준의(또는 콜도 못 미치는) 올인은 공격이 아니다.
        if (currentBettingRound.getCurrentBet() > betBefore
                && (action == PlayerAction.BET || action == PlayerAction.RAISE || action == PlayerAction.ALL_IN)) {
            lastAggressorId = playerId;
        }

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
        room.setPots(PotCalculator.calculate(room.getPlayers(), room.getAnteCollectedThisHand()));
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
        // 나가기 예약된 사람은 여기서 바로 빼지 않는다 — LeaveProcessingTimerService가 결과를 볼 시간
        // (3초)을 준 뒤에 처리한다. 미리 나가기를 눌러둔 채로 쇼다운 공개 중이었으면 결과 화면도
        // 못 보고 바로 튕겨나가던 버그가 있었다.
        onHandConcluded();
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
            startNewBettingRound(1, 0, room.getBigBlind());
        }
    }

    private void dealCommunity(int count) {
        for (int i = 0; i < count; i++) {
            room.getCommunityCards().add(room.getDeck().draw());
        }
    }

    // 쇼다운에 도달했을 때 호출된다. 정확히 2명이 겨루면(헤즈업) 승자를 미리 계산만 해두고,
    // 실제 포커 규칙대로 정한 한 명은 자동 공개, 나머지 한 명에게는 공개/머크 결정을 맡긴다 —
    // 그 결정이 끝나야 실제로 팟이 지급되고 핸드가 종료 처리된다("누가 이겼는지" 자체를 그때까지
    // 숨겨서 긴장감을 준다). 3명 이상이 남았으면(순서대로 머크를 묻는 실제 규칙까지는 이번엔
    // 구현하지 않고) 기존 그대로 즉시 전원 공개하고 확정한다.
    private void beginShowdown() {
        List<Player> inHand = room.getPlayers().stream()
                .filter(p -> p.getStatus() != PlayerStatus.FOLDED && p.getStatus() != PlayerStatus.BUSTED)
                .toList();

        if (inHand.size() == 2) {
            ShowdownResult result = computeShowdownResult(inHand);
            Player a = inHand.get(0);
            Player b = inHand.get(1);
            Player autoShown = determineFirstToShow(a, b);
            Player decider = autoShown == a ? b : a;

            room.setHeadsUpShowdown(true);
            room.getVoluntarilyRevealedIds().add(autoShown.getId());
            room.setHeadsUpDeciderPlayerId(decider.getId());
            pendingHeadsUpShowdownResult = result;
            return;
        }

        room.setLastShowdownResult(resolveShowdown());
        room.setPhase(Phase.SHOWDOWN);
        onHandConcluded();
    }

    // 실제 포커 쇼다운 공개 순서 규칙: 마지막 베팅 라운드에서 마지막으로 벳/레이즈한 사람이 먼저
    // 공개한다. 그 라운드에서 아무도 베팅하지 않았으면(전원 체크로 넘어갔으면) 포지션상 먼저
    // 액션하는 사람 — 헤즈업 포스트플랍 첫 액션자(버튼이 아닌 쪽, distanceFromButton이 더 작은
    // 쪽) — 이 먼저 공개한다. 프리플랍만 베팅이 있고 이후 전부 체크였거나 올인 런아웃으로
    // 이후 베팅 라운드 자체가 없었던 경우에도, lastAggressorId는 "마지막으로 실제 베팅이 있었던
    // 라운드"의 값을 그대로 유지하고 있으므로 올바르게 그 사람을 가리킨다.
    private Player determineFirstToShow(Player a, Player b) {
        if (lastAggressorId != null) {
            return lastAggressorId.equals(a.getId()) ? a : b;
        }
        return distanceFromButton(a) < distanceFromButton(b) ? a : b;
    }

    // 홀카드+커뮤니티 카드로 족보를 평가하고 팟별 승자를 정하기만 한다 — 칩 지급은 하지 않는다
    // (awardPots가 별도로 담당). resolveShowdown()(3명 이상/기존 테스트 경로)과 헤즈업 지연 흐름이
    // 이 계산 로직을 공유한다.
    private ShowdownResult computeShowdownResult(List<Player> inHand) {
        List<Pot> pots = PotCalculator.calculate(room.getPlayers(), room.getAnteCollectedThisHand());

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

    // 헤즈업 쇼다운에서 결정자가 공개/머크를 직접 선택했을 때 호출된다. 머크는 실제 포커 규칙대로
    // "죽은 패" 취급된다 — 실제로 이기고 있었어도 공개하지 않으면 그 몫은 전부 상대방에게 간다.
    public synchronized void decideHeadsUpReveal(String playerId, boolean reveal) {
        if (pendingHeadsUpShowdownResult == null || !playerId.equals(room.getHeadsUpDeciderPlayerId())) {
            throw new GameStateException("지금은 결정할 수 있는 상황이 아닙니다.");
        }
        if (reveal) {
            room.getVoluntarilyRevealedIds().add(playerId);
        } else {
            Player forfeiting = room.findPlayer(playerId);
            Player opponent = pendingHeadsUpShowdownResult.handsByPlayerId().keySet().stream()
                    .filter(id -> !id.equals(playerId))
                    .findFirst()
                    .map(room::findPlayer)
                    .orElseThrow();
            pendingHeadsUpShowdownResult = applyMuckForfeiture(pendingHeadsUpShowdownResult, forfeiting, opponent);
        }
        finalizeHeadsUpShowdown();
    }

    // 머크한 사람이 이기고 있던(또는 비기고 있던) 팟의 몫을 전부 상대방에게 넘긴다 — "테이블에
    // 보여주지 않은 패는 죽은 패"라는 실제 포커 규칙. eligible이 1명뿐인 팟(상대가 콜 못 해서
    // 아무도 못 다투고 자기 초과 베팅을 그냥 돌려받는 경우)은 실제 승부가 아니므로 건드리지
    // 않는다 — 머크한 사람 본인 것만 있고 넘겨줄 상대도 없다.
    private ShowdownResult applyMuckForfeiture(ShowdownResult result, Player forfeiting, Player opponent) {
        List<ShowdownResult.PotResult> adjusted = result.potResults().stream()
                .map(potResult -> {
                    boolean contested = potResult.pot().eligiblePlayerIds().size() > 1;
                    boolean forfeitingWasWinner = potResult.winners().contains(forfeiting);
                    if (!contested || !forfeitingWasWinner) {
                        return potResult;
                    }
                    return new ShowdownResult.PotResult(
                            potResult.pot(), List.of(opponent), potResult.winningHand(), potResult.pot().amount());
                })
                .toList();
        return new ShowdownResult(adjusted, result.handsByPlayerId());
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
        onHandConcluded();
    }

    // 핸드가 실제로 끝나는 시점(finishHandByFold/beginShowdown/finalizeHeadsUpShowdown)에 호출된다.
    private void onHandConcluded() {
        room.addHandHistoryEntry(HandHistoryEntry.capture(room.nextHandNumber(), room));
        room.recordHandCompletedForBlindLevel();
        room.markHandConcluded();
        resetKickClockForIdlePlayers();
        checkGameOver();
    }

    // 강퇴 유예(3초) 시계를 "핸드 결과가 막 공개된 시점"부터 다시 재도록 리셋한다. 레디는 핸드가
    // 진행되는 동안에도(의미는 없지만) 계속 꺼져 있을 수 있어서, 이걸 안 하면 핸드가 진행되는 내내
    // 레디 안 한 상태였던 사람은 핸드가 막 끝나자마자(반응할 틈도 없이) 곧바로 강퇴 가능해지는
    // 문제가 있었다 — 방장이 결과 화면을 보여주자마자 강퇴 버튼을 누를 수 있는 건 의도가 아니다.
    private void resetKickClockForIdlePlayers() {
        room.getPlayers().stream().filter(p -> !p.isReady()).forEach(p -> p.setReady(false));
    }

    // 나가기 예약된 사람은 아직 방에 남아있는 채로(제거는 LeaveProcessingTimerService가 3초 뒤에
    // 처리) 판단하되, 칩을 가진 사람이 정확히 1명이면 GAME OVER를 방에 "고정"시킨다. MIN_PLAYERS
    // 미만이면(예: 파산이 아니라 그냥 나가기로 빠져서 혼자 남은 경우) 오판을 막기 위해 판단하지 않는다.
    private void checkGameOver() {
        if (room.getPlayers().size() < Room.MIN_PLAYERS) {
            return;
        }
        List<Player> withChips = room.getPlayers().stream()
                .filter(p -> p.getChips() > 0)
                .toList();
        if (withChips.size() == 1) {
            Player winner = withChips.get(0);
            room.setGameOverWinnerId(winner.getId());
            room.setGameOverWinnerNickname(winner.getNickname());
        }
    }

    // GAME OVER(생존자 1명) 여부를 나타내는 고정값을 반환한다. checkGameOver()가 핸드 종료 시점에
    // 이 값을 세팅하고, resetForRematch()가(15초 카운트다운 만료 시) 다시 비운다 — "지금 이 순간
    // 인원이 몇 명인지"로 매번 다시 계산하지 않는다. 예전엔 매번 다시 계산했는데, GAME OVER 이후
    // 누군가 나가서 인원이 MIN_PLAYERS 밑으로 줄어드는 순간 판정이 null로 흔들려 카운트다운이
    // 취소돼버리는 버그가 있었다. RoomStateMapper(API 응답 노출)와
    // isWaitingForNextHandWithEveryoneReady(자동 시작 판단)가 공유하는 단일 소스다.
    public synchronized String resolveWinnerId() {
        return room.getGameOverWinnerId();
    }

    // resolveWinnerId()와 같은 시점에 고정된 닉네임. 승자가 카운트다운 도중 "나가기"로 방을 빠져도
    // players 목록에서 다시 찾을 필요 없이 그대로 정확한 닉네임을 보여줄 수 있다.
    public synchronized String resolveWinnerNickname() {
        return room.getGameOverWinnerNickname();
    }

    // GAME OVER 15초 타이머가 만료됐을 때 호출된다. 예약 시점에도 여전히 GAME OVER 상태일 때만
    // 전원 칩을 리필하고 레디를 초기화한다(리매치 대기 상태로 전환) — 다른 스케줄러들과 같은
    // "확인 후 실행" 원자적 패턴.
    public synchronized boolean resetAfterGameOverIfStillOver() {
        if (resolveWinnerId() == null) {
            return false;
        }
        room.resetForRematch();
        return true;
    }

    public synchronized void setReady(String playerId, boolean ready) {
        room.findPlayer(playerId).setReady(ready);
    }

    // "나가기"를 예약(leaving=true)하거나 취소(leaving=false)한다.
    // - 완전한 유휴 상태(phase==null, 아직 첫 핸드 전이거나 GAME OVER 리셋 직후 — 보여줄 결과가
    //   없음)면 즉시 방에서 제거한다.
    // - 핸드가 진행 중이면 이번 핸드가 끝날 때까지(phase가 SHOWDOWN이 될 때까지) 아무것도 안 하고
    //   기다린다.
    // - 핸드가 막 끝나서(phase==SHOWDOWN) 아직 RESULT_VIEW_GRACE_MILLIS가 안 지났으면, 결과를 볼
    //   시간을 주기 위해 즉시 제거하지 않고 LeaveProcessingTimerService가 3초 뒤에 처리하게
    //   맡긴다 — 미리 나가기를 눌러둔 채로 쇼다운 공개 중이었으면 결과 화면도 못 보고 바로
    //   튕겨나가던 버그가 있었다.
    // - 반대로 phase==SHOWDOWN이어도 RESULT_VIEW_GRACE_MILLIS가 이미 지났으면(STOP으로 자동
    //   진행이 멈춰서 프론트가 "게임 준비 중" 대기 화면으로 돌아간 뒤) 더는 보호할 결과가 없으므로
    //   3초를 더 기다리지 않고 바로 내보낸다 — 실사용 중 "대기 화면인데도 나가기가 늦게 처리된다"는
    //   버그로 발견됨.
    // 나가기를 누르면 레디도 자동으로 꺼서(STOP), 나가려는 사람이 남아있는 동안 실수로 다음 핸드가
    // 자동 시작되는 걸 막는다.
    public synchronized void requestLeave(String playerId, boolean leaving) {
        Player player = room.findPlayer(playerId);
        player.setLeaving(leaving);
        if (leaving) {
            player.setReady(false);
        }
        if (leaving && canLeaveImmediately()) {
            room.removeLeavingPlayers();
        }
    }

    private boolean canLeaveImmediately() {
        if (room.getPhase() == null) {
            return true;
        }
        if (room.getPhase() != Phase.SHOWDOWN) {
            return false;
        }
        return System.currentTimeMillis() - room.getHandConcludedAtMillis() >= RESULT_VIEW_GRACE_MILLIS;
    }

    // 핸드가 막 끝났거나(또는 다음 핸드를 기다리는 중이거나) 상관없이 phase가 SHOWDOWN이고, 나가기
    // 예약된 사람이 아직 방에 남아있는지. LeaveProcessingTimerService가 이 조건이 바뀔 때만
    // 다시 스케줄링하는 데 쓴다.
    public synchronized boolean hasPendingLeaveDuringShowdown() {
        return room.getPhase() == Phase.SHOWDOWN && room.getPlayers().stream().anyMatch(Player::isLeaving);
    }

    // LeaveProcessingTimerService의 3초 타이머가 만료됐을 때 호출된다. removeLeavingPlayers() 자체가
    // 나가기 예약된 사람이 없으면 아무 일도 안 하므로(취소됐거나 이미 처리됐거나) 별도의 재검증
    // 없이 그냥 호출한다.
    public synchronized void processPendingLeaves() {
        room.removeLeavingPlayers();
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

    // 방장이 다른 플레이어를 강퇴한다. 조건: 요청자가 방장이어야 하고, 핸드가 진행 중이 아니어야
    // 하고(resolveWinnerId/isWaitingForNextHandWithEveryoneReady와 같은 "핸드 진행 중" 기준 —
    // phase가 있고 SHOWDOWN이 아니면 진행 중), 대상이 레디 안 한 상태로 KICK_DELAY_MILLIS 이상
    // 지나야 한다. 실제 제거는 기존 "나가기" 메커니즘(leaving 플래그 + removeLeavingPlayers)을
    // 그대로 재사용한다 — 버튼 위치 보정 등 안전장치를 중복 구현하지 않기 위함.
    public synchronized void kickPlayer(String requesterId, String targetId) {
        if (requesterId.equals(targetId)) {
            throw new GameStateException("자기 자신은 강퇴할 수 없습니다.");
        }
        if (!requesterId.equals(room.getOwnerId())) {
            throw new GameStateException("방장만 강퇴할 수 있습니다.");
        }
        if (isHandInProgress()) {
            throw new GameStateException("핸드가 진행 중일 때는 강퇴할 수 없습니다.");
        }
        Player target = room.findPlayer(targetId);
        if (!target.isKickEligible(KICK_DELAY_MILLIS)) {
            throw new GameStateException("아직 강퇴할 수 없습니다.");
        }
        target.setLeaving(true);
        room.removeLeavingPlayers();
    }

    // 이미 앉아있는 플레이어가 다른 빈 좌석으로 옮긴다. 핸드 진행 중(phase가 있고 SHOWDOWN도
    // 아님)이면 거부되고, 너무 빠르게 연속으로 옮기려 해도 거부된다(SEAT_MOVE_THROTTLE_MILLIS).
    // 그 좌석이 실제로 비어있는지는 Room.moveSeat가 검증한다.
    public synchronized void requestSeatMove(String playerId, int newSeatIndex) {
        if (isHandInProgress()) {
            throw new GameStateException("핸드가 진행 중일 때는 자리를 옮길 수 없습니다.");
        }
        Player player = room.findPlayer(playerId);
        if (!player.isSeatMoveEligible(SEAT_MOVE_THROTTLE_MILLIS)) {
            throw new GameStateException("너무 빠르게 자리를 옮길 수 없습니다.");
        }
        room.moveSeat(playerId, newSeatIndex);
    }

    // "핸드가 진행 중"인지 — phase가 있고(핸드가 시작됐고) SHOWDOWN도 아닌 상태. 강퇴/좌석 이동
    // 둘 다 이 기준으로 "지금은 안 된다"를 판단한다(resolveWinnerId/isWaitingForNextHandWithEveryoneReady
    // 와 같은 기준).
    private boolean isHandInProgress() {
        return room.getPhase() != null && room.getPhase() != Phase.SHOWDOWN;
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
