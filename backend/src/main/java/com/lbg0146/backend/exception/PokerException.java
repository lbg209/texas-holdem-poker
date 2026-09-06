package com.lbg0146.backend.exception;

// 게임 규칙/상태 위반을 나타내는 도메인 예외의 공통 부모.
// 통신 계층(REST/WebSocket)에서 이 타입 하나로 잡아 사용자에게 보여줄 에러 메시지로 변환할 수 있다.
public class PokerException extends RuntimeException {

    public PokerException(String message) {
        super(message);
    }
}
