package com.lbg0146.backend.room;

import com.lbg0146.backend.card.Card;
import com.lbg0146.backend.card.Deck;
import com.lbg0146.backend.exception.GameStateException;
import com.lbg0146.backend.player.Player;

import java.util.ArrayList;
import java.util.List;

// 단일 고정 테이블의 상태를 담는 객체. 게임 진행/계산 로직은 game 패키지(GameEngine 등)가 담당한다.
public class Room {

    public static final int SMALL_BLIND = 100;
    public static final int BIG_BLIND = 200;
    public static final int STARTING_CHIPS = 30_000;
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 6;
    // BET/RAISE 금액은 이 단위의 배수여야 한다. 단, 보유 칩 전부를 거는 경우(사실상 올인)는 예외로 허용한다.
    public static final int BET_UNIT = 100;

    private final List<Player> players = new ArrayList<>();
    private final List<Card> communityCards = new ArrayList<>();
    private List<Pot> pots = new ArrayList<>();
    private Deck deck = new Deck();
    private Phase phase;
    private int dealerButtonPosition = -1;

    public void addPlayer(Player player) {
        if (players.size() >= MAX_PLAYERS) {
            throw new GameStateException("테이블 정원(" + MAX_PLAYERS + "명)이 가득 찼습니다.");
        }
        players.add(player);
    }

    // 딜러 버튼을 다음 좌석(players 리스트 순서 기준)으로 이동한다.
    public void moveButtonToNextSeat() {
        if (players.isEmpty()) {
            throw new GameStateException("좌석에 플레이어가 없습니다.");
        }
        dealerButtonPosition = (dealerButtonPosition + 1) % players.size();
    }

    public Player findPlayer(String playerId) {
        return players.stream()
                .filter(p -> p.getId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 플레이어: " + playerId));
    }

    public List<Player> getPlayers() {
        return players;
    }

    public List<Card> getCommunityCards() {
        return communityCards;
    }

    public List<Pot> getPots() {
        return pots;
    }

    public void setPots(List<Pot> pots) {
        this.pots = pots;
    }

    public Deck getDeck() {
        return deck;
    }

    public void resetDeck() {
        this.deck = new Deck();
    }

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public int getDealerButtonPosition() {
        return dealerButtonPosition;
    }
}
