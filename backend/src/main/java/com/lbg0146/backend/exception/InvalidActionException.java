package com.lbg0146.backend.exception;

// 플레이어가 시도한 액션(CHECK/CALL/BET/RAISE/FOLD/ALL_IN)이 현재 베팅 상태에서 규칙상 허용되지 않을 때 발생한다.
public class InvalidActionException extends PokerException {

    public InvalidActionException(String message) {
        super(message);
    }
}
