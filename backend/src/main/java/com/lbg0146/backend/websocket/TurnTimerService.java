package com.lbg0146.backend.websocket;

import com.lbg0146.backend.game.BettingRound;
import com.lbg0146.backend.game.GameEngine;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

// 상태가 바뀔 때마다(RoomBroadcaster.broadcastState) 호출되어, 지금 액션 차례인 플레이어가
// TURN_LIMIT_SECONDS 안에 액션하지 않으면 자동으로 폴드시킨다. GameEngine은 스케줄러의 존재를
// 전혀 모르며(autoFoldIfStillWaitingOn만 알고 있음), 이 클래스도 GameEngine 외에는 아무것도
// 몰라서(RoomBroadcaster에 대한 의존성 없음) 순환 의존이 생기지 않는다 — 타임아웃 시 무엇을 할지는
// onTimeout 콜백으로 호출 측(RoomBroadcaster)이 넘겨준다.
// Spring 빈이 아니다 — 방(RoomInstance)마다 하나씩 직접 생성해서 들고 있는다.
public class TurnTimerService {

    public static final int TURN_LIMIT_SECONDS = 30;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicReference<ScheduledFuture<?>> pending = new AtomicReference<>();

    // 마지막으로 예약한 (라운드 인스턴스, 액션자 id) 조합 — 실제로 턴이 바뀌었을 때만 다시
    // 예약한다. 그렇지 않으면(예: 핸드 도중 새 플레이어 입장으로 broadcastState가 턴과 무관하게
    // 또 호출된 경우) 매번 시간이 60초로 리셋되는 부작용이 생긴다.
    private BettingRound lastScheduledRound;
    private String lastScheduledPlayerId;
    private volatile Long currentDeadlineMillis;

    public synchronized void onStateBroadcast(GameEngine engine, Runnable onTimeout) {
        BettingRound round = engine.withLock(engine::getCurrentBettingRound);
        String actorId = round == null ? null : round.getCurrentActorId().orElse(null);

        if (round == lastScheduledRound && Objects.equals(actorId, lastScheduledPlayerId)) {
            return;
        }

        cancelPending();
        lastScheduledRound = round;
        lastScheduledPlayerId = actorId;

        if (round == null || actorId == null) {
            currentDeadlineMillis = null;
            return;
        }

        currentDeadlineMillis = System.currentTimeMillis() + TURN_LIMIT_SECONDS * 1000L;
        String targetPlayerId = actorId;
        ScheduledFuture<?> future = executor.schedule(() -> {
            try {
                if (engine.autoFoldIfStillWaitingOn(round, targetPlayerId)) {
                    onTimeout.run();
                }
            } catch (RuntimeException e) {
                // 백그라운드 스케줄러 스레드라 예외가 조용히 삼켜지면 디버깅이 어려워지므로 로그만 남긴다.
                System.err.println("턴 타이머 자동 폴드 처리 중 오류: " + e.getMessage());
            }
        }, TURN_LIMIT_SECONDS, TimeUnit.SECONDS);
        pending.set(future);
    }

    private void cancelPending() {
        ScheduledFuture<?> future = pending.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
    }

    // null이면 지금 액션 시간 제한 중인 사람이 없다는 뜻(핸드 진행 중이 아님).
    public Long getCurrentDeadlineMillis() {
        return currentDeadlineMillis;
    }
}
