package com.lbg0146.backend.game;

import com.lbg0146.backend.card.Card;
import com.lbg0146.backend.exception.GameStateException;
import com.lbg0146.backend.player.Player;
import com.lbg0146.backend.player.PlayerAction;
import com.lbg0146.backend.room.Pot;
import com.lbg0146.backend.room.Room;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 7단계: GameEngine 동시성 처리 검증. 실행 순서에 좌우되는 모호한 경쟁("누가 먼저 성공하나")은
// 피하고, 결과가 스케줄링과 무관하게 항상 같아야 하는 시나리오만 검증한다.
class GameEngineConcurrencyTest {

    @Test
    void 동시_참가는_정원을_넘지_않는다() throws InterruptedException {
        Room room = new Room();
        GameEngine engine = new GameEngine(room);
        int attempts = 20;

        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger rejectedCount = new AtomicInteger();

        for (int i = 0; i < attempts; i++) {
            int idx = i;
            executor.submit(() -> {
                try {
                    start.await();
                    engine.addPlayer(new Player("p" + idx, "P" + idx, Room.STARTING_CHIPS));
                    successCount.incrementAndGet();
                } catch (GameStateException e) {
                    rejectedCount.incrementAndGet();
                } catch (InterruptedException ignored) {
                }
            });
        }
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals(Room.MAX_PLAYERS, successCount.get());
        assertEquals(attempts - Room.MAX_PLAYERS, rejectedCount.get());
        assertEquals(Room.MAX_PLAYERS, room.getPlayers().size());
    }

    // 존재하지 않는 playerId로 보내는 액션은 스케줄링 순서와 무관하게 항상 거부되어야 한다.
    // 이 "항상 거부되는" 결정성 덕분에, 정상 액션 하나가 다수의 동시 잘못된 액션 속에서도
    // 정확히 한 번만 반영되는지를 모호함 없이 검증할 수 있다.
    @Test
    void 동시에_많은_잘못된_액션이_들어와도_정상_액션은_정확히_한_번만_반영된다() throws InterruptedException {
        Room room = new Room();
        Player a = new Player("a", "A", Room.STARTING_CHIPS);
        Player b = new Player("b", "B", Room.STARTING_CHIPS);
        room.addPlayer(a);
        room.addPlayer(b);
        GameEngine engine = new GameEngine(room);
        engine.startHand(); // 헤즈업: a가 버튼/SB이자 첫 액션자

        int decoyCount = 30;
        ExecutorService executor = Executors.newFixedThreadPool(decoyCount + 1);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger decoyRejections = new AtomicInteger();
        AtomicBoolean legitSucceeded = new AtomicBoolean(false);

        for (int i = 0; i < decoyCount; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    engine.applyAction(UUID.randomUUID().toString(), PlayerAction.CALL, 0);
                } catch (IllegalArgumentException e) {
                    decoyRejections.incrementAndGet();
                } catch (InterruptedException ignored) {
                }
            });
        }
        executor.submit(() -> {
            try {
                start.await();
                engine.applyAction("a", PlayerAction.CALL, 0);
                legitSucceeded.set(true);
            } catch (InterruptedException ignored) {
            }
        });

        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals(decoyCount, decoyRejections.get());
        assertTrue(legitSucceeded.get());
        // a의 콜이 정확히 한 번만 반영되었는지 (중복 반영이면 BIG_BLIND를 초과하게 됨)
        assertEquals(Room.BIG_BLIND, a.getCurrentRoundBet());
        assertEquals("b", engine.getCurrentBettingRound().getCurrentActorId().orElseThrow());
    }

    @Test
    void 상태를_반복적으로_읽는_동안_핸드를_반복_시작해도_예외가_발생하지_않는다() throws InterruptedException {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", Room.STARTING_CHIPS));
        room.addPlayer(new Player("b", "B", Room.STARTING_CHIPS));
        GameEngine engine = new GameEngine(room);

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicReference<Exception> readerException = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            while (running.get()) {
                try {
                    engine.withLock(() -> {
                        List<Card> communityCopy = new ArrayList<>(engine.getRoom().getCommunityCards());
                        List<Pot> potsCopy = new ArrayList<>(engine.getRoom().getPots());
                        return communityCopy.size() + potsCopy.size();
                    });
                } catch (Exception e) {
                    readerException.set(e);
                    running.set(false);
                }
            }
        });
        reader.start();

        for (int i = 0; i < 500 && readerException.get() == null; i++) {
            engine.startHand();
        }

        running.set(false);
        reader.join(2000);

        assertNull(readerException.get());
    }
}
