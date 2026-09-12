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
    // 마지막으로 "레디 안 함" 상태가 된 시각(ms). 방장 강퇴 가능 여부(일정 시간 이상 레디 안 함) 판단에
    // 쓰인다. ready가 true가 되면 0으로 리셋되고(강퇴 대상 아님), false가 될 때마다(생성 직후 포함)
    // 다시 그 시점으로 갱신된다.
    private long notReadySince;
    // 앉아있는 물리적 좌석 번호(0~Room.MAX_PLAYERS-1). Room.addPlayer()가 배정하고,
    // Room.moveSeat()가 자리 이동 시 바꾼다 — Room.players 리스트는 항상 이 값 기준 오름차순으로
    // 정렬된 상태를 유지해서, 버튼 이동/블라인드 순서 등 기존의 "리스트 순서 = 좌석 순서" 로직을
    // 그대로 재사용할 수 있게 한다.
    private int seatIndex = -1;
    // 이 방에 처음 입장한 시각(ms). 방장 승계(가장 오래 앉아있는 사람) 판단 기준 — seatIndex와
    // 달리 자리를 옮겨도 바뀌지 않는다(리스트 순서만으로는 더 이상 입장 순서를 알 수 없으므로 별도로
    // 들고 있어야 한다).
    private final long joinedAtMillis;
    // 마지막으로 좌석을 옮긴 시각(ms). 너무 빠르게 연속으로 자리를 옮기는 것을 막는 데 쓰인다
    // (GameEngine.requestSeatMove 참고). 기본값 0이라 첫 이동은 항상 허용된다.
    private long lastSeatChangeAtMillis;

    public Player(String id, String nickname, int chips) {
        this(id, nickname, chips, null);
    }

    public Player(String id, String nickname, int chips, Long accountUserId) {
        this.id = id;
        this.nickname = nickname;
        this.chips = chips;
        this.accountUserId = accountUserId;
        this.notReadySince = System.currentTimeMillis();
        this.joinedAtMillis = System.currentTimeMillis();
    }

    // 앤티를 낸다 — commitChips와 달리 currentRoundBet에도 totalHandContribution에도 반영하지
    // 않는다. 이 둘은 "베팅 스택 수준"을 나타내는 값이라(사이드팟 계산의 기준), 항상 딱 한 명(빅
    // 블라인드 자리)만 내는 앤티를 섞으면 PotCalculator가 마치 그 사람만 더 많이 올인한 것처럼
    // 오인해서, 그 사람만 가져가는 가짜 사이드팟을 만들어버린다(실사용 중 발견된 버그). 대신
    // 실제로 낸 금액(숏스택이면 모자란 만큼만)을 반환하고, 호출 측(GameEngine)이 Room의 "이번
    // 핸드 걷힌 앤티 총액"에 더해뒀다가 PotCalculator가 메인팟에 통째로 얹는다.
    public int payAnte(int amount) {
        int actual = Math.min(amount, chips);
        chips -= actual;
        if (chips == 0) {
            status = PlayerStatus.ALL_IN;
        }
        return actual;
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
        this.notReadySince = ready ? 0 : System.currentTimeMillis();
    }

    // 레디 안 한 상태로 delayMillis 이상 지났는지 — 방장 강퇴 가능 여부 판단에 쓰인다(GameEngine.kickPlayer).
    public boolean isKickEligible(long delayMillis) {
        return !ready && System.currentTimeMillis() - notReadySince >= delayMillis;
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

    public int getSeatIndex() {
        return seatIndex;
    }

    public void setSeatIndex(int seatIndex) {
        this.seatIndex = seatIndex;
    }

    public long getJoinedAtMillis() {
        return joinedAtMillis;
    }

    // 레디 안 함(isKickEligible)과 동일한 패턴 — 좌석을 옮긴 지 delayMillis 이상 지났는지.
    public boolean isSeatMoveEligible(long delayMillis) {
        return System.currentTimeMillis() - lastSeatChangeAtMillis >= delayMillis;
    }

    public void markSeatChanged() {
        this.lastSeatChangeAtMillis = System.currentTimeMillis();
    }
}
