package com.lbg0146.backend.room;

import com.lbg0146.backend.card.Card;
import com.lbg0146.backend.card.Deck;
import com.lbg0146.backend.exception.GameStateException;
import com.lbg0146.backend.exception.InvalidActionException;
import com.lbg0146.backend.game.ShowdownResult;
import com.lbg0146.backend.player.Player;
import com.lbg0146.backend.player.PlayerStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 단일 고정 테이블의 상태를 담는 객체. 게임 진행/계산 로직은 game 패키지(GameEngine 등)가 담당한다.
public class Room {

    // 아래 4개는 "기본값"이다 — 방이 비어있을 때 configure()로 바꾸지 않으면 이 값 그대로 쓰인다.
    public static final int SMALL_BLIND = 100;
    public static final int BIG_BLIND = 200;
    public static final int STARTING_CHIPS = 30_000;
    public static final int MAX_PLAYERS = 6;
    // 아래 2개는 방마다 다르게 설정할 수 없는 고정값이다.
    public static final int MIN_PLAYERS = 2;
    // BET/RAISE 금액은 이 단위의 배수여야 한다. 단, 보유 칩 전부를 거는 경우(사실상 올인)는 예외로 허용한다.
    public static final int BET_UNIT = 100;

    // 실제로 이번 방에 적용되는 값. 기본값(위 static final)으로 시작하고, configure()로 방이
    // 비어있을 때만 바꿀 수 있다 — 방장이 "방 만들기" 팝업에서 정한 값이 여기 반영된다.
    private int smallBlind = SMALL_BLIND;
    private int bigBlind = BIG_BLIND;
    private int startingChips = STARTING_CHIPS;
    private int maxPlayers = MAX_PLAYERS;

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
    // GAME OVER(생존자 1명) 여부를 "고정"해서 들고 있는 값. GameEngine.checkGameOver()가 핸드
    // 종료 시점에만 세팅하고, resetForRematch()(15초 카운트다운 만료)가 다시 null로 되돌린다 —
    // 그 사이 누가 나가서 인원이 줄어도 이 값 자체는 안 바뀐다(GameEngine.resolveWinnerId 참고).
    private String gameOverWinnerId;
    // gameOverWinnerId와 같은 시점에 같이 고정되는 닉네임. winnerId만 내려주면 프론트가 매번
    // players 목록에서 그 id로 닉네임을 다시 찾아야 하는데, 카운트다운 도중 그 승자가 "나가기"로
    // 방을 빠지면 더 이상 목록에 없어서 닉네임을 못 찾는 문제가 있었다 — 그래서 닉네임 자체도 값을
    // 고정해서 들고 있는다.
    private String gameOverWinnerNickname;

    // 로비 표시/입장에 쓰이는 방 정체성. RoomManager.createRoom()이 생성 직후 한 번만 initIdentity로
    // 채운다 — configure()(설정값)와 달리 방 생성 이후 바뀌지 않는다.
    private String roomCode;
    private String name;
    private boolean isPrivate;
    // 비공개방 비밀번호는 평문으로 보관한다 — 계정 비밀번호와 달리 민감도가 낮은(파티룸 PIN 수준)
    // 값이라 해시의 복잡도(방장 본인도 다시 확인 못 함)를 감수할 필요가 없다고 판단했다.
    // isPrivate=false면 항상 null이다.
    private String password;

