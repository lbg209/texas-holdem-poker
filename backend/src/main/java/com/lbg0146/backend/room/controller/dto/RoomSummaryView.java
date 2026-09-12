package com.lbg0146.backend.room.controller.dto;

import com.lbg0146.backend.room.Phase;
import com.lbg0146.backend.room.Room;
import com.lbg0146.backend.room.RoomInstance;

// 로비의 방 목록 한 줄에 필요한 최소 정보. 비밀번호 등 민감한 값은 담지 않는다.
// startingChips/bigBlind는 목록 줄에서부터 "어떤 판인지" 감을 잡을 수 있도록 노출한다(로비 UX 개선).
public record RoomSummaryView(
        String roomCode,
        String name,
        boolean isPrivate,
        int playerCount,
        int maxPlayers,
        boolean inProgress,
        int startingChips,
        int bigBlind
) {
    public static RoomSummaryView from(RoomInstance instance) {
        Room room = instance.getRoom();
        Phase phase = room.getPhase();
        boolean inProgress = phase != null && phase != Phase.SHOWDOWN;
        return new RoomSummaryView(room.getRoomCode(), room.getName(), room.isPrivate(),
                room.getPlayers().size(), room.getMaxPlayers(), inProgress,
                room.getStartingChips(), room.getBigBlind());
    }
}
