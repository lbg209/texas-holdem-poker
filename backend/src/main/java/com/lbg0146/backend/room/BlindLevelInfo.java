package com.lbg0146.backend.room;

// 블라인드 구조표 한 줄(레벨 1개). Room.getBlindStructure()가 전체 스케줄(고정 6단계)을 미리
// 계산해서 돌려줄 때 쓰는 값 객체 — 지금 몇 레벨인지와 무관하게 스케줄 전체를 한 번에 보여줄 수 있다.
public record BlindLevelInfo(int level, int smallBlind, int bigBlind, int ante) {
}
