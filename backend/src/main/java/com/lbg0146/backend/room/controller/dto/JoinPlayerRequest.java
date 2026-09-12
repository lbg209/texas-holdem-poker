package com.lbg0146.backend.room.controller.dto;

// nickname은 게스트 입장 시에만 쓰인다(로그인 사용자는 가입 시 정한 계정 닉네임을 그대로 쓰므로
// 무시된다). authToken이 없거나 유효하지 않으면 게스트로 처리된다. password는 비공개방에 입장할
// 때만 필요하다(공개방은 무시된다). seatIndex는 고정 좌석제 도입 후 프론트가 클릭한 좌석 번호를
// 보낸다 — null이면(지정 안 하면) 빈 좌석 중 가장 낮은 번호에 자동으로 앉는다(주로 테스트/방 만든
// 직후 혼자 들어가는 경우처럼 굳이 고를 필요가 없을 때).
public record JoinPlayerRequest(String nickname, String authToken, String password, Integer seatIndex) {
    public JoinPlayerRequest(String nickname, String authToken, String password) {
        this(nickname, authToken, password, null);
    }
}
