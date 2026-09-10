package com.lbg0146.backend.auth;

public record LoginResult(Long userId, String username, String nickname, String token) {
}
