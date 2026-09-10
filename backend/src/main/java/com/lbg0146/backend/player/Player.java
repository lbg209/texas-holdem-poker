package com.lbg0146.backend.player;

import com.lbg0146.backend.card.Card;

import java.util.ArrayList;
import java.util.List;

public class Player {

    private final String id;
    private final String nickname;
    // 로그인 계정으로 입장했을 때만 값이 있다(게스트는 null). 같은 계정이 방에 중복으로 앉는 걸
    // 막는 데 쓰인다(Room.addPlayer 참고) — playerId는 매 입장마다 새로 발급되므로 이 값이 없으면
    // "같은 사람"인지 구분할 방법이 없다.
    private final Long accountUserId;

    private int chips;
    private final List<Card> holeCards = new ArrayList<>();
    private PlayerStatus status = PlayerStatus.ACTIVE;

    // 이번 스트리트(베팅 라운드)에서 낸 금액. 새 스트리트가 시작되면 0으로 리셋된다.
    private int currentRoundBet;
    // 이번 핸드 전체(프리플랍~리버)에서 낸 누적 금액. 핸드가 끝날 때까지 유지되며 사이드팟 계산에 쓰인다.
    private int totalHandContribution;
    // 이번 스트리트에서 마지막으로 취한 액션. currentRoundBet과 함께 새 스트리트 시작 시 초기화된다.
    private PlayerAction lastAction;
    // 이번 핸드가 시작되기 직전(블라인드 걷기 전)의 칩 보유량. 핸드 종료 후 손익(chips - chipsAtHandStart)을
    // 계산하는 기준점이 된다.
    private int chipsAtHandStart;
    // 다음 핸드 자동 시작에 동의했는지. 핸드 진행 여부와 무관하게 언제든 토글 가능하며,
    // resetForNewHand()가 초기화하지 않는다 — 한 번 켜두면 계속 유지된다(다음 핸드에도, 그 다음
    // 핸드에도). 잠깐 자리를 비우고 싶으면 직접 꺼야 한다.
    private boolean ready;
    // 이번 핸드에서 턴 타임아웃으로 자동 폴드됐는지(직접 FOLD를 누른 게 아니라). resetForNewHand()가
    // 매 핸드 초기화한다 — status(FOLDED)와 달리 "왜 폴드됐는지"만 구분하는 부가 정보다.
    private boolean autoFolded;
    // "나가기" 예약 여부. true가 되면 핸드 진행 중이 아닐 때는 즉시, 진행 중이면 이번 핸드가 끝나는
    // 즉시 방에서 제거된다(GameEngine.requestLeave/removeLeavingPlayers 참고). 한 번 제거되면
    // Player 자체가 목록에서 사라지므로 이 플래그를 다시 초기화할 일은 없다.
    private boolean leaving;

    public Player(String id, String nickname, int chips) {
        this(id, nickname, chips, null);
    }

    public Player(String id, String nickname, int chips, Long accountUserId) {
        this.id = id;
        this.nickname = nickname;
        this.chips = chips;
        this.accountUserId = accountUserId;
    }

    // 칩을 판에 넣는다. 칩이 요청 금액보다 적으면 가진 만큼만 내고(올인), 잔여 칩이 0이 되면
    // 자동으로 ALL_IN 상태로 전환한다.
    public void commitChips(int amount) {
        int actual = Math.min(amount, chips);
        chips -= actual;
        currentRoundBet += actual;
        totalHandContribution += actual;
        if (chips == 0) {
            status = PlayerStatus.ALL_IN;
        }
    }

    public void fold() {
        status = PlayerStatus.FOLDED;
    }

    public void addChips(int amount) {
        chips += amount;
    }

    // GAME OVER 15초 후 리매치를 위해 칩을 특정 값으로 강제 설정한다(딜/베팅 도중 쓰는
    // commitChips/addChips와 달리 증감이 아니라 절대값 지정). Room.resetForRematch()에서만 쓴다.
    public void resetChips(int chips) {
        this.chips = chips;
    }

    public void recordAction(PlayerAction action) {
        lastAction = action;
    }

    public void resetForNewRound() {
        currentRoundBet = 0;
        lastAction = null;
    }

    public void resetForNewHand() {
        // 칩이 없으면 이번 핸드부터 파산 상태다 — ACTIVE로 되돌리지 않는다.
        status = chips > 0 ? PlayerStatus.ACTIVE : PlayerStatus.BUSTED;
        currentRoundBet = 0;
        totalHandContribution = 0;
        lastAction = null;
        chipsAtHandStart = chips;
        holeCards.clear();
        autoFolded = false;
    }

    public void receiveHoleCard(Card card) {
        holeCards.add(card);
    }

    public String getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public Long getAccountUserId() {
        return accountUserId;
    }

    public int getChips() {
        return chips;
    }

    public List<Card> getHoleCards() {
        return holeCards;
    }

    public PlayerStatus getStatus() {
        return status;
    }

    public int getCurrentRoundBet() {
        return currentRoundBet;
    }

    public int getTotalHandContribution() {
        return totalHandContribution;
    }

    public PlayerAction getLastAction() {
        return lastAction;
    }

    public int getChipsAtHandStart() {
        return chipsAtHandStart;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public boolean isAutoFolded() {
        return autoFolded;
    }

    public void markAutoFolded() {
        this.autoFolded = true;
    }

    public boolean isLeaving() {
        return leaving;
    }

    public void setLeaving(boolean leaving) {
        this.leaving = leaving;
    }
}
