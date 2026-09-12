package com.lbg0146.backend.websocket;

import com.lbg0146.backend.player.Player;
import com.lbg0146.backend.player.PlayerAction;
import com.lbg0146.backend.room.RoomManager;
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
    private RoomManager roomManager;

    @Autowired
    private ObjectMapper objectMapper;

    private final StandardWebSocketClient client = new StandardWebSocketClient();

    private String createRoom() {
        return roomManager.createRoom("테스트방", false, null, 30_000, 200);
    }

    private String join(String roomCode, String nickname) {
        String id = UUID.randomUUID().toString();
        roomManager.findRoom(roomCode).getGameEngine().addPlayer(new Player(id, nickname, 30_000));
        return id;
    }

    private WebSocketSession connect(String roomCode, String playerId, BlockingQueue<String> received) throws Exception {
        String url = "ws://localhost:" + port + "/ws?roomCode=" + roomCode
                + (playerId == null ? "" : "&playerId=" + playerId);
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
        String roomCode = createRoom();
        String aliceId = join(roomCode, "Alice");
        String bobId = join(roomCode, "Bob");
        roomManager.findRoom(roomCode).getGameEngine().startHand(); // 헤즈업: 먼저 참가한 Alice가 버튼/SB이자 첫 액션자

        BlockingQueue<String> aliceMessages = new LinkedBlockingQueue<>();
        BlockingQueue<String> bobMessages = new LinkedBlockingQueue<>();
        WebSocketSession aliceSession = connect(roomCode, aliceId, aliceMessages);
        WebSocketSession bobSession = connect(roomCode, bobId, bobMessages);

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
        String roomCode = createRoom();
        String aliceId = join(roomCode, "Alice");
        String bobId = join(roomCode, "Bob");
        roomManager.findRoom(roomCode).getGameEngine().startHand(); // Alice 차례

        BlockingQueue<String> bobMessages = new LinkedBlockingQueue<>();
        WebSocketSession bobSession = connect(roomCode, bobId, bobMessages);

        sendAction(bobSession, PlayerAction.CALL, 0);

        String bobReceived = bobMessages.poll(5, TimeUnit.SECONDS);
        assertNotNull(bobReceived);
        JsonNode node = objectMapper.readTree(bobReceived);
        assertEquals("ERROR", node.get("type").asString());

        bobSession.close();
    }

    @Test
    void 지원하지_않는_메시지_타입은_에러로_응답한다() throws Exception {
        String roomCode = createRoom();
        String aliceId = join(roomCode, "Alice");
        join(roomCode, "Bob");
        roomManager.findRoom(roomCode).getGameEngine().startHand();

        BlockingQueue<String> aliceMessages = new LinkedBlockingQueue<>();
        WebSocketSession aliceSession = connect(roomCode, aliceId, aliceMessages);

        aliceSession.sendMessage(new TextMessage("{\"type\":\"CHAT\"}"));

        String received = aliceMessages.poll(5, TimeUnit.SECONDS);
        assertNotNull(received);
        JsonNode node = objectMapper.readTree(received);
        assertEquals("ERROR", node.get("type").asString());

        aliceSession.close();
    }

    // 실사용 중 발견된 버그의 회귀 테스트: playerId 없이(관전자로) 연결하면, WebSocketSession의
    // attributes 맵(표준 구현은 ConcurrentHashMap 기반이라 null 값을 못 담음)에 null을 그대로
    // put()해서 NPE가 나며 연결이 1011(내부 오류)로 즉시 끊겼었다. 참가 화면(JoinForm)에서 관전자로
    // 연결해 방 상태를 실시간으로 받으려면 이 연결이 정상 동작해야 한다.
    @Test
    void playerId_없이_연결하면_관전자로_등록되고_초기_상태를_받는다() throws Exception {
        String roomCode = createRoom();
        join(roomCode, "Alice");

        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        WebSocketSession session = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                messages.add(message.getPayload());
            }
        }, "ws://localhost:" + port + "/ws?roomCode=" + roomCode).get(5, TimeUnit.SECONDS);

        String received = messages.poll(5, TimeUnit.SECONDS);
        assertNotNull(received, "관전자로 연결해도 초기 STATE를 받아야 한다");
        JsonNode node = objectMapper.readTree(received);
        assertEquals("STATE", node.get("type").asString());
        assertEquals(true, session.isOpen(), "관전자 연결이 즉시 끊기면 안 된다");

        session.close();
    }

    @Test
    void 존재하지_않는_playerId로_연결하면_거부된다() throws Exception {
        String roomCode = createRoom();
        join(roomCode, "Alice");

        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        WebSocketSession session = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                messages.add(message.getPayload());
            }
        }, "ws://localhost:" + port + "/ws?roomCode=" + roomCode + "&playerId=" + UUID.randomUUID())
                .get(5, TimeUnit.SECONDS);

        // 서버가 연결 직후 close 프레임을 보내므로 잠시 대기 후 상태를 확인한다.
        Thread.sleep(500);
        assertEquals(false, session.isOpen());
    }

    @Test
    void 존재하지_않는_roomCode로_연결하면_거부된다() throws Exception {
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        WebSocketSession session = client.execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                messages.add(message.getPayload());
            }
        }, "ws://localhost:" + port + "/ws?roomCode=NOSUCH").get(5, TimeUnit.SECONDS);

        Thread.sleep(500);
        assertEquals(false, session.isOpen());
    }
}
