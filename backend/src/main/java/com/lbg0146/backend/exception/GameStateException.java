package com.lbg0146.backend.exception;

// 방/핸드가 요청받은 동작을 처리할 수 있는 상태가 아닐 때 발생한다 (예: 정원 초과, 최소 인원 미달, 잘못된 단계 전환).
public class GameStateException extends PokerException {

    public GameStateException(String message) {
        super(message);
    }
}
