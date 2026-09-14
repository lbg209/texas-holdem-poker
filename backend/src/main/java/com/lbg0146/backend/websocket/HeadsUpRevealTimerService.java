package com.lbg0146.backend.websocket;

import com.lbg0146.backend.game.GameEngine;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

// 상태가 바뀔 때마다(RoomBroadcaster.broadcastState) 호출되어, 헤즈업 쇼다운에서 공개/머크를
// 결정해야 하는 사람이 DECISION_TIME_LIMIT_SECONDS 안에 결정하지 않으면 자동으로(강제) 공개
// 처리한다. TurnTimerService/AutoStartService와 다른 점: 이 카운트다운은 결정권자(Room.
// headsUpDeciderPlayerId)가 정해진 시점이 아니라, 그 클라이언트가 "상대 카드 공개 연출을 다
// 봤다"고 신호(Room.headsUpRevealReadyAtMillis)를 보낸 시점부터 시작한다 — 안 그러면 프리플랍
// 올인런아웃처럼 서버가 순간적으로 플랍/턴/리버를 다 처리해버린 경우, 프론트가 그 연출을 재생하는
// 몇 초 동안 타이머가 이미 다 돌아가서 정작 결정 화면이 뜰 땐 시간이 얼마 안 남아있는 문제가 있었다.
// Spring 빈이 아니다 — 방(RoomInstance)마다 하나씩 직접 생성해서 들고 있는다.
public class HeadsUpRevealTimerService {

    public static final int DECISION_TIME_LIMIT_SECONDS = 5;
    // 결정권자는 정해졌는데(headsUpDeciderPlayerId) 그 클라이언트의 "다 봤다" 신호가 끝내 안 오는
    // 경우(연결 끊김 등)를 대비한 안전장치. 이 시간이 지나도 신호가 없으면 신호가 방금 온 것으로
    // 간주하고 강제로 실제 카운트다운을 시작시킨다 — 신호 유실 때문에 게임이 영영 멈추는 걸 막는다.
    private static final long READY_SIGNAL_FALLBACK_MILLIS = 20_000;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicReference<ScheduledFuture<?>> pendingDecision = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> pendingFallback = new AtomicReference<>();

    // 안전장치를 "결정권자 한 명당 한 번만" 예약하기 위한 추적값(어떤 결정권자에 대해 이미
    // fallback을 걸어뒀는지). 실제 카운트다운은 lastScheduledReadyAtMillis로 별도 추적한다.
    private String lastSeenDeciderId;
    private long lastScheduledReadyAtMillis;
    private volatile Long currentDeadlineMillis;

    private record Snapshot(String deciderId, long readyAtMillis) {
    }

    public synchronized void onStateBroadcast(GameEngine engine, Runnable onTimeout) {
        Snapshot snap = engine.withLock(() -> new Snapshot(
                engine.getRoom().getHeadsUpDeciderPlayerId(), engine.getRoom().getHeadsUpRevealReadyAtMillis()));

        if (snap.deciderId() == null) {
            if (lastSeenDeciderId != null) {
                // 결정이 끝났거나(다음 핸드로 넘어가는 등) 더 이상 대기 중이 아님 — 전부 리셋.
                lastSeenDeciderId = null;
                lastScheduledReadyAtMillis = 0;
                currentDeadlineMillis = null;
                cancelPendingDecision();
                cancelPendingFallback();
            }
            return;
        }

        if (!snap.deciderId().equals(lastSeenDeciderId)) {
            // 새로운 결정 대기가 막 시작됨 — 신호가 끝내 안 올 경우를 대비한 안전장치를 예약한다.
            lastSeenDeciderId = snap.deciderId();
            lastScheduledReadyAtMillis = 0;
            currentDeadlineMillis = null;
            cancelPendingDecision();
            cancelPendingFallback();
            String targetDeciderId = snap.deciderId();
            ScheduledFuture<?> fallback = executor.schedule(() -> {
                engine.markHeadsUpRevealReady(targetDeciderId);
                onTimeout.run();
            }, READY_SIGNAL_FALLBACK_MILLIS, TimeUnit.MILLISECONDS);
            pendingFallback.set(fallback);
        }

        if (snap.readyAtMillis() == 0 || snap.readyAtMillis() == lastScheduledReadyAtMillis) {
            // 아직 "다 봤다" 신호가 안 왔거나, 이미 이 신호 기준으로 카운트다운을 걸어둔 상태.
            return;
        }
        lastScheduledReadyAtMillis = snap.readyAtMillis();
        cancelPendingFallback(); // 신호가 왔으니 안전장치는 더 이상 필요 없다.
        cancelPendingDecision();

        currentDeadlineMillis = snap.readyAtMillis() + DECISION_TIME_LIMIT_SECONDS * 1000L;
        long delayMillis = Math.max(0, currentDeadlineMillis - System.currentTimeMillis());
        String targetPlayerId = snap.deciderId();
        ScheduledFuture<?> future = executor.schedule(() -> {
            try {
                if (engine.forceHeadsUpRevealIfStillPending(targetPlayerId)) {
                    onTimeout.run();
                }
            } catch (RuntimeException e) {
                System.err.println("헤즈업 공개/머크 시간 초과 처리 중 오류: " + e.getMessage());
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
        pendingDecision.set(future);
    }

    private void cancelPendingDecision() {
        ScheduledFuture<?> future = pendingDecision.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void cancelPendingFallback() {
        ScheduledFuture<?> future = pendingFallback.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
    }

    // null이면 지금 공개/머크 결정 대기 중이 아니거나, 대기 중이지만 아직 "다 봤다" 신호가 안 와서
    // 실제 카운트다운이 시작되지 않은 상태라는 뜻(프론트는 이 값이 null이 아닐 때만 카운트다운을
    // 그린다 — 신호를 보내기 전까지는 화면에 시간이 표시되지 않는 게 자연스럽다).
    public Long getCurrentDeadlineMillis() {
        return currentDeadlineMillis;
    }
}
