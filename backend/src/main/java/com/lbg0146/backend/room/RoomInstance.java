package com.lbg0146.backend.room;

import com.lbg0146.backend.game.GameEngine;
import com.lbg0146.backend.websocket.AutoStartService;
import com.lbg0146.backend.websocket.GameOverResetTimerService;
import com.lbg0146.backend.websocket.HeadsUpRevealTimerService;
import com.lbg0146.backend.websocket.LeaveProcessingTimerService;
import com.lbg0146.backend.websocket.RoomBroadcaster;
import com.lbg0146.backend.websocket.TurnTimerService;
import tools.jackson.databind.ObjectMapper;

// 방 하나가 실제로 동작하는 데 필요한 것들(게임 엔진 + WebSocket 브로드캐스터 + 5개 스케줄러)을
// 한 세트로 묶는다. 예전에는 이 전부가 Spring 싱글톤 빈이라 서버 전체에 방이 하나뿐이었는데,
// 멀티룸으로 확장하면서 방마다 이 세트를 통째로 새로 만들어야 한다 — 스케줄러들은 "지금 예약되어
// 있는지" 같은 상태를 자체적으로 들고 있어서 방끼리 공유하면 안 된다. RoomManager가 방을 만들 때마다
// 이 클래스를 하나씩 생성해서 들고 있는다.
public class RoomInstance {

    private final Room room;
    private final GameEngine gameEngine;
    private final TurnTimerService turnTimerService = new TurnTimerService();
    private final AutoStartService autoStartService = new AutoStartService();
    private final HeadsUpRevealTimerService headsUpRevealTimerService = new HeadsUpRevealTimerService();
    private final GameOverResetTimerService gameOverResetTimerService = new GameOverResetTimerService();
    private final LeaveProcessingTimerService leaveProcessingTimerService = new LeaveProcessingTimerService();
    private final RoomBroadcaster broadcaster;

    // onEmpty는 방에 아무도 안 남게 되는 순간(즉시 나가기든, 3초 유예 후 나가기든) 호출된다 —
    // RoomManager가 자기 관리 목록에서 이 방을 지우는 데 쓴다.
    public RoomInstance(Room room, ObjectMapper objectMapper, Runnable onEmpty) {
        this.room = room;
        this.gameEngine = new GameEngine(room);
        this.broadcaster = new RoomBroadcaster(gameEngine, objectMapper, turnTimerService, autoStartService,
                headsUpRevealTimerService, gameOverResetTimerService, leaveProcessingTimerService, onEmpty);
    }

    public Room getRoom() {
        return room;
    }

    public GameEngine getGameEngine() {
        return gameEngine;
    }

    public RoomBroadcaster getBroadcaster() {
        return broadcaster;
    }

    public TurnTimerService getTurnTimerService() {
        return turnTimerService;
    }

    public AutoStartService getAutoStartService() {
        return autoStartService;
    }

    public HeadsUpRevealTimerService getHeadsUpRevealTimerService() {
        return headsUpRevealTimerService;
    }

    public GameOverResetTimerService getGameOverResetTimerService() {
        return gameOverResetTimerService;
    }
}
