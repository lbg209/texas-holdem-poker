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

    public void resetForNewRound() {
        currentRoundBet = 0;
    }

    public void resetForNewHand() {
        status = PlayerStatus.ACTIVE;
        currentRoundBet = 0;
        totalHandContribution = 0;
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
}
