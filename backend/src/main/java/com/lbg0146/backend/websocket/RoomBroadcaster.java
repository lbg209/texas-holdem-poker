package com.lbg0146.backend.websocket;

import com.lbg0146.backend.game.GameEngine;
import com.lbg0146.backend.room.controller.RoomStateMapper;
import com.lbg0146.backend.room.controller.dto.RoomStateResponse;
import com.lbg0146.backend.websocket.dto.ErrorMessage;
import com.lbg0146.backend.websocket.dto.StateMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// 연결된 WebSocket 세션을 등록/해제하고, 상태 변경 시 세션마다 맞춤 상태를 보내준다.
@Component
public class RoomBroadcaster {

    static final String PLAYER_ID_ATTRIBUTE = "playerId";

    private final GameEngine gameEngine;
    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    public RoomBroadcaster(GameEngine gameEngine, ObjectMapper objectMapper) {
        this.gameEngine = gameEngine;
        this.objectMapper = objectMapper;
    }

    public void register(WebSocketSession session) {
        sessions.add(session);
    }

    public void unregister(WebSocketSession session) {
        sessions.remove(session);
    }

    // 홀카드 노출 규칙이 요청자마다 다르므로, 세션마다 각자의 playerId 기준으로 상태를 다시 계산해서 개별 전송한다.
    public void broadcastState() {
        for (WebSocketSession session : sessions) {
            sendState(session);
        }
    }

    public void sendState(WebSocketSession session) {
        String playerId = (String) session.getAttributes().get(PLAYER_ID_ATTRIBUTE);
        RoomStateResponse state = gameEngine.withLock(() -> RoomStateMapper.toResponse(gameEngine, playerId));
        send(session, new StateMessage(state));
    }

    public void sendError(WebSocketSession session, String message) {
        send(session, new ErrorMessage(message));
    }

    private void send(WebSocketSession session, Object payload) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (Exception e) {
            sessions.remove(session);
        }
    }
}
