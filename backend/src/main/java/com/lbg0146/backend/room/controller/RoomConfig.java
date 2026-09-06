package com.lbg0146.backend.room.controller;

import com.lbg0146.backend.game.GameEngine;
import com.lbg0146.backend.room.Room;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// MVP는 "단일 고정 테이블"이라 Room/GameEngine을 서버 전체에서 하나씩만 쓰는 싱글톤 빈으로 둔다.
// 나중에 멀티룸으로 확장하면 이 빈 등록 대신 RoomManager 등이 roomCode별로 Room/GameEngine을
// 관리하게 되는데, Room/GameEngine 자체에는 "싱글톤"을 가정한 코드가 없어 이 설정만 바꾸면 된다.
@Configuration
public class RoomConfig {

    @Bean
    public Room room() {
        return new Room();
    }

    @Bean
    public GameEngine gameEngine(Room room) {
        return new GameEngine(room);
    }
}
