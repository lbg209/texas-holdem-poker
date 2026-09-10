package com.lbg0146.backend.room;

import com.lbg0146.backend.exception.GameStateException;
import com.lbg0146.backend.exception.InvalidActionException;
import com.lbg0146.backend.player.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

        room.configure(50_000, 1000, 4);

        assertEquals(50_000, room.getStartingChips());
        assertEquals(1000, room.getBigBlind());
        assertEquals(500, room.getSmallBlind());
        assertEquals(4, room.getMaxPlayers());
    }

    @Test
    void 방에_참가자가_있으면_설정을_바꿀_수_없다() {
        Room room = new Room();
        room.addPlayer(new Player("p0", "P0", Room.STARTING_CHIPS));

        assertThrows(GameStateException.class, () -> room.configure(50_000, 1000, 4));
    }

    @Test
    void 시작_칩이_0_이하면_거부된다() {
        Room room = new Room();

        assertThrows(InvalidActionException.class, () -> room.configure(0, 200, 6));
    }

    @Test
    void 빅블라인드가_BET_UNIT_단위가_아니면_거부된다() {
        Room room = new Room();

        assertThrows(InvalidActionException.class, () -> room.configure(30_000, 150, 6));
    }

    @Test
    void 최대_인원이_범위를_벗어나면_거부된다() {
        Room room = new Room();

        assertThrows(InvalidActionException.class, () -> room.configure(30_000, 200, 1));
        assertThrows(InvalidActionException.class, () -> room.configure(30_000, 200, 7));
    }

    @Test
    void 설정을_바꾸면_정원_체크도_새_최대_인원_기준으로_동작한다() {
        Room room = new Room();
        room.configure(30_000, 200, 2);
        room.addPlayer(new Player("p0", "P0", Room.STARTING_CHIPS));
        room.addPlayer(new Player("p1", "P1", Room.STARTING_CHIPS));

        assertThrows(GameStateException.class,
                () -> room.addPlayer(new Player("p2", "P2", Room.STARTING_CHIPS)));
    }
}
