package com.lbg0146.backend.websocket;

import com.lbg0146.backend.exception.PokerException;
import com.lbg0146.backend.room.RoomInstance;
import com.lbg0146.backend.room.RoomManager;
import com.lbg0146.backend.websocket.dto.ActionMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    // 세션이 어느 방(RoomInstance)에 속하는지는 세션 attributes에 직접 들고 있는다 — RoomManager를
    // 매 메시지/연결종료마다 다시 조회할 필요 없이 바로 그 방의 GameEngine/RoomBroadcaster를 쓴다.
    static final String ROOM_INSTANCE_ATTRIBUTE = "roomInstance";

    private final RoomManager roomManager;
    private final ObjectMapper objectMapper;

    public GameWebSocketHandler(RoomManager roomManager, ObjectMapper objectMapper) {
        this.roomManager = roomManager;
        this.objectMapper = objectMapper;
    }

    // roomCode 쿼리 파라미터가 없거나 존재하지 않는 방이면 연결을 거부한다.
    // playerId 쿼리 파라미터가 없으면 관전자로 연결한다 (REST GET /api/rooms/{roomCode}와 동일한 규칙).
    // playerId가 있는데 그 방에 없는 값이면 연결을 거부한다.
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String roomCode = extractParam(session, "roomCode");
        RoomInstance instance;
        try {
            instance = roomManager.findRoom(roomCode);
        } catch (IllegalArgumentException e) {
            session.close(CloseStatus.BAD_DATA.withReason("존재하지 않는 방입니다."));
            return;
        }

        String playerId = extractParam(session, "playerId");
        if (playerId != null) {
            try {
                instance.getGameEngine().withLock(() -> instance.getGameEngine().getRoom().findPlayer(playerId));
            } catch (IllegalArgumentException e) {
                session.close(CloseStatus.BAD_DATA.withReason("존재하지 않는 playerId입니다."));
                return;
            }
        }

        session.getAttributes().put(ROOM_INSTANCE_ATTRIBUTE, instance);
        // attributes 맵(WebSocketSession 표준 구현은 ConcurrentHashMap 기반)은 null 값을 저장할 수
        // 없다 — 관전자(playerId == null)일 때 그냥 put()하면 NPE로 연결이 즉시 끊겼다(1011). 값이
        // 있을 때만 넣고, 없으면 아예 안 넣는다 — 어차피 읽는 쪽(get)도 없으면 null을 반환하므로
        // 동작은 동일하다.
        if (playerId != null) {
            session.getAttributes().put(RoomBroadcaster.PLAYER_ID_ATTRIBUTE, playerId);
        }
        instance.getBroadcaster().register(session);
        instance.getBroadcaster().sendState(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        RoomInstance instance = (RoomInstance) session.getAttributes().get(ROOM_INSTANCE_ATTRIBUTE);

        ActionMessage actionMessage;
        try {
            actionMessage = objectMapper.readValue(message.getPayload(), ActionMessage.class);
        } catch (Exception e) {
            instance.getBroadcaster().sendError(session, "메시지 형식이 올바르지 않습니다.");
            return;
        }

        if (!"ACTION".equals(actionMessage.type())) {
            instance.getBroadcaster().sendError(session, "지원하지 않는 메시지 타입입니다: " + actionMessage.type());
            return;
        }

        String playerId = (String) session.getAttributes().get(RoomBroadcaster.PLAYER_ID_ATTRIBUTE);
        if (playerId == null) {
            instance.getBroadcaster().sendError(session, "관전자는 액션을 보낼 수 없습니다.");
            return;
        }

        try {
            instance.getGameEngine().applyAction(playerId, actionMessage.action(), actionMessage.amount());
        } catch (PokerException e) {
            instance.getBroadcaster().sendError(session, e.getMessage());
            return;
        }

        instance.getBroadcaster().broadcastState();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        RoomInstance instance = (RoomInstance) session.getAttributes().get(ROOM_INSTANCE_ATTRIBUTE);
        if (instance != null) {
            instance.getBroadcaster().unregister(session);
        }
    }

    private String extractParam(WebSocketSession session, String key) {
        return UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().getFirst(key);
    }
}
