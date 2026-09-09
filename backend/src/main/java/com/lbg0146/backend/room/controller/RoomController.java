package com.lbg0146.backend.room.controller;

import com.lbg0146.backend.game.GameEngine;
import com.lbg0146.backend.player.Player;
import com.lbg0146.backend.room.Room;
import com.lbg0146.backend.room.controller.dto.JoinPlayerRequest;
import com.lbg0146.backend.room.controller.dto.JoinPlayerResponse;
import com.lbg0146.backend.room.controller.dto.RoomStateResponse;
import com.lbg0146.backend.websocket.AutoStartService;
import com.lbg0146.backend.websocket.HeadsUpRevealTimerService;
import com.lbg0146.backend.websocket.RoomBroadcaster;
import com.lbg0146.backend.websocket.TurnTimerService;
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
    private final RoomBroadcaster broadcaster;
    private final TurnTimerService turnTimerService;
    private final AutoStartService autoStartService;
    private final HeadsUpRevealTimerService headsUpRevealTimerService;

    public RoomController(GameEngine gameEngine, RoomBroadcaster broadcaster, TurnTimerService turnTimerService,
            AutoStartService autoStartService, HeadsUpRevealTimerService headsUpRevealTimerService) {
        this.gameEngine = gameEngine;
        this.broadcaster = broadcaster;
        this.turnTimerService = turnTimerService;
        this.autoStartService = autoStartService;
        this.headsUpRevealTimerService = headsUpRevealTimerService;
    }

    // playerId를 안 주면 관전자 시점(아무 홀카드도 안 보임)으로 조회된다.
    @GetMapping
    public RoomStateResponse getRoomState(@RequestParam(required = false) String playerId) {
        return gameEngine.withLock(() -> buildResponse(playerId));
    }

    @PostMapping("/players")
    public ResponseEntity<JoinPlayerResponse> joinRoom(@Valid @RequestBody JoinPlayerRequest request) {
        // 로그인/인증이 없는 MVP라 서버가 UUID를 임시 식별자로 발급한다.
        String playerId = UUID.randomUUID().toString();
        gameEngine.addPlayer(new Player(playerId, request.nickname(), Room.STARTING_CHIPS));
        broadcaster.broadcastState();
        return ResponseEntity.status(HttpStatus.CREATED).body(new JoinPlayerResponse(playerId));
    }

    // playerId를 안 주면 GET /api/room과 동일하게 관전자 시점으로 응답한다.
    // 프론트엔드에는 더 이상 수동 "핸드 시작" 버튼이 없지만(레디 시스템으로 대체), 엔드포인트
    // 자체는 디버깅/관리 목적으로 남겨둔다.
    @PostMapping("/hands")
    public RoomStateResponse startHand(@RequestParam(required = false) String playerId) {
        gameEngine.startHand();
        broadcaster.broadcastState();
        return gameEngine.withLock(() -> buildResponse(playerId));
    }

    // 다음 핸드 자동 시작에 동의하는지 토글한다. 핸드 진행 중에도 언제든 호출할 수 있다 —
    // 이번 핸드에는 영향을 주지 않고, 다음 핸드가 자동으로 시작될지에만 반영된다.
    @PostMapping("/ready")
    public RoomStateResponse setReady(@RequestParam String playerId, @RequestParam boolean ready) {
        gameEngine.setReady(playerId, ready);
        broadcaster.broadcastState();
        return gameEngine.withLock(() -> buildResponse(playerId));
    }

    // 폴드로 종료된 핸드의 승자가 자원해서 자기 카드를 공개한다.
    @PostMapping("/reveal")
    public RoomStateResponse revealFoldWinHand(@RequestParam String playerId) {
        gameEngine.revealFoldWinHand(playerId);
        broadcaster.broadcastState();
        return gameEngine.withLock(() -> buildResponse(playerId));
    }

    // 헤즈업 쇼다운에서 결정자가 공개(reveal=true) 또는 머크(reveal=false)를 선택한다.
    @PostMapping("/showdown-decision")
    public RoomStateResponse decideHeadsUpReveal(@RequestParam String playerId, @RequestParam boolean reveal) {
        gameEngine.decideHeadsUpReveal(playerId, reveal);
        broadcaster.broadcastState();
        return gameEngine.withLock(() -> buildResponse(playerId));
    }

    private RoomStateResponse buildResponse(String playerId) {
        return RoomStateMapper.toResponse(gameEngine, playerId, turnTimerService.getCurrentDeadlineMillis(),
                autoStartService.getCurrentDeadlineMillis(), headsUpRevealTimerService.getCurrentDeadlineMillis());
    }
}
