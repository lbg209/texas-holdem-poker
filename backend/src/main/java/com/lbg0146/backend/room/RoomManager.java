package com.lbg0146.backend.room;

import com.lbg0146.backend.exception.InvalidActionException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

// roomCode로 구분되는 여러 개의 RoomInstance(방)를 관리한다. 예전 RoomConfig가 Room/GameEngine을
// 서버 전체에서 하나씩만 있는 싱글톤 빈으로 등록했던 것을, 멀티룸으로 확장하면서 이 클래스가 대체한다.
// Room/GameEngine 자체에는 "싱글톤"을 가정한 코드가 없었기 때문에 이 관리 계층만 새로 생기는 것으로 충분하다.
@Component
public class RoomManager {

    // 헷갈리기 쉬운 0/O, 1/I는 코드 생성에서 제외한다.
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;

    private final ObjectMapper objectMapper;
    // 방 생성 순서(로비 목록 정렬)를 보존하기 위해 LinkedHashMap을 쓰고, 동시 접근은 바깥쪽
    // synchronizedMap으로 보호한다.
    private final Map<String, RoomInstance> rooms = Collections.synchronizedMap(new LinkedHashMap<>());

    public RoomManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // 방을 새로 만들고 roomCode를 반환한다. 비공개방(isPrivate=true)은 비밀번호가 필수다.
    public String createRoom(String name, boolean isPrivate, String password, int startingChips, int bigBlind,
            int maxPlayers) {
        if (name == null || name.isBlank()) {
            throw new InvalidActionException("방 이름을 입력해주세요.");
        }
        if (isPrivate && (password == null || password.isBlank())) {
            throw new InvalidActionException("비공개방은 비밀번호를 입력해야 합니다.");
        }

        Room room = new Room();
        room.configure(startingChips, bigBlind, maxPlayers);
        String roomCode = generateUniqueRoomCode();
        room.initIdentity(roomCode, name.trim(), isPrivate, isPrivate ? password : null);

        rooms.put(roomCode, new RoomInstance(room, objectMapper, () -> rooms.remove(roomCode)));
        return roomCode;
    }

    public RoomInstance findRoom(String roomCode) {
        RoomInstance instance = rooms.get(roomCode);
        if (instance == null) {
            throw new IllegalArgumentException("존재하지 않는 방입니다: " + roomCode);
        }
        return instance;
    }

    // 로비 목록에 노출되는 방들. 비공개방도 함께 노출된다 — 로비에서는 자물쇠 표시만 하고, 실제
    // 보호는 입장 시 비밀번호 검사(RoomController.joinRoom)가 담당한다.
    public List<RoomInstance> listRooms() {
        synchronized (rooms) {
            return List.copyOf(rooms.values());
        }
    }

    private String generateUniqueRoomCode() {
        String code;
        do {
            code = randomCode();
        } while (rooms.containsKey(code));
        return code;
    }

    private static String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(ThreadLocalRandom.current().nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
