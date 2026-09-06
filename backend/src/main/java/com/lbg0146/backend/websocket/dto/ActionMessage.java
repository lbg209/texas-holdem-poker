package com.lbg0146.backend.websocket.dto;

import com.lbg0146.backend.player.PlayerAction;

// 클라이언트가 보내는 메시지의 공통 봉투. type이 "ACTION"이 아니면 action/amount는 사용하지 않는다.
public record ActionMessage(String type, PlayerAction action, int amount) {
}
