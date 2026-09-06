package com.lbg0146.backend.websocket.dto;

public record ErrorMessage(String type, String message) {

    public ErrorMessage(String message) {
        this("ERROR", message);
    }
}
