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
    private final TurnTimerService turnTimerService;
    private final AutoStartService autoStartService;
    private final HeadsUpRevealTimerService headsUpRevealTimerService;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    public RoomBroadcaster(GameEngine gameEngine, ObjectMapper objectMapper, TurnTimerService turnTimerService,
            AutoStartService autoStartService, HeadsUpRevealTimerService headsUpRevealTimerService) {
        this.gameEngine = gameEngine;
        this.objectMapper = objectMapper;
        this.turnTimerService = turnTimerService;
        this.autoStartService = autoStartService;
        this.headsUpRevealTimerService = headsUpRevealTimerService;
    }

    public void register(WebSocketSession session) {
        sessions.add(session);
    }

    public void unregister(WebSocketSession session) {
        sessions.remove(session);
    }

    // 홀카드 노출 규칙이 요청자마다 다르므로, 세션마다 각자의 playerId 기준으로 상태를 다시 계산해서 개별 전송한다.
    // 상태가 실제로 바뀌는 지점(액션 적용/핸드 시작 등)마다 호출되므로, 턴 타이머를 다시 예약할지
    // 판단하기에도 정확히 맞는 지점이다 — onStateBroadcast가 자체적으로 "턴이 실제로 바뀌었을 때만"
    // 다시 예약하므로 여기서 매번 호출해도 안전하다.
    public void broadcastState() {
        turnTimerService.onStateBroadcast(gameEngine, this::broadcastState);
        autoStartService.onStateBroadcast(gameEngine, this::broadcastState);
        headsUpRevealTimerService.onStateBroadcast(gameEngine, this::broadcastState);
        for (WebSocketSession session : sessions) {
            sendState(session);
        }
    }

    public void sendState(WebSocketSession session) {
        String playerId = (String) session.getAttributes().get(PLAYER_ID_ATTRIBUTE);
        RoomStateResponse state = gameEngine.withLock(() -> RoomStateMapper.toResponse(gameEngine, playerId,
                turnTimerService.getCurrentDeadlineMillis(), autoStartService.getCurrentDeadlineMillis(),
                headsUpRevealTimerService.getCurrentDeadlineMillis()));
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
