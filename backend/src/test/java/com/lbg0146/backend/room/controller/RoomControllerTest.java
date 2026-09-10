package com.lbg0146.backend.room.controller;

import com.lbg0146.backend.game.GameEngine;
import com.lbg0146.backend.player.PlayerAction;
import com.lbg0146.backend.room.Room;
import com.lbg0146.backend.room.RoomManager;
import com.lbg0146.backend.room.controller.dto.CreateRoomResponse;
import com.lbg0146.backend.room.controller.dto.JoinPlayerRequest;
import com.lbg0146.backend.room.controller.dto.JoinPlayerResponse;
import com.lbg0146.backend.room.controller.dto.PlayerView;
import com.lbg0146.backend.room.controller.dto.RoomStateResponse;
import com.lbg0146.backend.room.controller.dto.ShowdownHandView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// RoomManager가 방마다 상태를 격리해주므로(더 이상 싱글톤 Room/GameEngine이 아님) 이론적으로는
// DirtiesContext 없이도 테스트끼리 안전하지만, AuthService의 토큰 맵은 여전히 싱글톤이라 다른 테스트
// 클래스와의 격리를 위해 기존 패턴을 유지한다.
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RoomControllerTest {

    private static final int DEFAULT_STARTING_CHIPS = 30_000;
    private static final int DEFAULT_BIG_BLIND = 200;
    private static final int DEFAULT_MAX_PLAYERS = 6;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RoomManager roomManager;

    private String createRoom() throws Exception {
        return createRoom(DEFAULT_STARTING_CHIPS, DEFAULT_BIG_BLIND, DEFAULT_MAX_PLAYERS);
    }

    private String createRoom(int startingChips, int bigBlind, int maxPlayers) throws Exception {
        String body = "{\"name\":\"테스트방\",\"isPrivate\":false,\"password\":null,"
                + "\"startingChips\":" + startingChips + ",\"bigBlind\":" + bigBlind
                + ",\"maxPlayers\":" + maxPlayers + "}";
        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), CreateRoomResponse.class).roomCode();
    }

    private String createPrivateRoom(String password) throws Exception {
        String body = "{\"name\":\"비공개방\",\"isPrivate\":true,\"password\":\"" + password + "\","
                + "\"startingChips\":" + DEFAULT_STARTING_CHIPS + ",\"bigBlind\":" + DEFAULT_BIG_BLIND
                + ",\"maxPlayers\":" + DEFAULT_MAX_PLAYERS + "}";
        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), CreateRoomResponse.class).roomCode();
    }

    private String join(String roomCode, String nickname) throws Exception {
        return join(roomCode, nickname, null, null);
    }

    private String join(String roomCode, String nickname, String authToken) throws Exception {
        return join(roomCode, nickname, authToken, null);
    }

    private String join(String roomCode, String nickname, String authToken, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/rooms/" + roomCode + "/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JoinPlayerRequest(nickname, authToken, password))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), JoinPlayerResponse.class).playerId();
    }

    private RoomStateResponse readRoomState(RequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), RoomStateResponse.class);
    }

    private PlayerView findPlayer(RoomStateResponse response, String playerId) {
        return response.players().stream()
                .filter(p -> p.id().equals(playerId))
                .findFirst()
                .orElseThrow();
    }

    private GameEngine gameEngineFor(String roomCode) {
        return roomManager.findRoom(roomCode).getGameEngine();
    }

    @Test
    void 빈_방_상태를_조회할_수_있다() throws Exception {
        String roomCode = createRoom();

        RoomStateResponse response = readRoomState(get("/api/rooms/" + roomCode));

        assertEquals(0, response.players().size());
        assertNull(response.phase());
        assertEquals(roomCode, response.roomCode());
    }

    @Test
    void 존재하지_않는_방을_조회하면_404를_반환한다() throws Exception {
        mockMvc.perform(get("/api/rooms/NOSUCH"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 방을_만들때_설정한_값이_그대로_적용된다() throws Exception {
        String roomCode = createRoom(50_000, 1000, 4);

        String playerId = join(roomCode, "Alice");
        RoomStateResponse response = readRoomState(get("/api/rooms/" + roomCode).param("playerId", playerId));

        assertEquals(50_000, response.startingChips());
        assertEquals(1000, response.bigBlind());
        assertEquals(500, response.smallBlind());
        assertEquals(4, response.maxPlayers());
        assertEquals(50_000, findPlayer(response, playerId).chips());
    }

    @Test
    void 빅블라인드가_단위에_맞지_않으면_방_생성이_400을_반환한다() throws Exception {
        String body = "{\"name\":\"잘못된방\",\"isPrivate\":false,\"password\":null,"
                + "\"startingChips\":30000,\"bigBlind\":150,\"maxPlayers\":6}";
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 비공개방도_로비_목록에_나타나고_isPrivate_플래그로_구분된다() throws Exception {
        String publicRoomCode = createRoom();
        String privateRoomCode = createPrivateRoom("secret");

        MvcResult listResult = mockMvc.perform(get("/api/rooms")).andExpect(status().isOk()).andReturn();
        String listBody = listResult.getResponse().getContentAsString();
        assertTrue(listBody.contains(publicRoomCode));
        assertTrue(listBody.contains(privateRoomCode));

        RoomStateResponse detail = readRoomState(get("/api/rooms/" + privateRoomCode));
        assertEquals(privateRoomCode, detail.roomCode());
        assertTrue(detail.isPrivate());
    }

    @Test
    void 비공개방에_비밀번호가_틀리면_입장이_400을_반환한다() throws Exception {
        String roomCode = createPrivateRoom("secret");

        mockMvc.perform(post("/api/rooms/" + roomCode + "/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JoinPlayerRequest("Alice", null, "wrong"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 비공개방에_비밀번호가_맞으면_입장된다() throws Exception {
        String roomCode = createPrivateRoom("secret");

        String playerId = join(roomCode, "Alice", null, "secret");

        assertDoesNotThrow(() -> UUID.fromString(playerId));
    }

    @Test
    void 코드로_입장하면_비공개방이라도_비밀번호_없이_입장된다() throws Exception {
        String roomCode = createPrivateRoom("secret");

        MvcResult result = mockMvc.perform(post("/api/rooms/" + roomCode + "/players/by-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JoinPlayerRequest("Alice", null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        String playerId = objectMapper.readValue(result.getResponse().getContentAsString(), JoinPlayerResponse.class)
                .playerId();

        assertDoesNotThrow(() -> UUID.fromString(playerId));
    }

    @Test
    void 참가하면_UUID_형태의_playerId를_받는다() throws Exception {
        String roomCode = createRoom();

        String playerId = join(roomCode, "Alice");

        assertDoesNotThrow(() -> UUID.fromString(playerId));
    }

    @Test
    void 닉네임이_비어있으면_400을_반환한다() throws Exception {
        String roomCode = createRoom();

        mockMvc.perform(post("/api/rooms/" + roomCode + "/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JoinPlayerRequest("", null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 게스트로_참가하면_닉네임_뒤에_Guest가_붙는다() throws Exception {
        String roomCode = createRoom();

        String playerId = join(roomCode, "Alice");

        RoomStateResponse response = readRoomState(get("/api/rooms/" + roomCode).param("playerId", playerId));

        assertEquals("Alice (Guest)", findPlayer(response, playerId).nickname());
    }

    @Test
    void 유효한_토큰으로_참가하면_요청한_닉네임_대신_가입_시_정한_닉네임을_사용한다() throws Exception {
        // MySQL에 영속되므로 반복 실행해도 충돌 없게 매번 새 username을 쓴다(이미 있으면 이 register
        // 호출은 조용히 409로 실패하고, 아래 로그인은 처음 가입 시의 비밀번호/닉네임으로 그대로 성공한다).
        String username = "u" + Long.toString(System.nanoTime(), 36);
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"pw1234\",\"nickname\":\"가입시닉네임\"}"));
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"pw1234\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("token").asText();

        String roomCode = createRoom();
        String playerId = join(roomCode, "입장할때_보낸_닉네임(무시되어야함)", token);

        RoomStateResponse response = readRoomState(get("/api/rooms/" + roomCode).param("playerId", playerId));

        assertEquals("가입시닉네임", findPlayer(response, playerId).nickname());
    }

    @Test
    void 같은_계정으로_다른_브라우저에서_또_참가하면_409를_반환한다() throws Exception {
        String username = "u" + Long.toString(System.nanoTime(), 36);
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"pw1234\",\"nickname\":\"중복테스트\"}"));
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"pw1234\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("token").asText();

        String roomCode = createRoom();
        join(roomCode, null, token); // 첫 번째 브라우저에서 입장

        // 같은 토큰으로 두 번째 브라우저에서 또 입장을 시도하면 거부되어야 한다.
        mockMvc.perform(post("/api/rooms/" + roomCode + "/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JoinPlayerRequest(null, token, null))))
                .andExpect(status().isConflict());
    }

    @Test
    void 핸드_진행_중이_아닐_때_나가기를_요청하면_즉시_방에서_빠진다() throws Exception {
        String roomCode = createRoom();
        String aliceId = join(roomCode, "Alice");
        join(roomCode, "Bob");

        mockMvc.perform(post("/api/rooms/" + roomCode + "/leave")
                        .param("playerId", aliceId)
                        .param("leaving", "true"))
                .andExpect(status().isOk());

        RoomStateResponse response = readRoomState(get("/api/rooms/" + roomCode));

        assertTrue(response.players().stream().noneMatch(p -> p.id().equals(aliceId)));
    }

    @Test
    void 마지막_인원이_나가면_방이_삭제된다() throws Exception {
        String roomCode = createRoom();
        String aliceId = join(roomCode, "Alice");

        mockMvc.perform(post("/api/rooms/" + roomCode + "/leave")
                        .param("playerId", aliceId)
                        .param("leaving", "true"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/rooms/" + roomCode))
                .andExpect(status().isNotFound());
    }

    @Test
    void 두_명_중_한_명만_나가면_방은_삭제되지_않는다() throws Exception {
        String roomCode = createRoom();
        String aliceId = join(roomCode, "Alice");
        join(roomCode, "Bob");

        mockMvc.perform(post("/api/rooms/" + roomCode + "/leave")
                        .param("playerId", aliceId)
                        .param("leaving", "true"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/rooms/" + roomCode))
                .andExpect(status().isOk());
    }

    @Test
    void 정원을_초과해서_참가하면_409를_반환한다() throws Exception {
        String roomCode = createRoom();
        for (int i = 0; i < DEFAULT_MAX_PLAYERS; i++) {
            join(roomCode, "P" + i);
        }

        mockMvc.perform(post("/api/rooms/" + roomCode + "/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JoinPlayerRequest("extra", null, null))))
                .andExpect(status().isConflict());
    }

    @Test
    void 최소_인원_미만이면_핸드_시작이_409를_반환한다() throws Exception {
        String roomCode = createRoom();
        join(roomCode, "Alice");

        mockMvc.perform(post("/api/rooms/" + roomCode + "/hands"))
                .andExpect(status().isConflict());
    }

    @Test
    void 핸드를_시작하면_본인_홀카드만_보이고_다른_사람_카드는_숨겨진다() throws Exception {
        String roomCode = createRoom();
        String aliceId = join(roomCode, "Alice");
        String bobId = join(roomCode, "Bob");

        mockMvc.perform(post("/api/rooms/" + roomCode + "/hands")).andExpect(status().isOk());

        RoomStateResponse asAlice = readRoomState(get("/api/rooms/" + roomCode).param("playerId", aliceId));
        RoomStateResponse asSpectator = readRoomState(get("/api/rooms/" + roomCode));

        assertEquals(2, findPlayer(asAlice, aliceId).holeCards().size());
        assertEquals(0, findPlayer(asAlice, bobId).holeCards().size());
        assertEquals(0, findPlayer(asSpectator, aliceId).holeCards().size());
    }

    @Test
    void 핸드_시작_응답도_playerId를_주면_본인_홀카드가_바로_보인다() throws Exception {
        String roomCode = createRoom();
        String aliceId = join(roomCode, "Alice");
        String bobId = join(roomCode, "Bob");

        RoomStateResponse asAlice = readRoomState(
                post("/api/rooms/" + roomCode + "/hands").param("playerId", aliceId));

        assertEquals(2, findPlayer(asAlice, aliceId).holeCards().size());
        assertEquals(0, findPlayer(asAlice, bobId).holeCards().size());
    }

    @Test
    void 베팅_중에도_현재_팟과_누적_기여금이_응답에_바로_반영된다() throws Exception {
        String roomCode = createRoom();
        String aliceId = join(roomCode, "Alice");
        String bobId = join(roomCode, "Bob");

        // 헤즈업: 버튼=Alice(SB), Bob(BB), 프리플랍 첫 액션=Alice
        // 게임 액션은 REST가 아니라 WebSocket으로만 처리되므로, GameEngine을 직접 호출해서 액션을 적용한다.
        mockMvc.perform(post("/api/rooms/" + roomCode + "/hands")).andExpect(status().isOk());
        gameEngineFor(roomCode).applyAction(aliceId, PlayerAction.CALL, 0);

        RoomStateResponse afterAliceCalls = readRoomState(get("/api/rooms/" + roomCode));
        // 아직 쇼다운(핸드 종료) 전이지만, Alice가 콜해서 둘 다 BIG_BLIND씩 낸 상태 — 응답에 바로 반영돼야 한다.
        assertEquals(1, afterAliceCalls.pots().size());
        assertEquals(Room.BIG_BLIND * 2, afterAliceCalls.pots().get(0).amount());
        assertEquals(Room.BIG_BLIND, findPlayer(afterAliceCalls, aliceId).totalHandContribution());
        assertEquals(Room.BIG_BLIND, findPlayer(afterAliceCalls, bobId).totalHandContribution());
        assertEquals(PlayerAction.CALL, findPlayer(afterAliceCalls, aliceId).lastAction());
        assertNull(findPlayer(afterAliceCalls, bobId).lastAction(), "아직 액션하지 않은 플레이어의 lastAction은 null이다");
    }

    @Test
    void 폴드로_핸드가_끝나면_상대_턴처럼_보이지_않고_승자_카드도_공개되지_않는다() throws Exception {
        String roomCode = createRoom();
        String aliceId = join(roomCode, "Alice");
        String bobId = join(roomCode, "Bob");

        // 헤즈업: 버튼=Alice(SB), Bob(BB), 프리플랍 첫 액션=Alice. Alice가 폴드하면 Bob이 액션 없이 즉시 승리한다.
        mockMvc.perform(post("/api/rooms/" + roomCode + "/hands")).andExpect(status().isOk());
        gameEngineFor(roomCode).applyAction(aliceId, PlayerAction.FOLD, 0);

        RoomStateResponse asBob = readRoomState(get("/api/rooms/" + roomCode).param("playerId", bobId));
        assertNull(asBob.currentActorId(), "핸드가 끝났으므로 아무도 액션할 차례가 아니다");
        assertNull(asBob.currentBet());
        assertNull(asBob.minimumRaise());
        assertEquals(2, findPlayer(asBob, bobId).holeCards().size(), "본인 카드는 그대로 보인다");

        RoomStateResponse asAlice = readRoomState(get("/api/rooms/" + roomCode).param("playerId", aliceId));
        assertEquals(0, findPlayer(asAlice, bobId).holeCards().size(), "폴드로 이긴 것이라 상대 카드가 공개되면 안 된다");
        assertNull(asAlice.showdownHands(), "폴드로 끝난 핸드는 쇼다운 족보 정보가 없어야 한다");
    }

    @Test
    void 리버까지_체크로_진행하면_헤즈업_공개_결정_대기_상태가_된다() throws Exception {
        String roomCode = createRoom();
        String aliceId = join(roomCode, "Alice");
        String bobId = join(roomCode, "Bob");
        GameEngine gameEngine = gameEngineFor(roomCode);

        // 헤즈업: 버튼=Alice(SB), Bob(BB). 프리플랍은 Alice 콜 -> Bob 체크, 이후 스트리트는 Bob(비버튼)이 먼저 체크한다.
        mockMvc.perform(post("/api/rooms/" + roomCode + "/hands")).andExpect(status().isOk());
        gameEngine.applyAction(aliceId, PlayerAction.CALL, 0);
        gameEngine.applyAction(bobId, PlayerAction.CHECK, 0);
        for (int street = 0; street < 3; street++) {
            gameEngine.applyAction(bobId, PlayerAction.CHECK, 0);
            gameEngine.applyAction(aliceId, PlayerAction.CHECK, 0);
        }

        // 헤즈업 쇼다운은 곧바로 확정되지 않는다 — 무작위로 한 명은 자동 공개되고, 나머지 한
        // 명(결정자)의 공개/머크 결정을 기다리는 동안은 아직 핸드가 "끝난" 상태가 아니다.
        RoomStateResponse pending = readRoomState(get("/api/rooms/" + roomCode));
        assertEquals(5, pending.communityCards().size());
        assertNull(pending.currentActorId());
        assertNull(pending.showdownHands(), "결정이 끝나기 전에는 족보 정보가 아직 없어야 한다");
        String deciderId = pending.headsUpDeciderPlayerId();
        assertNotNull(deciderId, "정확히 2명이 쇼다운까지 갔으니 결정자가 정해져 있어야 한다");
        long visibleCount = List.of(aliceId, bobId).stream()
                .filter(id -> findPlayer(pending, id).holeCards().size() == 2)
                .count();
        assertEquals(1, visibleCount, "무작위로 뽑힌 한 명만 먼저 자동 공개되어야 한다");

        // 결정자가 공개를 선택하면 그제서야 실제로 핸드가 확정된다.
        gameEngine.decideHeadsUpReveal(deciderId, true);

        RoomStateResponse state = readRoomState(get("/api/rooms/" + roomCode));
        assertNull(state.headsUpDeciderPlayerId(), "결정이 끝났으므로 더 이상 대기 상태가 아니다");
        assertNotNull(state.showdownHands(), "쇼다운까지 갔으므로 족보 정보가 있어야 한다");
        assertEquals(2, state.showdownHands().size());
        assertTrue(state.showdownHands().stream().anyMatch(ShowdownHandView::isWinner), "승자가 최소 한 명은 있어야 한다");
        assertEquals(2, findPlayer(state, aliceId).holeCards().size(), "공개를 선택했으므로 양쪽 다 카드가 공개된다");
        assertEquals(2, findPlayer(state, bobId).holeCards().size());
    }
}
