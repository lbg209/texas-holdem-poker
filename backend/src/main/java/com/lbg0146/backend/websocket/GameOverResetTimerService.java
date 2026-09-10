package com.lbg0146.backend.websocket;

import com.lbg0146.backend.game.GameEngine;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

// 상태가 바뀔 때마다(RoomBroadcaster.broadcastState) 호출되어, GAME OVER(생존자 1명) 상태가 되면
// 15초 뒤 전원 칩을 리필하고 레디를 초기화한다(내보내지는 않음) — 그 뒤 다시 전원 레디하면
// AutoStartService가 기존 로직 그대로 새 핸드를 시작시킨다. TurnTimerService/AutoStartService와
// 동일한 구조.
// Spring 빈이 아니다 — 방(RoomInstance)마다 하나씩 직접 생성해서 들고 있는다.
public class GameOverResetTimerService {

    public static final int GAME_OVER_RESET_DELAY_SECONDS = 15;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicReference<ScheduledFuture<?>> pending = new AtomicReference<>();

    // 지금 카운트다운이 예약되어 있는지 — 조건이 실제로 바뀌었을 때만 다시 스케줄링하기 위한 값.
    private boolean currentlyScheduled;
    private volatile Long currentDeadlineMillis;

    public synchronized void onStateBroadcast(GameEngine engine, Runnable onReset) {
        boolean shouldCountDown = engine.resolveWinnerId() != null;

        if (shouldCountDown == currentlyScheduled) {
            return;
        }
        currentlyScheduled = shouldCountDown;
        cancelPending();

        if (!shouldCountDown) {
            currentDeadlineMillis = null;
            return;
        }

        currentDeadlineMillis = System.currentTimeMillis() + GAME_OVER_RESET_DELAY_SECONDS * 1000L;
        ScheduledFuture<?> future = executor.schedule(() -> {
            try {
                if (engine.resetAfterGameOverIfStillOver()) {
                    onReset.run();
                }
            } catch (RuntimeException e) {
                System.err.println("GAME OVER 자동 초기화 처리 중 오류: " + e.getMessage());
            }
        }, GAME_OVER_RESET_DELAY_SECONDS, TimeUnit.SECONDS);
        pending.set(future);
    }

    private void cancelPending() {
        ScheduledFuture<?> future = pending.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
    }

    // null이면 지금 GAME OVER 초기화 카운트다운 중이 아니라는 뜻.
    public Long getCurrentDeadlineMillis() {
        return currentDeadlineMillis;
    }
}
