package com.lbg0146.backend.websocket;

import com.lbg0146.backend.game.GameEngine;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

// 상태가 바뀔 때마다(RoomBroadcaster.broadcastState) 호출되어, 헤즈업 쇼다운에서 공개/머크를
// 결정해야 하는 사람이 DECISION_TIME_LIMIT_SECONDS 안에 결정하지 않으면 자동으로(강제) 공개
// 처리한다. TurnTimerService/AutoStartService와 동일한 구조.
// Spring 빈이 아니다 — 방(RoomInstance)마다 하나씩 직접 생성해서 들고 있는다.
public class HeadsUpRevealTimerService {

    public static final int DECISION_TIME_LIMIT_SECONDS = 8;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicReference<ScheduledFuture<?>> pending = new AtomicReference<>();

    private String lastScheduledDeciderId;
    private volatile Long currentDeadlineMillis;

    public synchronized void onStateBroadcast(GameEngine engine, Runnable onTimeout) {
        String deciderId = engine.withLock(() -> engine.getRoom().getHeadsUpDeciderPlayerId());

        if (Objects.equals(deciderId, lastScheduledDeciderId)) {
            return;
        }
        lastScheduledDeciderId = deciderId;
        cancelPending();

        if (deciderId == null) {
            currentDeadlineMillis = null;
            return;
        }

        currentDeadlineMillis = System.currentTimeMillis() + DECISION_TIME_LIMIT_SECONDS * 1000L;
        String targetPlayerId = deciderId;
        ScheduledFuture<?> future = executor.schedule(() -> {
            try {
                if (engine.forceHeadsUpRevealIfStillPending(targetPlayerId)) {
                    onTimeout.run();
                }
            } catch (RuntimeException e) {
                System.err.println("헤즈업 공개/머크 시간 초과 처리 중 오류: " + e.getMessage());
            }
        }, DECISION_TIME_LIMIT_SECONDS, TimeUnit.SECONDS);
        pending.set(future);
    }

    private void cancelPending() {
        ScheduledFuture<?> future = pending.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
    }

    // null이면 지금 공개/머크 결정 대기 중이 아니라는 뜻.
    public Long getCurrentDeadlineMillis() {
        return currentDeadlineMillis;
    }
}
