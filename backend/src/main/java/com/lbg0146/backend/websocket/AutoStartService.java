package com.lbg0146.backend.websocket;

import com.lbg0146.backend.game.GameEngine;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

// 상태가 바뀔 때마다(RoomBroadcaster.broadcastState) 호출되어, 파산하지 않은 플레이어 전원이
// 레디 상태이면 일정 시간 뒤 자동으로 다음 핸드를 시작한다. 아직 한 번도 핸드를 시작한 적
// 없으면(방금 다 같이 입장해서 레디한 경우) 짧게, 핸드가 끝난 뒤 다음 핸드를 기다리는 경우에는
// 방금 끝난 결과(칩 이동/족보 등)를 확인할 시간을 주기 위해 더 길게 기다린다.
// TurnTimerService와 동일한 구조 — GameEngine은 스케줄러의 존재를 모르고, 이 클래스도 GameEngine
// 외에는 아무것도 몰라서 순환 의존이 생기지 않는다.
@Component
public class AutoStartService {

    public static final int FIRST_HAND_DELAY_SECONDS = 1;
    public static final int BETWEEN_HANDS_DELAY_SECONDS = 5;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicReference<ScheduledFuture<?>> pending = new AtomicReference<>();

    // 지금 카운트다운이 예약되어 있는지 — 조건이 실제로 바뀌었을 때만 다시 스케줄링하기 위한 값.
    private boolean currentlyScheduled;
    private volatile Long currentDeadlineMillis;

    public synchronized void onStateBroadcast(GameEngine engine, Runnable onAutoStart) {
        boolean shouldCountDown = engine.isWaitingForNextHandWithEveryoneReady();

        if (shouldCountDown == currentlyScheduled) {
            return;
        }
        currentlyScheduled = shouldCountDown;
        cancelPending();

        if (!shouldCountDown) {
            currentDeadlineMillis = null;
            return;
        }

        int delaySeconds = engine.isBeforeFirstHand() ? FIRST_HAND_DELAY_SECONDS : BETWEEN_HANDS_DELAY_SECONDS;
        currentDeadlineMillis = System.currentTimeMillis() + delaySeconds * 1000L;
        ScheduledFuture<?> future = executor.schedule(() -> {
            try {
                if (engine.autoStartIfStillReady()) {
                    onAutoStart.run();
                }
            } catch (RuntimeException e) {
                System.err.println("자동 핸드 시작 처리 중 오류: " + e.getMessage());
            }
        }, delaySeconds, TimeUnit.SECONDS);
        pending.set(future);
    }

    private void cancelPending() {
        ScheduledFuture<?> future = pending.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
    }

    // null이면 지금 자동 시작 카운트다운 중이 아니라는 뜻.
    public Long getCurrentDeadlineMillis() {
        return currentDeadlineMillis;
    }
}
