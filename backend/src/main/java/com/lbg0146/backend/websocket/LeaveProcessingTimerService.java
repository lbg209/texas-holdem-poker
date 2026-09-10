package com.lbg0146.backend.websocket;

import com.lbg0146.backend.game.GameEngine;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

// 상태가 바뀔 때마다(RoomBroadcaster.broadcastState) 호출되어, 핸드가 막 끝나서(SHOWDOWN) 나가기
// 예약된 사람이 있으면 3초 뒤 실제로 방에서 제거한다 — 즉시 제거하면 결과(쇼다운 공개/GAME OVER
// 오버레이) 연출을 볼 시간도 없이 바로 튕겨나가는 문제가 있었다. TurnTimerService/AutoStartService와
// 동일한 구조.
// Spring 빈이 아니다 — 방(RoomInstance)마다 하나씩 직접 생성해서 들고 있는다.
public class LeaveProcessingTimerService {

    public static final int LEAVE_PROCESSING_DELAY_SECONDS = 3;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicReference<ScheduledFuture<?>> pending = new AtomicReference<>();

    private boolean currentlyScheduled;

    public synchronized void onStateBroadcast(GameEngine engine, Runnable onProcessed) {
        boolean shouldSchedule = engine.hasPendingLeaveDuringShowdown();

        if (shouldSchedule == currentlyScheduled) {
            return;
        }
        currentlyScheduled = shouldSchedule;
        cancelPending();

        if (!shouldSchedule) {
            return;
        }

        ScheduledFuture<?> future = executor.schedule(() -> {
            try {
                engine.processPendingLeaves();
                onProcessed.run();
            } catch (RuntimeException e) {
                System.err.println("나가기 처리 중 오류: " + e.getMessage());
            }
        }, LEAVE_PROCESSING_DELAY_SECONDS, TimeUnit.SECONDS);
        pending.set(future);
    }

    private void cancelPending() {
        ScheduledFuture<?> future = pending.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
    }
}
