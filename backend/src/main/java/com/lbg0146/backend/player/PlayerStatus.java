package com.lbg0146.backend.player;

public enum PlayerStatus {
    ACTIVE,
    FOLDED,
    ALL_IN,
    // 칩이 0이 되어 더는 핸드에 참여하지 못하는 상태. 새 핸드가 시작될 때 칩이 없는 플레이어는
    // ACTIVE 대신 이 상태가 되고, 홀카드를 받지 않으며 버튼/블라인드/액션 순서에서도 제외된다.
    BUSTED
}