    // RoomManager가 방 생성 직후 한 번만 호출한다. roomCode/name은 이후 바뀌지 않는다.
    public void initIdentity(String roomCode, String name, boolean isPrivate, String password) {
        this.roomCode = roomCode;
        this.name = name;
        this.isPrivate = isPrivate;
        this.password = password;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public String getName() {
        return name;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public String getPassword() {
        return password;
    }

    // 방 설정(시작 칩/빅블라인드/최대 인원)을 바꾼다. 방이 완전히 비어있을 때만 허용된다 — 이미
    // 누가 참가했으면 그 사람 기준으로 이미 확정된 설정을 뒤바꿀 수 없다. 스몰블라인드는 따로
    // 입력받지 않고 항상 빅블라인드의 절반으로 자동 계산한다(실제 포커 대회 관례).
    public void configure(int startingChips, int bigBlind, int maxPlayers) {
        if (!players.isEmpty()) {
            throw new GameStateException("이미 참가자가 있어 방 설정을 변경할 수 없습니다.");
        }
        if (startingChips <= 0) {
            throw new InvalidActionException("시작 칩은 0보다 커야 합니다.");
        }
        if (bigBlind <= 0 || bigBlind % BET_UNIT != 0) {
            throw new InvalidActionException("빅블라인드는 " + BET_UNIT + " 단위의 양수여야 합니다.");
        }
        if (maxPlayers < MIN_PLAYERS || maxPlayers > MAX_PLAYERS) {
            throw new InvalidActionException("최대 인원은 " + MIN_PLAYERS + "~" + MAX_PLAYERS + "명 사이여야 합니다.");
        }
        this.startingChips = startingChips;
        this.bigBlind = bigBlind;
        this.smallBlind = bigBlind / 2;
        this.maxPlayers = maxPlayers;
    }

    public void addPlayer(Player player) {
        if (players.size() >= maxPlayers) {
            throw new GameStateException("테이블 정원(" + maxPlayers + "명)이 가득 찼습니다.");
        }
        // 게스트(accountUserId == null)는 중복 체크 대상이 아니다 — 같은 로그인 계정이 다른 브라우저/탭
        // 에서 또 입장해서 자기 자신과 마주 앉는(멀티어카운팅) 것만 막는다.
        if (player.getAccountUserId() != null
                && players.stream().anyMatch(p -> player.getAccountUserId().equals(p.getAccountUserId()))) {
            throw new GameStateException("이미 이 계정으로 참가 중입니다.");
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

    // leaving=true로 예약된 플레이어를 실제로 방에서 제거한다(GameEngine.requestLeave/핸드 종료
    // 시점에서 호출됨). 딜러 버튼이 리스트 인덱스 기준이라, 버튼보다 앞쪽(작은 인덱스)의 누군가가
    // 빠지면 나머지 인덱스가 한 칸씩 당겨져서 버튼이 엉뚱한 사람을 가리킬 수 있다 — 그래서 인덱스가
    // 아니라 "버튼을 쥔 사람(객체)"을 기준으로 추적했다가 제거 후 그 사람의 새 인덱스를 다시 찾는다.
    public void removeLeavingPlayers() {
        if (players.stream().noneMatch(Player::isLeaving)) {
            return;
        }
        Player buttonHolder = (dealerButtonPosition >= 0 && dealerButtonPosition < players.size())
                ? players.get(dealerButtonPosition)
                : null;

        players.removeIf(Player::isLeaving);

        if (players.isEmpty()) {
            dealerButtonPosition = -1;
        } else if (buttonHolder != null && players.contains(buttonHolder)) {
            dealerButtonPosition = players.indexOf(buttonHolder);
        } else {
            // 버튼을 쥐고 있던 사람 본인이 나간 경우 — 정확히 "다음 버튼이 누구여야 하는지"까지
            // 보존하진 않고(다음 핸드 시작 시 moveButtonToNextSeat가 다시 정상적으로 순환시킨다),
            // 범위 안으로만 clamp한다.
            dealerButtonPosition = dealerButtonPosition % players.size();
        }
    }

    // GAME OVER(생존자 1명) 15초 후 호출된다. 앉아있던 사람은 내보내지 않고 그대로 둔 채, 전원 칩을
    // 시작 칩으로 리필하고 레디를 초기화해서(파산했던 사람도 다시 ACTIVE로 돌아옴) "전원 레디하면
    // 자동 시작"을 다시 기다리는 상태로 되돌린다 — 기존 레디 시스템(AutoStartService/
    // isWaitingForNextHandWithEveryoneReady)이 그대로 재사용되어, 새로 레디를 받아야만 실제로
    // 다음 핸드가 시작된다. phase/버튼/쇼다운 관련 필드는 새로 만든 Room과 동일한 상태로 돌아간다.
    public void resetForRematch() {
        // 나가기 처리(LeaveProcessingTimerService, 3초 유예)가 미처 끝나기 전에 GAME OVER 리셋
        // (15초)이 먼저 도달하는 드문 경우를 대비해, 리셋 전에 나가기 예약된 사람을 먼저 정리한다 —
        // 안 그러면 leaving=true인 채로 칩만 리필되어 영원히 안 나가지는 상태가 될 수 있다.
        removeLeavingPlayers();
        for (Player player : players) {
            player.resetChips(startingChips);
            player.resetForNewHand();
            player.setReady(false);
        }
        communityCards.clear();
        pots = new ArrayList<>();
        deck = new Deck();
        phase = null;
        dealerButtonPosition = -1;
        wonByFold = false;
        lastShowdownResult = null;
        foldWinWinnerId = null;
        voluntarilyRevealedIds.clear();
        headsUpShowdown = false;
        headsUpDeciderPlayerId = null;
        gameOverWinnerId = null;
        gameOverWinnerNickname = null;
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

    public int getSmallBlind() {
        return smallBlind;
    }

    public int getBigBlind() {
        return bigBlind;
    }

    public int getStartingChips() {
        return startingChips;
    }

    public int getMaxPlayers() {
        return maxPlayers;
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

    public String getGameOverWinnerId() {
        return gameOverWinnerId;
    }

    public void setGameOverWinnerId(String gameOverWinnerId) {
        this.gameOverWinnerId = gameOverWinnerId;
    }

    public String getGameOverWinnerNickname() {
        return gameOverWinnerNickname;
    }

    public void setGameOverWinnerNickname(String gameOverWinnerNickname) {
        this.gameOverWinnerNickname = gameOverWinnerNickname;
    }
}
