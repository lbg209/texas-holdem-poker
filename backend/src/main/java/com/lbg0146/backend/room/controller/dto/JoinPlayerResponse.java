package com.lbg0146.backend.room.controller.dto;

// playerId는 로그인/인증이 없는 지금 단계의 임시 식별자다. 이후 인증이 추가되면 이 발급 방식만
// 바뀌면 되도록, 이 값을 도메인 로직(Room/GameEngine 등)과는 REST 계층에서만 다룬다.
public record JoinPlayerResponse(String playerId) {
}
