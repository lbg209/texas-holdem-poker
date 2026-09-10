package com.lbg0146.backend.exception;

// 이미 사용 중인 username으로 회원가입을 시도했을 때 발생한다.
public class DuplicateUsernameException extends RuntimeException {

    public DuplicateUsernameException(String message) {
        super(message);
    }
}
