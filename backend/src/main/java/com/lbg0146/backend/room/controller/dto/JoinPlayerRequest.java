package com.lbg0146.backend.room.controller.dto;

// nickname은 게스트 입장 시에만 쓰인다(로그인 사용자는 가입 시 정한 계정 닉네임을 그대로 쓰므로
// 무시된다). authToken이 없거나 유효하지 않으면 게스트로 처리된다. password는 비공개방에 입장할
// 때만 필요하다(공개방은 무시된다).
public record JoinPlayerRequest(String nickname, String authToken, String password) {
}
