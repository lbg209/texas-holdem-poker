package com.lbg0146.backend.player;

import com.lbg0146.backend.card.Card;

import java.util.ArrayList;
import java.util.List;

public class Player {

    private final String id;
    private final String nickname;

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

    public Player(String id, String nickname, int chips) {
        this.id = id;
        this.nickname = nickname;
        this.chips = chips;
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
}
