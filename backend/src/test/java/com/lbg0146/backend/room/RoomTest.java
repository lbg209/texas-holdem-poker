package com.lbg0146.backend.room;

import com.lbg0146.backend.exception.GameStateException;
import com.lbg0146.backend.exception.InvalidActionException;
import com.lbg0146.backend.player.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoomTest {

    @Test
    void 정원을_초과해서_앉을_수_없다() {
        Room room = new Room();
        for (int i = 0; i < Room.MAX_PLAYERS; i++) {
            room.addPlayer(new Player("p" + i, "P" + i, Room.STARTING_CHIPS));
        }

        assertThrows(GameStateException.class,
                () -> room.addPlayer(new Player("extra", "extra", Room.STARTING_CHIPS)));
    }

    @Test
    void 딜러버튼은_좌석_순서대로_순환한다() {
        Room room = new Room();
        room.addPlayer(new Player("p0", "P0", Room.STARTING_CHIPS));
        room.addPlayer(new Player("p1", "P1", Room.STARTING_CHIPS));
        room.addPlayer(new Player("p2", "P2", Room.STARTING_CHIPS));

        room.moveButtonToNextSeat();
        assertEquals(0, room.getDealerButtonPosition());
        room.moveButtonToNextSeat();
        assertEquals(1, room.getDealerButtonPosition());
        room.moveButtonToNextSeat();
        assertEquals(2, room.getDealerButtonPosition());
        room.moveButtonToNextSeat();
        assertEquals(0, room.getDealerButtonPosition());
    }

    @Test
    void 존재하지_않는_플레이어를_조회하면_예외가_발생한다() {
        Room room = new Room();
        room.addPlayer(new Player("p0", "P0", Room.STARTING_CHIPS));

        assertThrows(IllegalArgumentException.class, () -> room.findPlayer("no-such-id"));
    }

    @Test
    void 같은_계정으로_두_번_입장할_수_없다() {
        Room room = new Room();
        room.addPlayer(new Player("p0", "P0", Room.STARTING_CHIPS, 42L));

        assertThrows(GameStateException.class,
                () -> room.addPlayer(new Player("p1", "P0(다른 탭)", Room.STARTING_CHIPS, 42L)));
    }

    @Test
    void 게스트는_계정_중복_체크_대상이_아니다() {
        Room room = new Room();
        room.addPlayer(new Player("p0", "게스트1", Room.STARTING_CHIPS));

        // 게스트(accountUserId == null)끼리는 얼마든지 같이 앉을 수 있다.
        assertDoesNotThrow(() -> room.addPlayer(new Player("p1", "게스트2", Room.STARTING_CHIPS)));
    }

    @Test
    void 나가는_사람이_버튼보다_앞_좌석이어도_버튼_위치가_유지된다() {
        Room room = new Room();
        Player a = new Player("a", "A", Room.STARTING_CHIPS);
        Player b = new Player("b", "B", Room.STARTING_CHIPS);
        Player c = new Player("c", "C", Room.STARTING_CHIPS);
        room.addPlayer(a);
        room.addPlayer(b);
        room.addPlayer(c);
        room.moveButtonToNextSeat(); // 버튼 = a(인덱스 0)
        room.moveButtonToNextSeat(); // 버튼 = b(인덱스 1)

        // a(인덱스 0)가 빠지면 남은 리스트에서 b는 인덱스 1 -> 0으로 당겨진다. 단순히 int 인덱스만
        // 유지했다면 버튼이 엉뚱하게 c를 가리켰을 것이다 — 여전히 b를 가리켜야 한다.
        a.setLeaving(true);
        room.removeLeavingPlayers();

        assertEquals(b, room.getPlayers().get(room.getDealerButtonPosition()));
    }

    @Test
    void 가장_먼저_입장한_사람이_방장이_된다() {
        Room room = new Room();
        Player a = new Player("a", "A", Room.STARTING_CHIPS);
        Player b = new Player("b", "B", Room.STARTING_CHIPS);

        room.addPlayer(a);
        room.addPlayer(b);

        assertEquals("a", room.getOwnerId());
    }

    @Test
    void 방장이_나가면_가장_오래_앉아있는_사람에게_승계된다() {
        Room room = new Room();
        Player a = new Player("a", "A", Room.STARTING_CHIPS);
        Player b = new Player("b", "B", Room.STARTING_CHIPS);
        Player c = new Player("c", "C", Room.STARTING_CHIPS);
        room.addPlayer(a);
        room.addPlayer(b);
        room.addPlayer(c);

        a.setLeaving(true);
        room.removeLeavingPlayers();

        assertEquals("b", room.getOwnerId());
    }

    @Test
    void 방장이_아닌_사람이_나가면_방장은_그대로_유지된다() {
        Room room = new Room();
        Player a = new Player("a", "A", Room.STARTING_CHIPS);
        Player b = new Player("b", "B", Room.STARTING_CHIPS);
        room.addPlayer(a);
        room.addPlayer(b);

        b.setLeaving(true);
        room.removeLeavingPlayers();

        assertEquals("a", room.getOwnerId());
    }

    @Test
    void 방이_완전히_비면_방장도_사라진다() {
        Room room = new Room();
        Player a = new Player("a", "A", Room.STARTING_CHIPS);
        room.addPlayer(a);

        a.setLeaving(true);
        room.removeLeavingPlayers();

        assertNull(room.getOwnerId());
    }

    @Test
    void 나가는_사람이_없으면_removeLeavingPlayers는_아무_영향이_없다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", Room.STARTING_CHIPS));
        room.addPlayer(new Player("b", "B", Room.STARTING_CHIPS));
        room.moveButtonToNextSeat();
        int before = room.getDealerButtonPosition();

        room.removeLeavingPlayers();

        assertEquals(2, room.getPlayers().size());
        assertEquals(before, room.getDealerButtonPosition());
    }

    @Test
    void 방이_비어있으면_설정을_바꿀_수_있고_스몰블라인드는_빅블라인드의_절반이_된다() {
        Room room = new Room();

        room.configure(50_000, 1000);

        assertEquals(50_000, room.getStartingChips());
        assertEquals(1000, room.getBigBlind());
        assertEquals(500, room.getSmallBlind());
        assertEquals(6, room.getMaxPlayers(), "좌석 수는 더 이상 설정할 수 없고 항상 6석 고정이다");
        assertEquals(1000, room.getAnte(),
                "첫 핸드가 시작되기 전(로비 상세 조회 시점 등)에도 레벨 1 앤티가 바로 반영되어야 한다");
    }

    @Test
    void 방에_참가자가_있으면_설정을_바꿀_수_없다() {
        Room room = new Room();
        room.addPlayer(new Player("p0", "P0", Room.STARTING_CHIPS));

        assertThrows(GameStateException.class, () -> room.configure(50_000, 1000));
    }

    @Test
    void 시작_칩이_0_이하면_거부된다() {
        Room room = new Room();

        assertThrows(InvalidActionException.class, () -> room.configure(0, 200));
    }

    @Test
    void 빅블라인드가_BET_UNIT_단위가_아니면_거부된다() {
        Room room = new Room();

        assertThrows(InvalidActionException.class, () -> room.configure(30_000, 150));
    }

    @Test
    void 좌석_번호가_범위를_벗어나면_거부된다() {
        Room room = new Room();

        assertThrows(InvalidActionException.class,
                () -> room.addPlayer(new Player("p0", "P0", Room.STARTING_CHIPS), -1));
        assertThrows(InvalidActionException.class,
                () -> room.addPlayer(new Player("p0", "P0", Room.STARTING_CHIPS), Room.MAX_PLAYERS));
    }

    @Test
    void 이미_차있는_좌석에는_앉을_수_없다() {
        Room room = new Room();
        room.addPlayer(new Player("p0", "P0", Room.STARTING_CHIPS), 2);

        assertThrows(GameStateException.class,
                () -> room.addPlayer(new Player("p1", "P1", Room.STARTING_CHIPS), 2));
    }

    @Test
    void 좌석을_옮기면_리스트_순서도_좌석_번호_순서로_바뀐다() {
        Room room = new Room();
        Player a = new Player("a", "A", Room.STARTING_CHIPS);
        Player b = new Player("b", "B", Room.STARTING_CHIPS);
        room.addPlayer(a, 0);
        room.addPlayer(b, 1);

        room.moveSeat("a", 5);

        assertEquals(List.of(b, a), room.getPlayers(), "a가 5번으로 옮기면 리스트 순서도 b, a로 바뀌어야 한다");
        assertEquals(5, a.getSeatIndex());
    }

    @Test
    void 자리_이동으로_리스트_순서가_바뀌어도_버튼을_쥔_사람은_계속_같은_사람을_가리킨다() {
        Room room = new Room();
        Player a = new Player("a", "A", Room.STARTING_CHIPS);
        Player b = new Player("b", "B", Room.STARTING_CHIPS);
        Player c = new Player("c", "C", Room.STARTING_CHIPS);
        room.addPlayer(a, 0);
        room.addPlayer(b, 1);
        room.addPlayer(c, 2);
        room.moveButtonToNextSeat(); // 버튼 = a(인덱스 0)
        room.moveButtonToNextSeat(); // 버튼 = b(인덱스 1)

        // a가 5번 좌석(맨 뒤)으로 옮기면 리스트 순서가 [b, c, a]로 바뀌어서 b의 인덱스가 1 -> 0으로
        // 당겨진다. 단순히 int 인덱스만 유지했다면 버튼이 엉뚱하게 c를 가리켰을 것이다.
        room.moveSeat("a", 5);

        assertEquals(b, room.getPlayers().get(room.getDealerButtonPosition()));
    }

    @Test
    void 핸드가_15판_미만이면_블라인드_레벨1_그대로_유지된다() {
        Room room = new Room();
        room.applyBlindLevel(); // GameEngine.startHand()가 매 핸드 시작 시 호출하는 것과 동일

        assertEquals(200, room.getBigBlind());
        assertEquals(100, room.getSmallBlind());
        assertEquals(200, room.getAnte(), "실제 대회(JOPT 등) 관례대로 앤티는 레벨 1부터 그 레벨 빅블라인드와 같은 금액으로 걷힌다");

        for (int i = 0; i < 14; i++) {
            room.recordHandCompletedForBlindLevel();
        }
        room.applyBlindLevel();

        assertEquals(200, room.getBigBlind(), "14판까지는 아직 레벨 1(시작값)이어야 한다");
    }

    @Test
    void 핸드가_15판_지나면_블라인드가_1_5배로_오른다() {
        Room room = new Room();
        for (int i = 0; i < 15; i++) {
            room.recordHandCompletedForBlindLevel();
        }

        room.applyBlindLevel();

        assertEquals(300, room.getBigBlind());
        assertEquals(150, room.getSmallBlind());
        assertEquals(300, room.getAnte(), "레벨이 올라도 앤티는 계속 그 레벨 빅블라인드를 따라간다");
    }

    @Test
    void 마지막_레벨에서도_앤티는_그_레벨_빅블라인드와_같은_금액이다() {
        Room room = new Room();
        for (int i = 0; i < 75; i++) { // 100/200 -> 150/300 -> 200/400 -> 300/600 -> 400/800 -> 500/1000(마지막)
            room.recordHandCompletedForBlindLevel();
        }

        room.applyBlindLevel();

        assertEquals(1000, room.getBigBlind());
        assertEquals(1000, room.getAnte(), "앤티는 그 시점 빅블라인드와 같은 금액(빅블라인드 앤티 방식)이어야 한다");
    }

    @Test
    void 마지막_레벨_이후로는_더_오르지_않고_유지된다() {
        Room room = new Room();
        for (int i = 0; i < 500; i++) {
            room.recordHandCompletedForBlindLevel();
        }

        room.applyBlindLevel();

        assertEquals(1000, room.getBigBlind(), "고정 6단계 스케줄이 끝나면 더 안 올라야 한다");
        assertEquals(1000, room.getAnte());
    }

    @Test
    void GAME_OVER_리매치로_리셋되면_블라인드도_레벨1로_돌아간다() {
        Room room = new Room();
        room.addPlayer(new Player("a", "A", Room.STARTING_CHIPS));
        room.addPlayer(new Player("b", "B", Room.STARTING_CHIPS));
        for (int i = 0; i < 75; i++) {
            room.recordHandCompletedForBlindLevel();
        }
        room.applyBlindLevel();
        assertEquals(1000, room.getBigBlind());

        room.resetForRematch();

        assertEquals(200, room.getBigBlind());
        assertEquals(100, room.getSmallBlind());
        assertEquals(200, room.getAnte(), "레벨 1도 앤티가 있으므로 리셋 후에도 0이 아니라 시작 빅블라인드와 같아야 한다");
    }

    @Test
    void 방_설정에서_정한_빅블라인드를_기준으로_배율이_적용된다() {
        Room room = new Room();
        room.configure(30_000, 300);

        for (int i = 0; i < 15; i++) {
            room.recordHandCompletedForBlindLevel();
        }
        room.applyBlindLevel();

        // 300 * 1.5 = 450은 BET_UNIT(100)의 배수가 아니라 가장 가까운 100 단위로 반올림된다 —
        // 정확히 반반(450)인 경우 Math.round는 올림하므로 500이 된다.
        assertEquals(500, room.getBigBlind());
    }

    @Test
    void 블라인드_구조표는_현재_판_수와_무관하게_고정_6단계를_그대로_보여준다() {
        Room room = new Room();

        List<BlindLevelInfo> structure = room.getBlindStructure();

        assertEquals(6, structure.size());
        // 앤티는 레벨 1부터 계속 걷히고, 항상 그 레벨 빅블라인드와 같은 금액이다(빅블라인드 앤티 방식).
        assertEquals(new BlindLevelInfo(1, 100, 200, 200), structure.get(0));
        assertEquals(new BlindLevelInfo(2, 150, 300, 300), structure.get(1));
        assertEquals(new BlindLevelInfo(3, 200, 400, 400), structure.get(2));
        assertEquals(new BlindLevelInfo(4, 300, 600, 600), structure.get(3));
        assertEquals(new BlindLevelInfo(5, 400, 800, 800), structure.get(4));
        assertEquals(new BlindLevelInfo(6, 500, 1000, 1000), structure.get(5));
    }

    @Test
    void 현재_블라인드_레벨_번호는_1부터_시작하고_판_수에_따라_오른다() {
        Room room = new Room();
        assertEquals(1, room.getCurrentBlindLevel());

        for (int i = 0; i < 15; i++) {
            room.recordHandCompletedForBlindLevel();
        }
        assertEquals(2, room.getCurrentBlindLevel());

        for (int i = 0; i < 500; i++) {
            room.recordHandCompletedForBlindLevel();
        }
        assertEquals(6, room.getCurrentBlindLevel(), "마지막 단계를 넘어도 6에서 멈춰야 한다");
    }

    @Test
    void 핸드_히스토리는_최근_30개까지만_유지되고_최신이_맨_앞이다() {
        Room room = new Room();
        for (int i = 1; i <= 35; i++) {
            room.addHandHistoryEntry(new HandHistoryEntry(i, List.of(), List.of(), List.of(), true,
                    "w", "W", false, Set.of()));
        }

        List<HandHistoryEntry> history = room.getHandHistory();

        assertEquals(30, history.size());
        assertEquals(35, history.get(0).handNumber(), "최신 핸드가 맨 앞에 와야 한다");
        assertEquals(6, history.get(history.size() - 1).handNumber(), "가장 오래된 5개(1~5)는 버려져야 한다");
    }
}
