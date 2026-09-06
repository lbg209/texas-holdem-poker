package com.lbg0146.backend.room.controller;

import com.lbg0146.backend.room.controller.dto.JoinPlayerRequest;
import com.lbg0146.backend.room.controller.dto.JoinPlayerResponse;
import com.lbg0146.backend.room.controller.dto.PlayerView;
import com.lbg0146.backend.room.controller.dto.RoomStateResponse;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
}
