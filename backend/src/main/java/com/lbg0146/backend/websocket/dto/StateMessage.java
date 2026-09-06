package com.lbg0146.backend.websocket.dto;

import com.lbg0146.backend.room.controller.dto.RoomStateResponse;

public record StateMessage(String type, RoomStateResponse state) {

    public StateMessage(RoomStateResponse state) {
        this("STATE", state);
    }
}
