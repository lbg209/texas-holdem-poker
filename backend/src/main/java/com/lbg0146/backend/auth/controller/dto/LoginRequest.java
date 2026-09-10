package com.lbg0146.backend.auth.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "아이디는 비어 있을 수 없습니다.") String username,
        @NotBlank(message = "비밀번호는 비어 있을 수 없습니다.") String password
) {
}
