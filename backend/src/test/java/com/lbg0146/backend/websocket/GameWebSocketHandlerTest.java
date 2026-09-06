package com.lbg0146.backend.websocket;

import com.lbg0146.backend.game.GameEngine;
import com.lbg0146.backend.player.Player;
import com.lbg0146.backend.player.PlayerAction;
import com.lbg0146.backend.room.Room;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class GameWebSocketHandlerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private GameEngine gameEngine;

    @Autowired
    private ObjectMapper objectMapper;

    private final StandardWebSocketClient client = new StandardWebSocketClient();

    private String join(String nickname) {
        String id = UUID.randomUUID().toString();
        gameEngine.getRoom().addPlayer(new Player(id, nickname, Room.STARTING_CHIPS));
        return id;
    }

    private WebSocketSession connect(String playerId, BlockingQueue<String> received) throws Exception {
        String url = "ws://localhost:" + port + "/ws" + (playerId == null ? "" : "?playerId=" + playerId);
        WebSocketSession session = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                received.add(message.getPayload());
            }
        }, url).get(5, TimeUnit.SECONDS);
        received.poll(5, TimeUnit.SECONDS); // 연결 직후 오는 초기 STATE는 소비하고 시작한다.
        return session;
    }

    private void sendAction(WebSocketSession session, PlayerAction action, int amount) throws Exception {
        session.sendMessage(new TextMessage(
                "{\"type\":\"ACTION\",\"action\":\"" + action + "\",\"amount\":" + amount + "}"));
    }

    @Test
    void 액션을_보내면_연결된_모두에게_갱신된_상태가_브로드캐스트된다() throws Exception {
        String aliceId = join("Alice");
        String bobId = join("Bob");
        gameEngine.startHand(); // 헤즈업: 먼저 참가한 Alice가 버튼/SB이자 첫 액션자

        BlockingQueue<String> aliceMessages = new LinkedBlockingQueue<>();
        BlockingQueue<String> bobMessages = new LinkedBlockingQueue<>();
        WebSocketSession aliceSession = connect(aliceId, aliceMessages);
        WebSocketSession bobSession = connect(bobId, bobMessages);

        sendAction(aliceSession, PlayerAction.CALL, 0);

        String bobReceived = bobMessages.poll(5, TimeUnit.SECONDS);
        assertNotNull(bobReceived);
        JsonNode node = objectMapper.readTree(bobReceived);
        assertEquals("STATE", node.get("type").asString());

        aliceSession.close();
        bobSession.close();
    }

    @Test
    void 차례가_아닌_액션을_보내면_보낸_세션에만_에러가_온다() throws Exception {
        String aliceId = join("Alice");
        String bobId = join("Bob");
        gameEngine.startHand(); // Alice 차례

        BlockingQueue<String> bobMessages = new LinkedBlockingQueue<>();
        WebSocketSession bobSession = connect(bobId, bobMessages);

        sendAction(bobSession, PlayerAction.CALL, 0);

        String bobReceived = bobMessages.poll(5, TimeUnit.SECONDS);
        assertNotNull(bobReceived);
        JsonNode node = objectMapper.readTree(bobReceived);
        assertEquals("ERROR", node.get("type").asString());

        bobSession.close();
    }

    @Test
    void 지원하지_않는_메시지_타입은_에러로_응답한다() throws Exception {
        String aliceId = join("Alice");
        join("Bob");
        gameEngine.startHand();

        BlockingQueue<String> aliceMessages = new LinkedBlockingQueue<>();
        WebSocketSession aliceSession = connect(aliceId, aliceMessages);

        aliceSession.sendMessage(new TextMessage("{\"type\":\"CHAT\"}"));

        String received = aliceMessages.poll(5, TimeUnit.SECONDS);
        assertNotNull(received);
        JsonNode node = objectMapper.readTree(received);
        assertEquals("ERROR", node.get("type").asString());

        aliceSession.close();
    }

    @Test
    void 존재하지_않는_playerId로_연결하면_거부된다() throws Exception {
        join("Alice");

        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        WebSocketSession session = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                messages.add(message.getPayload());
            }
        }, "ws://localhost:" + port + "/ws?playerId=" + UUID.randomUUID()).get(5, TimeUnit.SECONDS);

        // 서버가 연결 직후 close 프레임을 보내므로 잠시 대기 후 상태를 확인한다.
        Thread.sleep(500);
        assertEquals(false, session.isOpen());
    }
}
