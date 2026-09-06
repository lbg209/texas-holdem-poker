package com.lbg0146.backend.room;

import com.lbg0146.backend.exception.GameStateException;
import com.lbg0146.backend.player.Player;
import org.junit.jupiter.api.Test;

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
}
