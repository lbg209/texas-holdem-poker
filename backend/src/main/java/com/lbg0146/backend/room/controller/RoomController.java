package com.lbg0146.backend.room.controller;

import com.lbg0146.backend.game.GameEngine;
import com.lbg0146.backend.player.Player;
import com.lbg0146.backend.room.Room;
import com.lbg0146.backend.room.controller.dto.JoinPlayerRequest;
import com.lbg0146.backend.room.controller.dto.JoinPlayerResponse;
import com.lbg0146.backend.room.controller.dto.RoomStateResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/room")
public class RoomController {

    private final GameEngine gameEngine;

    public RoomController(GameEngine gameEngine) {
        this.gameEngine = gameEngine;
    }

    // playerId를 안 주면 관전자 시점(아무 홀카드도 안 보임)으로 조회된다.
    @GetMapping
    public RoomStateResponse getRoomState(@RequestParam(required = false) String playerId) {
        return RoomStateMapper.toResponse(gameEngine, playerId);
    }

    @PostMapping("/players")
    public ResponseEntity<JoinPlayerResponse> joinRoom(@Valid @RequestBody JoinPlayerRequest request) {
        // 로그인/인증이 없는 MVP라 서버가 UUID를 임시 식별자로 발급한다.
        String playerId = UUID.randomUUID().toString();
        gameEngine.getRoom().addPlayer(new Player(playerId, request.nickname(), Room.STARTING_CHIPS));
        return ResponseEntity.status(HttpStatus.CREATED).body(new JoinPlayerResponse(playerId));
    }

    @PostMapping("/hands")
    public RoomStateResponse startHand() {
        gameEngine.startHand();
        return RoomStateMapper.toResponse(gameEngine, null);
    }
}
