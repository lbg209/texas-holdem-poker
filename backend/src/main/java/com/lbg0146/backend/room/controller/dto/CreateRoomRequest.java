package com.lbg0146.backend.room.controller.dto;

// 로비의 "방 만들기"가 보내는 요청. isPrivate=false면 password는 무시된다(비공개방만 비밀번호를 쓴다).
// 스몰블라인드는 여기 없다 — 항상 bigBlind의 절반으로 서버가 자동 계산한다(실제 포커 대회 관례).
// 좌석 수는 더 이상 방마다 설정할 수 없다 — 항상 6석 고정이라 여기 없다.
// 값 검증(이름/비밀번호/시작 칩/블라인드)은 RoomManager.createRoom()과 Room.configure()가 담당한다.
public record CreateRoomRequest(
        String name,
        boolean isPrivate,
        String password,
        int startingChips,
        int bigBlind
) {
}
