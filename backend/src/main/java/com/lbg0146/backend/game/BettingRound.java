package com.lbg0146.backend.game;

import com.lbg0146.backend.exception.InvalidActionException;
import com.lbg0146.backend.player.Player;
import com.lbg0146.backend.player.PlayerAction;
import com.lbg0146.backend.player.PlayerStatus;
import com.lbg0146.backend.room.Room;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// 한 스트리트(프리플랍/플랍/턴/리버)의 베팅 진행 상태를 관리한다.
public class BettingRound {

    // 좌석 순환 계산 기준이 되는 테이블 전체 좌석 순서(폴드/올인 여부와 무관하게 고정)
    private final List<Player> tableSeats;
    // 이번 라운드에서 아직 액션 가능한(폴드/올인 아닌) 플레이어들
    private final List<Player> contestants;

    // 이번 라운드에서 맞춰야 하는 최고 베팅액
    private int currentBet;
    // 다음 정상 레이즈에 필요한 최소 증가폭 (베팅 전에는 최소 베팅액과 같은 의미로 쓰인다)
    private int minimumRaise;

    // canRaise=false인 항목은 short all-in으로 재소환된 경우로, CALL/FOLD만 허용되고 RAISE는 거부된다.
    private record PendingActor(Player player, boolean canRaise) {
    }

    private final Deque<PendingActor> pendingActors = new ArrayDeque<>();

    public BettingRound(List<Player> tableSeats, List<Player> actingOrder, int currentBet, int minimumRaise) {
        this.tableSeats = tableSeats;
        this.contestants = new ArrayList<>(actingOrder);
        this.currentBet = currentBet;
        this.minimumRaise = minimumRaise;
        for (Player player : actingOrder) {
            pendingActors.addLast(new PendingActor(player, true));
        }
    }

    public void applyAction(Player actor, PlayerAction action, int amount) {
        PendingActor pending = pendingActors.peekFirst();
        if (pending == null || pending.player() != actor) {
            throw new InvalidActionException(actor.getId() + "의 차례가 아닙니다.");
        }

        // 액션이 실제로 유효한지 먼저 검증한 뒤에만 큐에서 제거한다.
        // (검증보다 먼저 제거해버리면, 유효하지 않은 액션이 거부된 후에도 그 플레이어가
        //  큐에서 사라져 다음 정상 액션(CALL/FOLD 등)의 차례까지 어긋나게 된다.)
        switch (action) {
            case CHECK -> {
                if (actor.getCurrentRoundBet() != currentBet) {
                    throw new InvalidActionException("콜해야 할 금액이 남아 있어 체크할 수 없습니다.");
                }
            }
            case BET -> {
                if (currentBet != 0) {
                    throw new InvalidActionException("이미 베팅이 있어 BET을 할 수 없습니다. RAISE를 사용하세요.");
                }
                validateRaise(actor, pending.canRaise(), amount);
            }
            case RAISE -> {
                if (currentBet == 0) {
                    throw new InvalidActionException("아직 베팅이 없어 RAISE를 할 수 없습니다. BET을 사용하세요.");
                }
                validateRaise(actor, pending.canRaise(), amount);
            }
            case FOLD, CALL, ALL_IN -> {
                // 별도 사전 검증 없음
            }
        }

        pendingActors.pollFirst();

        switch (action) {
            case FOLD -> {
                actor.fold();
                contestants.remove(actor);
            }
            case CHECK -> {
                // 상태 변화 없음
            }
            case CALL -> {
                actor.commitChips(currentBet - actor.getCurrentRoundBet());
                if (actor.getStatus() == PlayerStatus.ALL_IN) {
                    contestants.remove(actor);
                }
            }
            case BET, RAISE -> applyRaise(actor, amount);
            case ALL_IN -> applyAllIn(actor);
        }
    }

