package com.lbg0146.backend.room.controller;

import com.lbg0146.backend.game.GameEngine;
import com.lbg0146.backend.player.PlayerAction;
import com.lbg0146.backend.room.Room;
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

// Room/GameEngine이 싱글톤 빈이라 테스트 간 상태가 섞이지 않도록 매 테스트마다 컨텍스트를 새로 띄운다.
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private GameEngine gameEngine;

    private String join(String nickname) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/room/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JoinPlayerRequest(nickname))))
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

    @Test
    void 빈_방_상태를_조회할_수_있다() throws Exception {
        RoomStateResponse response = readRoomState(get("/api/room"));

        assertEquals(0, response.players().size());
        assertNull(response.phase());
    }

    @Test
    void 참가하면_UUID_형태의_playerId를_받는다() throws Exception {
        String playerId = join("Alice");

        assertDoesNotThrow(() -> UUID.fromString(playerId));
    }

    @Test
    void 닉네임이_비어있으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/room/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JoinPlayerRequest(""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 정원을_초과해서_참가하면_409를_반환한다() throws Exception {
        for (int i = 0; i < 6; i++) {
            join("P" + i);
        }

        mockMvc.perform(post("/api/room/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JoinPlayerRequest("extra"))))
                .andExpect(status().isConflict());
    }

    @Test
    void 최소_인원_미만이면_핸드_시작이_409를_반환한다() throws Exception {
        join("Alice");

        mockMvc.perform(post("/api/room/hands"))
                .andExpect(status().isConflict());
    }

    @Test
    void 핸드를_시작하면_본인_홀카드만_보이고_다른_사람_카드는_숨겨진다() throws Exception {
        String aliceId = join("Alice");
        String bobId = join("Bob");

        mockMvc.perform(post("/api/room/hands")).andExpect(status().isOk());

        RoomStateResponse asAlice = readRoomState(get("/api/room").param("playerId", aliceId));
        RoomStateResponse asSpectator = readRoomState(get("/api/room"));

        assertEquals(2, findPlayer(asAlice, aliceId).holeCards().size());
        assertEquals(0, findPlayer(asAlice, bobId).holeCards().size());
        assertEquals(0, findPlayer(asSpectator, aliceId).holeCards().size());
    }

    @Test
    void 핸드_시작_응답도_playerId를_주면_본인_홀카드가_바로_보인다() throws Exception {
        String aliceId = join("Alice");
        String bobId = join("Bob");

        RoomStateResponse asAlice = readRoomState(post("/api/room/hands").param("playerId", aliceId));

        assertEquals(2, findPlayer(asAlice, aliceId).holeCards().size());
        assertEquals(0, findPlayer(asAlice, bobId).holeCards().size());
    }

    @Test
    void 베팅_중에도_현재_팟과_누적_기여금이_응답에_바로_반영된다() throws Exception {
        String aliceId = join("Alice");
        String bobId = join("Bob");

        // 헤즈업: 버튼=Alice(SB), Bob(BB), 프리플랍 첫 액션=Alice
        // 게임 액션은 REST가 아니라 WebSocket으로만 처리되므로, GameEngine을 직접 호출해서 액션을 적용한다.
        mockMvc.perform(post("/api/room/hands")).andExpect(status().isOk());
        gameEngine.applyAction(aliceId, PlayerAction.CALL, 0);

        RoomStateResponse afterAliceCalls = readRoomState(get("/api/room"));
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
        String aliceId = join("Alice");
        String bobId = join("Bob");

        // 헤즈업: 버튼=Alice(SB), Bob(BB), 프리플랍 첫 액션=Alice. Alice가 폴드하면 Bob이 액션 없이 즉시 승리한다.
        mockMvc.perform(post("/api/room/hands")).andExpect(status().isOk());
        gameEngine.applyAction(aliceId, PlayerAction.FOLD, 0);

        RoomStateResponse asBob = readRoomState(get("/api/room").param("playerId", bobId));
        assertNull(asBob.currentActorId(), "핸드가 끝났으므로 아무도 액션할 차례가 아니다");
        assertNull(asBob.currentBet());
        assertNull(asBob.minimumRaise());
        assertEquals(2, findPlayer(asBob, bobId).holeCards().size(), "본인 카드는 그대로 보인다");

        RoomStateResponse asAlice = readRoomState(get("/api/room").param("playerId", aliceId));
        assertEquals(0, findPlayer(asAlice, bobId).holeCards().size(), "폴드로 이긴 것이라 상대 카드가 공개되면 안 된다");
        assertNull(asAlice.showdownHands(), "폴드로 끝난 핸드는 쇼다운 족보 정보가 없어야 한다");
    }

    @Test
    void 리버까지_체크로_진행하면_헤즈업_공개_결정_대기_상태가_된다() throws Exception {
        String aliceId = join("Alice");
        String bobId = join("Bob");

        // 헤즈업: 버튼=Alice(SB), Bob(BB). 프리플랍은 Alice 콜 -> Bob 체크, 이후 스트리트는 Bob(비버튼)이 먼저 체크한다.
        mockMvc.perform(post("/api/room/hands")).andExpect(status().isOk());
        gameEngine.applyAction(aliceId, PlayerAction.CALL, 0);
        gameEngine.applyAction(bobId, PlayerAction.CHECK, 0);
        for (int street = 0; street < 3; street++) {
            gameEngine.applyAction(bobId, PlayerAction.CHECK, 0);
            gameEngine.applyAction(aliceId, PlayerAction.CHECK, 0);
        }

        // 헤즈업 쇼다운은 곧바로 확정되지 않는다 — 무작위로 한 명은 자동 공개되고, 나머지 한
        // 명(결정자)의 공개/머크 결정을 기다리는 동안은 아직 핸드가 "끝난" 상태가 아니다.
        RoomStateResponse pending = readRoomState(get("/api/room"));
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

        RoomStateResponse state = readRoomState(get("/api/room"));
        assertNull(state.headsUpDeciderPlayerId(), "결정이 끝났으므로 더 이상 대기 상태가 아니다");
        assertNotNull(state.showdownHands(), "쇼다운까지 갔으므로 족보 정보가 있어야 한다");
        assertEquals(2, state.showdownHands().size());
        assertTrue(state.showdownHands().stream().anyMatch(ShowdownHandView::isWinner), "승자가 최소 한 명은 있어야 한다");
        assertEquals(2, findPlayer(state, aliceId).holeCards().size(), "공개를 선택했으므로 양쪽 다 카드가 공개된다");
        assertEquals(2, findPlayer(state, bobId).holeCards().size());
    }
}
