package com.lbg0146.backend.room;

import com.lbg0146.backend.card.Card;
import com.lbg0146.backend.card.Deck;
import com.lbg0146.backend.exception.GameStateException;
import com.lbg0146.backend.game.ShowdownResult;
import com.lbg0146.backend.player.Player;
import com.lbg0146.backend.player.PlayerStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    // 전원 폴드로 핸드가 조기 종료됐는지 여부. 이 경우 실제 쇼다운(카드 비교)이 없었으므로
    // 승자의 홀카드를 공개하지 않는다 — RoomStateMapper의 쇼다운 공개 규칙에서 참조한다.
    private boolean wonByFold;
    // 가장 최근 쇼다운 결과(족보/승자). 다음 핸드가 시작되기 전까지 유지되며, 그 사이 조회 응답에 노출된다.
    private ShowdownResult lastShowdownResult;
    // 폴드로 종료된 핸드의 승자 id(wonByFold=true일 때만 의미 있음). 그 승자가 자원해서 카드를
    // 공개할 수 있는 대상을 판단하는 데 쓰인다.
    private String foldWinWinnerId;
    // 이번 핸드에서 "기본 공개 규칙과 무관하게" 카드가 보이기로 확정된 플레이어 id 모음 —
    // 폴드승 승자가 자원 공개를 선택한 경우, 그리고 헤즈업 쇼다운에서 무작위로 뽑혀 자동
    // 공개되거나 상대가 공개를 선택(혹은 시간 초과로 강제 공개)한 경우 여기 추가된다.
    // 다음 핸드가 시작되면 비워진다.
    private final Set<String> voluntarilyRevealedIds = new HashSet<>();
    // 이번 쇼다운이 정확히 2명(헤즈업)이 겨루는 특수 흐름을 탔는지 — 이 경우 "쇼다운에서 폴드 안 한
    // 사람 전원 자동 공개"라는 일반 규칙이 적용되지 않고, voluntarilyRevealedIds로만 공개 여부가
    // 결정된다. 다음 핸드가 시작되면 초기화된다.
    private boolean headsUpShowdown;
    // 헤즈업 쇼다운에서 공개/머크를 결정해야 하는 사람의 id. 아직 결정 전(대기 중)에만 non-null이고,
    // 결정이 끝나면(또는 시간 초과로 강제 처리되면) null로 돌아간다 — "지금 대기 중인지" 자체를
    // 이 값의 null 여부로 판단한다.
    private String headsUpDeciderPlayerId;

    public void addPlayer(Player player) {
        if (players.size() >= MAX_PLAYERS) {
            throw new GameStateException("테이블 정원(" + MAX_PLAYERS + "명)이 가득 찼습니다.");
        }
        players.add(player);
    }

    // 딜러 버튼을 다음 좌석(players 리스트 순서 기준)으로 이동한다. 파산(BUSTED)한 좌석은
    // 건너뛰고, 그다음으로 만나는 파산하지 않은 좌석에 버튼이 온다.
    public void moveButtonToNextSeat() {
        if (players.isEmpty()) {
            throw new GameStateException("좌석에 플레이어가 없습니다.");
        }
        int next = dealerButtonPosition;
        for (int i = 0; i < players.size(); i++) {
            next = (next + 1) % players.size();
            if (players.get(next).getStatus() != PlayerStatus.BUSTED) {
                dealerButtonPosition = next;
                return;
            }
        }
        // 파산하지 않은 좌석이 하나도 없는 비정상 상황 — 호출 측(GameEngine.startHand)이 이미
        // 칩 있는 플레이어 2명 이상을 검증하므로 실제로는 일어나지 않는다. 방어적으로만 처리한다.
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

    public boolean isWonByFold() {
        return wonByFold;
    }

    public void setWonByFold(boolean wonByFold) {
        this.wonByFold = wonByFold;
    }

    public ShowdownResult getLastShowdownResult() {
        return lastShowdownResult;
    }

    public void setLastShowdownResult(ShowdownResult lastShowdownResult) {
        this.lastShowdownResult = lastShowdownResult;
    }

    public String getFoldWinWinnerId() {
        return foldWinWinnerId;
    }

    public void setFoldWinWinnerId(String foldWinWinnerId) {
        this.foldWinWinnerId = foldWinWinnerId;
    }

    public Set<String> getVoluntarilyRevealedIds() {
        return voluntarilyRevealedIds;
    }

    public boolean isHeadsUpShowdown() {
        return headsUpShowdown;
    }

    public void setHeadsUpShowdown(boolean headsUpShowdown) {
        this.headsUpShowdown = headsUpShowdown;
    }

    public String getHeadsUpDeciderPlayerId() {
        return headsUpDeciderPlayerId;
    }

    public void setHeadsUpDeciderPlayerId(String headsUpDeciderPlayerId) {
        this.headsUpDeciderPlayerId = headsUpDeciderPlayerId;
    }
}
