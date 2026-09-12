package com.lbg0146.backend.room.controller;

import com.lbg0146.backend.auth.AuthService;
import com.lbg0146.backend.auth.User;
import com.lbg0146.backend.exception.InvalidActionException;
import com.lbg0146.backend.player.Player;
import com.lbg0146.backend.room.Room;
import com.lbg0146.backend.room.RoomInstance;
import com.lbg0146.backend.room.RoomManager;
import com.lbg0146.backend.room.controller.dto.CreateRoomRequest;
import com.lbg0146.backend.room.controller.dto.CreateRoomResponse;
import com.lbg0146.backend.room.controller.dto.HandHistoryEntryView;
import com.lbg0146.backend.room.controller.dto.JoinPlayerRequest;
import com.lbg0146.backend.room.controller.dto.JoinPlayerResponse;
import com.lbg0146.backend.room.controller.dto.RoomStateResponse;
import com.lbg0146.backend.room.controller.dto.RoomSummaryView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomManager roomManager;
    private final AuthService authService;

    public RoomController(RoomManager roomManager, AuthService authService) {
        this.roomManager = roomManager;
        this.authService = authService;
    }

    // 로비 방 목록. 비공개방도 포함된다(자물쇠 표시는 프론트엔드가 isPrivate로 판단) —
    // 보호는 입장 시 비밀번호 검사가 담당한다.
    @GetMapping
    public List<RoomSummaryView> listRooms() {
        return roomManager.listRooms().stream().map(RoomSummaryView::from).toList();
    }

    // 로비의 "방 만들기" 팝업이 호출한다. 방을 만들기만 하고 만든 사람을 자동으로 입장시키지는
    // 않는다 — 반환된 roomCode로 별도로 POST /api/rooms/{roomCode}/players를 호출해야 실제로 앉는다.
    @PostMapping
    public ResponseEntity<CreateRoomResponse> createRoom(@RequestBody CreateRoomRequest request) {
        String roomCode = roomManager.createRoom(request.name(), request.isPrivate(), request.password(),
                request.startingChips(), request.bigBlind());
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateRoomResponse(roomCode));
    }

    // 로비에서 방을 클릭했을 때(또는 "코드로 입장"으로 코드를 입력했을 때) 왼쪽 정보 패널에 보여줄
    // 상세 정보. playerId를 안 주면 관전자 시점(아무 홀카드도 안 보임)으로 조회된다.
    @GetMapping("/{roomCode}")
    public RoomStateResponse getRoomState(@PathVariable String roomCode,
            @RequestParam(required = false) String playerId) {
        RoomInstance instance = roomManager.findRoom(roomCode);
        return instance.getGameEngine().withLock(() -> buildResponse(instance, playerId));
    }

    // 로비 목록에서 방을 클릭해 좌측 정보 패널로 들어오는 입장. 비공개방이면 비밀번호를 확인한다.
    @PostMapping("/{roomCode}/players")
    public ResponseEntity<JoinPlayerResponse> joinRoom(@PathVariable String roomCode,
            @RequestBody JoinPlayerRequest request) {
        RoomInstance instance = roomManager.findRoom(roomCode);
        Room room = instance.getGameEngine().getRoom();
        if (room.getPassword() != null && !room.getPassword().equals(request.password())) {
            throw new InvalidActionException("비밀번호가 올바르지 않습니다.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(doJoin(instance, request));
    }

    // "코드로 입장" — roomCode 자체를 직접 입력해서 들어오는 경우는 비밀번호 검사를 생략한다.
    // (roomCode를 안다는 것 자체가 초대로 간주된다. 목록 API가 어차피 모든 방의 roomCode를
    // 내려주므로 이건 암호학적으로 안전한 보호가 아니라 "실수로/장난으로 들어오는 것"만 막는
    // 수준의 보호라는 걸 감안한 선택 — 사용자와 상의해서 확정함.)
    @PostMapping("/{roomCode}/players/by-code")
    public ResponseEntity<JoinPlayerResponse> joinRoomByCode(@PathVariable String roomCode,
            @RequestBody JoinPlayerRequest request) {
        RoomInstance instance = roomManager.findRoom(roomCode);
        return ResponseEntity.status(HttpStatus.CREATED).body(doJoin(instance, request));
    }

    private JoinPlayerResponse doJoin(RoomInstance instance, JoinPlayerRequest request) {
        // Room/Player는 로그인 여부와 무관하게 매 입장마다 새로 발급되는 임시 식별자다(계정과
        // playerId는 별개 — 계정은 로그인 상태/닉네임만 결정하고, 게임 참여 자체는 지금처럼 진행됨).
        String playerId = UUID.randomUUID().toString();
        // 로그인 사용자는 가입 시 한 번 정한 닉네임을 그대로 쓴다(요청의 nickname은 무시) —
        // 게스트처럼 입장할 때마다 다시 입력하지 않는다. 게스트만 직접 입력한 닉네임 뒤에
        // "(Guest)"가 자동으로 붙는다. accountUserId가 있으면 Room.addPlayer가 같은 계정의 중복
        // 입장(다른 브라우저/탭에서 또 앉는 것)을 막아준다.
        Optional<User> user = authService.resolveUser(request.authToken());
        String nickname = user.map(User::getNickname).orElseGet(() -> guestNickname(request.nickname()));
        Player player = new Player(playerId, nickname, instance.getGameEngine().getRoom().getStartingChips(),
                user.map(User::getId).orElse(null));
        if (request.seatIndex() != null) {
            instance.getGameEngine().addPlayer(player, request.seatIndex());
        } else {
            instance.getGameEngine().addPlayer(player);
        }
        instance.getBroadcaster().broadcastState();
        return new JoinPlayerResponse(playerId);
    }

    private static String guestNickname(String rawNickname) {
        if (rawNickname == null || rawNickname.isBlank()) {
            throw new InvalidActionException("닉네임은 비어 있을 수 없습니다.");
        }
        return rawNickname + " (Guest)";
    }

    // playerId를 안 주면 관전자 시점으로 응답한다.
    // 프론트엔드에는 더 이상 수동 "핸드 시작" 버튼이 없지만(레디 시스템으로 대체), 엔드포인트
    // 자체는 디버깅/관리 목적으로 남겨둔다.
    @PostMapping("/{roomCode}/hands")
    public RoomStateResponse startHand(@PathVariable String roomCode,
            @RequestParam(required = false) String playerId) {
        RoomInstance instance = roomManager.findRoom(roomCode);
        instance.getGameEngine().startHand();
        instance.getBroadcaster().broadcastState();
        return instance.getGameEngine().withLock(() -> buildResponse(instance, playerId));
    }

    // 다음 핸드 자동 시작에 동의하는지 토글한다. 핸드 진행 중에도 언제든 호출할 수 있다 —
    // 이번 핸드에는 영향을 주지 않고, 다음 핸드가 자동으로 시작될지에만 반영된다.
    @PostMapping("/{roomCode}/ready")
    public RoomStateResponse setReady(@PathVariable String roomCode, @RequestParam String playerId,
            @RequestParam boolean ready) {
        RoomInstance instance = roomManager.findRoom(roomCode);
        instance.getGameEngine().setReady(playerId, ready);
        instance.getBroadcaster().broadcastState();
        return instance.getGameEngine().withLock(() -> buildResponse(instance, playerId));
    }

    // "나가기"를 예약(leaving=true)하거나 취소(leaving=false)한다. 핸드 진행 중이 아니면 즉시
    // 방에서 제거되고, 진행 중이면 이번 핸드가 끝나는 시점에 제거된다.
    @PostMapping("/{roomCode}/leave")
    public RoomStateResponse requestLeave(@PathVariable String roomCode, @RequestParam String playerId,
            @RequestParam boolean leaving) {
        RoomInstance instance = roomManager.findRoom(roomCode);
        instance.getGameEngine().requestLeave(playerId, leaving);
        instance.getBroadcaster().broadcastState();
        return instance.getGameEngine().withLock(() -> buildResponse(instance, playerId));
    }

    // 이 방의 최근 핸드 히스토리(최대 30개, 최신순). DB에 저장하지 않고 방이 사라지면 같이
    // 사라진다. playerId를 주면 본인 카드 + 그 핸드에서 실제로 공개됐던 카드만 보이고, 생략하면
    // 관전자 시점(아무도 공개 안 한 카드는 전부 숨김)으로 내려간다.
    @GetMapping("/{roomCode}/history")
    public List<HandHistoryEntryView> getHandHistory(@PathVariable String roomCode,
            @RequestParam(required = false) String playerId) {
        RoomInstance instance = roomManager.findRoom(roomCode);
        return instance.getGameEngine()
                .withLock(() -> HandHistoryMapper.toResponse(instance.getGameEngine().getRoom(), playerId));
    }

    // 방장이 레디 안 한 플레이어를 강퇴한다(핸드 진행 중이거나, 대상이 레디했거나, 레디 안 한 지
    // 3초가 안 지났으면 거부됨). 남은 시간은 응답에 노출하지 않는다 — 너무 일찍 시도하면 그냥 실패한다.
    @PostMapping("/{roomCode}/players/{targetId}/kick")
    public RoomStateResponse kickPlayer(@PathVariable String roomCode, @PathVariable String targetId,
            @RequestParam String requesterId) {
        RoomInstance instance = roomManager.findRoom(roomCode);
        instance.getGameEngine().kickPlayer(requesterId, targetId);
        instance.getBroadcaster().broadcastState();
        return instance.getGameEngine().withLock(() -> buildResponse(instance, requesterId));
    }

    // 이미 앉아있는 플레이어가 다른 빈 좌석으로 옮긴다. 핸드 진행 중이거나, 너무 빠르게 연속으로
    // 옮기려 하거나, 그 좌석이 이미 차 있으면 거부된다.
    @PostMapping("/{roomCode}/players/{playerId}/seat")
    public RoomStateResponse moveSeat(@PathVariable String roomCode, @PathVariable String playerId,
            @RequestParam int seatIndex) {
        RoomInstance instance = roomManager.findRoom(roomCode);
        instance.getGameEngine().requestSeatMove(playerId, seatIndex);
        instance.getBroadcaster().broadcastState();
        return instance.getGameEngine().withLock(() -> buildResponse(instance, playerId));
    }

    // 폴드로 종료된 핸드의 승자가 자원해서 자기 카드를 공개한다.
    @PostMapping("/{roomCode}/reveal")
    public RoomStateResponse revealFoldWinHand(@PathVariable String roomCode, @RequestParam String playerId) {
        RoomInstance instance = roomManager.findRoom(roomCode);
        instance.getGameEngine().revealFoldWinHand(playerId);
        instance.getBroadcaster().broadcastState();
        return instance.getGameEngine().withLock(() -> buildResponse(instance, playerId));
    }

    // 헤즈업 쇼다운에서 결정자가 공개(reveal=true) 또는 머크(reveal=false)를 선택한다.
    @PostMapping("/{roomCode}/showdown-decision")
    public RoomStateResponse decideHeadsUpReveal(@PathVariable String roomCode, @RequestParam String playerId,
            @RequestParam boolean reveal) {
        RoomInstance instance = roomManager.findRoom(roomCode);
        instance.getGameEngine().decideHeadsUpReveal(playerId, reveal);
        instance.getBroadcaster().broadcastState();
        return instance.getGameEngine().withLock(() -> buildResponse(instance, playerId));
    }

    private static RoomStateResponse buildResponse(RoomInstance instance, String playerId) {
        return RoomStateMapper.toResponse(instance.getGameEngine(), playerId,
                instance.getTurnTimerService().getCurrentDeadlineMillis(),
                instance.getAutoStartService().getCurrentDeadlineMillis(),
                instance.getHeadsUpRevealTimerService().getCurrentDeadlineMillis(),
                instance.getGameOverResetTimerService().getCurrentDeadlineMillis());
    }
}
