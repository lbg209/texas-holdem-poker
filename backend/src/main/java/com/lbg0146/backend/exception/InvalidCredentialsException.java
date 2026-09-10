package com.lbg0146.backend.exception;

// 로그인 시 아이디 또는 비밀번호가 일치하지 않을 때 발생한다.
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
