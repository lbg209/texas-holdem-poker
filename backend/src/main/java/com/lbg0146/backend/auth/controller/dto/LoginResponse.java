package com.lbg0146.backend.auth.controller.dto;

public record LoginResponse(Long userId, String username, String nickname, String token) {
}