    private void validateRaise(Player actor, boolean canRaise, int newTotal) {
        if (!canRaise) {
            throw new InvalidActionException("short all-in 이후에는 콜/폴드만 가능하며 다시 레이즈할 수 없습니다.");
        }
        int actorMax = actor.getCurrentRoundBet() + actor.getChips();
        if (newTotal > actorMax) {
            throw new InvalidActionException("보유 칩을 초과하는 금액입니다. 최대 " + actorMax + "까지 가능하며, 전액을 걸려면 ALL_IN을 사용하세요.");
        }
        // 보유 칩 전부를 거는 경우(사실상 올인)는 100단위가 아니어도 허용한다.
        if (newTotal != actorMax && newTotal % Room.BET_UNIT != 0) {
            throw new InvalidActionException(Room.BET_UNIT + " 단위로만 베팅/레이즈할 수 있습니다.");
        }
        if (newTotal < currentBet + minimumRaise) {
            throw new InvalidActionException("최소 레이즈 총액(" + (currentBet + minimumRaise) + ") 미만입니다.");
        }
    }

    private void applyRaise(Player actor, int newTotal) {
        int increment = newTotal - currentBet;
        actor.commitChips(newTotal - actor.getCurrentRoundBet());
        currentBet = newTotal;
        minimumRaise = increment;
        if (actor.getStatus() == PlayerStatus.ALL_IN) {
            contestants.remove(actor);
        }
        reopenFully(actor);
    }

    private void applyAllIn(Player actor) {
        int allInTotal = actor.getCurrentRoundBet() + actor.getChips();
        actor.commitChips(actor.getChips());
        contestants.remove(actor);

        if (allInTotal <= currentBet) {
            // 콜도 안 되는 올인: 상태 변화 없음, 재오픈도 없음
            return;
        }

        int increment = allInTotal - currentBet;
        currentBet = allInTotal;
        if (increment >= minimumRaise) {
            minimumRaise = increment;
            reopenFully(actor);
        } else {
            // short all-in(요구사항 6번 예외): currentBet만 올리고 minimumRaise는 갱신하지 않으며,
            // 이미 액션을 마친 플레이어에게는 RAISE 없이 CALL/FOLD 기회만 다시 준다.
            reopenCallFoldOnly(actor);
        }
    }

    private void reopenFully(Player actor) {
        pendingActors.clear();
        for (Player player : orderedFrom(actor, contestants)) {
            pendingActors.addLast(new PendingActor(player, true));
        }
    }

    private void reopenCallFoldOnly(Player actor) {
        Set<Player> stillPending = pendingActors.stream()
                .map(PendingActor::player)
                .collect(Collectors.toSet());
        List<Player> alreadyActed = contestants.stream()
                .filter(player -> player != actor && !stillPending.contains(player))
                .toList();
        for (Player player : orderedFrom(actor, alreadyActed)) {
            pendingActors.addLast(new PendingActor(player, false));
        }
    }

    // actor 다음 좌석부터 시계방향으로 테이블을 순회하며, candidates에 포함된 플레이어만 순서대로 반환한다.
    private List<Player> orderedFrom(Player actor, List<Player> candidates) {
        int startIndex = tableSeats.indexOf(actor);
        int seatCount = tableSeats.size();
        List<Player> ordered = new ArrayList<>();
        for (int i = 1; i < seatCount; i++) {
            Player seatPlayer = tableSeats.get((startIndex + i) % seatCount);
            if (candidates.contains(seatPlayer)) {
                ordered.add(seatPlayer);
            }
        }
        return ordered;
    }

    public boolean isComplete() {
        return pendingActors.isEmpty();
    }

    // 액션 가능한(칩이 남은) 플레이어가 1명 이하면 더 이상 베팅이 일어날 수 없다는 뜻이므로,
    // 남은 스트리트는 새 베팅 라운드 없이 커뮤니티 카드만 순서대로 오픈해야 한다(올인 런아웃).
    public boolean noFurtherBettingPossible() {
        return contestants.size() <= 1;
    }

    // 지금 액션할 차례인 플레이어의 id (조회 전용, 상태를 바꾸지 않음)
    public Optional<String> getCurrentActorId() {
        PendingActor pending = pendingActors.peekFirst();
        return pending == null ? Optional.empty() : Optional.of(pending.player().getId());
    }

    public int getCurrentBet() {
        return currentBet;
    }

    public int getMinimumRaise() {
        return minimumRaise;
    }
}
