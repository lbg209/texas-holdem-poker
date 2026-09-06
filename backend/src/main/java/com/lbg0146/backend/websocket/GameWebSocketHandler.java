package com.lbg0146.backend.websocket;

import com.lbg0146.backend.exception.PokerException;
import com.lbg0146.backend.game.GameEngine;
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

    private final GameEngine gameEngine;
    private final RoomBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    public GameWebSocketHandler(GameEngine gameEngine, RoomBroadcaster broadcaster, ObjectMapper objectMapper) {
        this.gameEngine = gameEngine;
        this.broadcaster = broadcaster;
        this.objectMapper = objectMapper;
    }

    // playerId 쿼리 파라미터가 없으면 관전자로 연결한다 (REST GET /api/room과 동일한 규칙).
    // playerId가 있는데 방에 없는 값이면 연결을 거부한다.
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String playerId = extractPlayerId(session);
        if (playerId != null) {
            try {
                gameEngine.withLock(() -> gameEngine.getRoom().findPlayer(playerId));
            } catch (IllegalArgumentException e) {
                session.close(CloseStatus.BAD_DATA.withReason("존재하지 않는 playerId입니다."));
                return;
            }
        }
        session.getAttributes().put(RoomBroadcaster.PLAYER_ID_ATTRIBUTE, playerId);
        broadcaster.register(session);
        broadcaster.sendState(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ActionMessage actionMessage;
        try {
            actionMessage = objectMapper.readValue(message.getPayload(), ActionMessage.class);
        } catch (Exception e) {
            broadcaster.sendError(session, "메시지 형식이 올바르지 않습니다.");
            return;
        }

        if (!"ACTION".equals(actionMessage.type())) {
            broadcaster.sendError(session, "지원하지 않는 메시지 타입입니다: " + actionMessage.type());
            return;
        }

        String playerId = (String) session.getAttributes().get(RoomBroadcaster.PLAYER_ID_ATTRIBUTE);
        if (playerId == null) {
            broadcaster.sendError(session, "관전자는 액션을 보낼 수 없습니다.");
            return;
        }

        try {
            gameEngine.applyAction(playerId, actionMessage.action(), actionMessage.amount());
        } catch (PokerException e) {
            broadcaster.sendError(session, e.getMessage());
            return;
        }

        broadcaster.broadcastState();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        broadcaster.unregister(session);
    }

    private String extractPlayerId(WebSocketSession session) {
        return UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().getFirst("playerId");
    }
}
