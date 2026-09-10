package com.lbg0146.backend.auth.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "아이디는 비어 있을 수 없습니다.")
        @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "아이디는 영문/숫자만 사용할 수 있습니다.")
        @Size(min = 3, max = 20, message = "아이디는 3~20자여야 합니다.") String username,
        @NotBlank(message = "비밀번호는 비어 있을 수 없습니다.")
        @Size(min = 4, message = "비밀번호는 4자 이상이어야 합니다.") String password,
        // 방에 표시될 닉네임. 로그인 아이디와 달리 자유롭게(한글 포함) 입력할 수 있고, 가입 시 한 번만
        // 정한다 — 게스트처럼 입장할 때마다 다시 입력하지 않는다.
        @NotBlank(message = "닉네임은 비어 있을 수 없습니다.")
        @Size(max = 20, message = "닉네임은 20자 이하여야 합니다.") String nickname
) {
}
