package com.lbg0146.backend.room.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinPlayerRequest(@NotBlank(message = "닉네임은 비어 있을 수 없습니다.") String nickname) {
}
