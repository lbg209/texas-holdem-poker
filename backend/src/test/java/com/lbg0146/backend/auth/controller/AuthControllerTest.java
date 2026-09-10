package com.lbg0146.backend.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// User는 실제 MySQL(poker_project)에 영속되어 테스트 실행 간에도 데이터가 남으므로, 매번 새로운
// username을 써서 반복 실행해도 충돌 없이 통과하도록 한다. username은 영문/숫자만 허용되므로
// UUID 대신 nanoTime을 36진수 문자열로 바꿔서 쓴다(하이픈이 안 섞인다).
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String uniqueUsername() {
        return "u" + Long.toString(System.nanoTime(), 36);
    }

    private static final String TEST_NICKNAME = "테스트닉네임";

    private void register(String username, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", username, "password", password, "nickname", TEST_NICKNAME))))
                .andExpect(status().isCreated());
    }

    @Test
    void 회원가입하면_201을_반환한다() throws Exception {
        register(uniqueUsername(), "pw1234");
    }

    @Test
    void 이미_있는_아이디로_회원가입하면_409를_반환한다() throws Exception {
        String username = uniqueUsername();
        register(username, "pw1234");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", username, "password", "other1234", "nickname", "다른닉네임"))))
                .andExpect(status().isConflict());
    }

    @Test
    void 짧은_비밀번호로_회원가입하면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", uniqueUsername(), "password", "abc", "nickname", "닉네임"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 한글이_섞인_아이디로_회원가입하면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "한글아이디", "password", "pw1234", "nickname", "닉네임"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 닉네임이_비어있으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", uniqueUsername(), "password", "pw1234", "nickname", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 등록한_아이디_비밀번호로_로그인하면_토큰을_받는다() throws Exception {
        String username = uniqueUsername();
        register(username, "pw1234");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", "pw1234"))))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains(username));
        assertTrue(objectMapper.readTree(body).get("token").asText().length() > 0);
        assertEquals(TEST_NICKNAME, objectMapper.readTree(body).get("nickname").asText());
    }

    @Test
    void 비밀번호가_틀리면_401을_반환한다() throws Exception {
        String username = uniqueUsername();
        register(username, "pw1234");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", "wrongpw"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 존재하지_않는_아이디로_로그인하면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", uniqueUsername(), "password", "pw1234"))))
                .andExpect(status().isUnauthorized());
    }
}
