package com.lbg0146.backend.room;

import com.lbg0146.backend.card.Card;
import com.lbg0146.backend.card.Deck;
import com.lbg0146.backend.exception.GameStateException;
import com.lbg0146.backend.exception.InvalidActionException;
import com.lbg0146.backend.game.ShowdownResult;
import com.lbg0146.backend.player.Player;
import com.lbg0146.backend.player.PlayerStatus;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 단일 고정 테이블의 상태를 담는 객체. 게임 진행/계산 로직은 game 패키지(GameEngine 등)가 담당한다.
public class Room {

    // 아래 2개는 "기본값"이다 — 방이 비어있을 때 configure()로 바꾸지 않으면 이 값 그대로 쓰인다.
    public static final int SMALL_BLIND = 100;
    public static final int BIG_BLIND = 200;
    public static final int STARTING_CHIPS = 30_000;
    // 고정 좌석 수 — 방마다 다르게 설정할 수 없다(예전엔 방 생성 시 2~6명 중 고를 수 있었는데,
    // 고정 좌석제(빈자리 클릭해서 앉기/옮기기) 도입으로 항상 6자리로 통일했다).
    public static final int MAX_PLAYERS = 6;
    // 핸드를 시작하려면 최소 이만큼은 앉아있어야 한다. 좌석 수(MAX_PLAYERS)와는 별개 개념이다.
    public static final int MIN_PLAYERS = 2;
    // BET/RAISE 금액은 이 단위의 배수여야 한다. 단, 보유 칩 전부를 거는 경우(사실상 올인)는 예외로 허용한다.
    public static final int BET_UNIT = 100;

    // 실제로 이번 방에 적용되는 값. 기본값(위 static final)으로 시작하고, configure()로 방이
    // 비어있을 때만 바꿀 수 있다 — 방장이 "방 만들기" 팝업에서 정한 값이 여기 반영된다.
    private int smallBlind = SMALL_BLIND;
    private int bigBlind = BIG_BLIND;
    private int startingChips = STARTING_CHIPS;

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

    // 방장(방을 만든 뒤 가장 먼저 입장한 사람)의 playerId. addPlayer()가 방이 비어있을 때 첫
    // 입장자를 방장으로 지정하고, removeLeavingPlayers()가 방장이 나가면 남은 사람 중 가장 오래
    // 앉아있는 사람(players 리스트의 첫 번째 — 입장 순서가 그대로 유지됨)에게 자동 승계한다.
    // 방이 완전히 비면 null로 돌아간다. 순수 표시용 배지 + 강퇴 권한 판단에만 쓰인다.
    private String ownerId;

    // 핸드 히스토리 — DB에 저장하지 않고 이 방(RoomInstance)이 살아있는 동안만 메모리에 최근
    // MAX_HAND_HISTORY개만 유지한다(방이 사라지면 같이 사라짐). 최신이 맨 앞에 오도록 addFirst만 쓴다.
    private static final int MAX_HAND_HISTORY = 30;
    private int handCounter;
    private final Deque<HandHistoryEntry> handHistory = new ArrayDeque<>();

    // 블라인드 상승 스케줄 — 시작 빅블라인드 대비 배율. HANDS_PER_LEVEL판마다 다음 단계로 오르고,
    // 마지막 단계(5배) 이후로는 더 오르지 않고 그대로 유지된다(고정 6단계 스케줄, 무한정 계속
    // 오르지는 않음). 앤티는 실제 대회(JOPT 등)처럼 레벨 1부터 계속 걷힌다 — "빅블라인드 앤티"
    // 방식이라 빅블라인드 자리에 앉은 사람이 그 레벨 빅블라인드와 같은 금액을 자기 빅블라인드에
    // 더해 혼자 추가로 낸다(GameEngine.postBlinds 참고). 레벨이 오르면 앤티도 그 레벨 빅블라인드를
    // 그대로 따라가며 같이 오른다.
    private static final double[] BLIND_LEVEL_MULTIPLIERS = {1.0, 1.5, 2.0, 3.0, 4.0, 5.0};
    private static final int HANDS_PER_LEVEL = 15;
    private static final int ANTE_START_LEVEL_INDEX = 0;

    // 방 생성 시 정한 시작 빅블라인드 — 블라인드 상승 배율 계산의 기준값. configure() 시점에
    // 고정되고 이후 안 바뀐다(bigBlind/smallBlind 필드 자체는 레벨이 오르면서 계속 갱신됨).
    private int baseBigBlind = BIG_BLIND;
    // 블라인드가 마지막으로 리셋된 뒤(방 시작, 또는 GAME OVER 리매치) 지금까지 끝난 핸드 수.
    // 이 값으로 현재 블라인드 레벨을 계산한다.
    private int handsSinceBlindReset;
    private int ante;
    // 이번 핸드에서 실제로 걷힌 앤티 총액(숏스택이면 명목 앤티보다 적을 수 있음) — 특정 플레이어
    // 소유가 아니라 팟 전체에 속하는 "죽은 돈"이라 Player.totalHandContribution과는 별도로 여기
    // 들고 있다가, PotCalculator가 메인팟에 통째로 더한다. 새 핸드가 시작되면 0으로 리셋된다.
    private int anteCollectedThisHand;
    // 이 핸드가 실제로 끝난 시각(ms) — GameEngine.onHandConcluded()가 매 핸드 종료 시점마다 갱신한다.
    // "나가기"를 눌렀을 때 결과 화면을 보호할 필요가 아직 있는지(프론트 useShowBoard.ts의
    // RESULT_HOLD_MS만큼 시간이 지났는지) 판단하는 데 쓰인다(GameEngine.canLeaveImmediately 참고).
    private long handConcludedAtMillis;

    public String getOwnerId() {
        return ownerId;
    }

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

    // 방 설정(시작 칩/빅블라인드)을 바꾼다. 방이 완전히 비어있을 때만 허용된다 — 이미 누가
    // 참가했으면 그 사람 기준으로 이미 확정된 설정을 뒤바꿀 수 없다. 스몰블라인드는 따로 입력받지
    // 않고 항상 빅블라인드의 절반으로 자동 계산한다(실제 포커 대회 관례).
    public void configure(int startingChips, int bigBlind) {
        if (!players.isEmpty()) {
            throw new GameStateException("이미 참가자가 있어 방 설정을 변경할 수 없습니다.");
        }
        if (startingChips <= 0) {
            throw new InvalidActionException("시작 칩은 0보다 커야 합니다.");
        }
        if (bigBlind <= 0 || bigBlind % BET_UNIT != 0) {
            throw new InvalidActionException("빅블라인드는 " + BET_UNIT + " 단위의 양수여야 합니다.");
        }
        this.startingChips = startingChips;
        this.bigBlind = bigBlind;
        this.smallBlind = bigBlind / 2;
        this.baseBigBlind = bigBlind;
        // 레벨 1의 앤티까지 지금 바로 반영해둔다 — 안 그러면 첫 핸드가 시작되기 전(예: 로비 상세
        // 정보 조회 시점)에는 ante가 기본값 0으로 남아있어서, 방금 만든 방인데 앤티가 안 보이는
        // 것처럼 보이는 문제가 있었다(실사용 중 발견됨).
        applyBlindLevel();
    }

    // 빈 좌석 중 가장 낮은 번호에 자동으로 앉힌다. 주로 테스트와 "방 만들기" 직후 혼자인 방에
    // 들어가는 경우처럼 굳이 좌석을 직접 고를 필요가 없을 때 쓴다 — 실제 로비에서 들어오는
    // 입장은 항상 addPlayer(player, seatIndex)로 사용자가 클릭한 좌석을 명시한다.
    public void addPlayer(Player player) {
        addPlayer(player, nextFreeSeatIndex());
    }

    // 명시적으로 지정한 좌석에 앉힌다(고정 좌석제 — 빈자리 클릭해서 입장). 그 좌석이 이미 차 있으면
    // 거부된다. players 리스트는 항상 seatIndex 오름차순으로 유지되므로, 버튼 이동(moveButtonToNextSeat)
    // 등 "리스트 순서 = 좌석 순서" 로직은 이 메서드만으로 계속 정상 동작한다.
    public void addPlayer(Player player, int seatIndex) {
        if (seatIndex < 0 || seatIndex >= MAX_PLAYERS) {
            throw new InvalidActionException("좌석 번호가 올바르지 않습니다.");
        }
        if (isSeatTaken(seatIndex)) {
            throw new GameStateException("이미 다른 사람이 앉아있는 자리입니다.");
        }
        // 게스트(accountUserId == null)는 중복 체크 대상이 아니다 — 같은 로그인 계정이 다른 브라우저/탭
        // 에서 또 입장해서 자기 자신과 마주 앉는(멀티어카운팅) 것만 막는다.
        if (player.getAccountUserId() != null
                && players.stream().anyMatch(p -> player.getAccountUserId().equals(p.getAccountUserId()))) {
            throw new GameStateException("이미 이 계정으로 참가 중입니다.");
        }
        boolean firstPlayer = players.isEmpty();
        player.setSeatIndex(seatIndex);
        players.add(player);
        resortSeatsPreservingButton();
        if (firstPlayer) {
            ownerId = player.getId();
        }
        // 핸드가 진행 중일 때(phase가 있고 SHOWDOWN도 아님) 새로 들어온 사람은 이번 핸드를 딜받지
        // 않았으므로, GameEngine이 "이번 핸드에 아직 남아있는 사람"으로 잘못 세지 않도록 표시해둔다
        // — 다음 핸드가 시작되면(resetForNewHand) 자동으로 풀린다.
        if (phase != null && phase != Phase.SHOWDOWN) {
            player.sitOutThisHand();
        }
    }

    // 이미 앉아있는 플레이어가 다른 빈 좌석으로 옮긴다. 핸드 진행 중 제한/연타 방지 등 정책 판단은
    // GameEngine.requestSeatMove()가 먼저 하고, 여기서는 순수하게 "그 자리가 비어있는지"만 본다.
    public void moveSeat(String playerId, int newSeatIndex) {
        if (newSeatIndex < 0 || newSeatIndex >= MAX_PLAYERS) {
            throw new InvalidActionException("좌석 번호가 올바르지 않습니다.");
        }
        Player player = findPlayer(playerId);
        if (player.getSeatIndex() == newSeatIndex) {
            return;
        }
        if (isSeatTaken(newSeatIndex)) {
            throw new GameStateException("이미 다른 사람이 앉아있는 자리입니다.");
        }
        player.setSeatIndex(newSeatIndex);
        player.markSeatChanged();
        resortSeatsPreservingButton();
    }

    public boolean isSeatTaken(int seatIndex) {
        return players.stream().anyMatch(p -> p.getSeatIndex() == seatIndex);
    }

    private int nextFreeSeatIndex() {
        for (int i = 0; i < MAX_PLAYERS; i++) {
            if (!isSeatTaken(i)) {
                return i;
            }
        }
        throw new GameStateException("테이블 정원(" + MAX_PLAYERS + "명)이 가득 찼습니다.");
    }

    // players를 seatIndex 오름차순으로 다시 정렬한다. dealerButtonPosition은 리스트 인덱스 기준이라
    // 정렬로 순서가 바뀌면 엉뚱한 사람을 가리킬 수 있다 — removeLeavingPlayers()와 같은 패턴으로,
    // 정렬 전에 "버튼을 쥔 사람(객체)"을 기억해뒀다가 정렬 후 그 사람의 새 인덱스로 보정한다.
    private void resortSeatsPreservingButton() {
        Player buttonHolder = (dealerButtonPosition >= 0 && dealerButtonPosition < players.size())
                ? players.get(dealerButtonPosition)
                : null;
        players.sort(Comparator.comparingInt(Player::getSeatIndex));
        if (buttonHolder != null) {
            dealerButtonPosition = players.indexOf(buttonHolder);
        }
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
        boolean ownerLeaving = ownerId != null
                && players.stream().anyMatch(p -> p.isLeaving() && ownerId.equals(p.getId()));

        players.removeIf(Player::isLeaving);

        if (players.isEmpty()) {
            dealerButtonPosition = -1;
            ownerId = null;
            return;
        }

        if (ownerLeaving) {
            // 남은 사람 중 가장 먼저 입장한 사람(joinedAtMillis 최솟값)에게 승계. 리스트 순서는
            // 이제 좌석 번호 순서라 입장 순서와 다를 수 있어(자리를 옮기면 순서가 바뀜), 더 이상
            // "리스트 맨 앞"으로 판단할 수 없다.
            ownerId = players.stream().min(Comparator.comparingLong(Player::getJoinedAtMillis))
                    .map(Player::getId)
                    .orElse(null);
        }

        if (buttonHolder != null && players.contains(buttonHolder)) {
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
        anteCollectedThisHand = 0;
        handConcludedAtMillis = 0;
        // 블라인드 상승/앤티도 시작 단계로 되돌린다.
        handsSinceBlindReset = 0;
        applyBlindLevel();
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
        return MAX_PLAYERS;
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

    // 다음 핸드 번호를 하나 소모해서 반환한다. GameEngine.onHandConcluded()가 핸드 히스토리 항목을
    // 만들 때 한 번만 호출한다.
    public int nextHandNumber() {
        return ++handCounter;
    }

    // 최신 항목이 맨 앞에 오도록 추가하고, MAX_HAND_HISTORY를 넘으면 가장 오래된 것부터 버린다.
    public void addHandHistoryEntry(HandHistoryEntry entry) {
        handHistory.addFirst(entry);
        while (handHistory.size() > MAX_HAND_HISTORY) {
            handHistory.removeLast();
        }
    }

    public List<HandHistoryEntry> getHandHistory() {
        return List.copyOf(handHistory);
    }

    public int getAnte() {
        return ante;
    }

    // GameEngine.postBlinds()가 앤티를 걷을 때마다 호출한다 — 실제로 낸 금액(숏스택이면 명목
    // 앤티보다 적을 수 있음)을 누적한다.
    public void addAnteCollected(int amount) {
        anteCollectedThisHand += amount;
    }

    public int getAnteCollectedThisHand() {
        return anteCollectedThisHand;
    }

    // 새 핸드가 시작될 때(GameEngine.startHand()) 호출한다 — 이전 핸드에서 걷힌 앤티가 이번
    // 핸드의 팟 계산에 섞이지 않도록 리셋한다.
    public void resetAnteCollected() {
        anteCollectedThisHand = 0;
    }

    // 핸드가 하나 끝날 때마다 GameEngine.onHandConcluded()가 호출한다 — 다음 핸드의 블라인드 레벨
    // 계산에 반영된다(레벨 자체는 applyBlindLevel()이 실제로 적용).
    public void recordHandCompletedForBlindLevel() {
        handsSinceBlindReset++;
    }

    // 핸드가 하나 끝날 때마다 GameEngine.onHandConcluded()가 호출한다.
    public void markHandConcluded() {
        handConcludedAtMillis = System.currentTimeMillis();
    }

    public long getHandConcludedAtMillis() {
        return handConcludedAtMillis;
    }

    // handsSinceBlindReset 기준으로 현재 레벨을 계산해서 bigBlind/smallBlind/ante에 반영한다.
    // GameEngine.startHand()가 블라인드를 걷기 직전에 호출하고, resetForRematch()도 리셋 직후
    // 바로 호출해서(다음 핸드를 기다리는 15초 동안도) 화면에 시작 단계 값이 즉시 보이게 한다.
    public void applyBlindLevel() {
        int levelIndex = currentBlindLevelIndex();
        int newBigBlind = bigBlindForLevelIndex(levelIndex);
        this.bigBlind = newBigBlind;
        this.smallBlind = newBigBlind / 2;
        this.ante = levelIndex >= ANTE_START_LEVEL_INDEX ? newBigBlind : 0;
    }

    // 1부터 시작하는 현재 블라인드 레벨 번호(화면 표시용) — applyBlindLevel()이 실제로 적용하는
    // 0-based 인덱스에 +1만 한 값이다.
    public int getCurrentBlindLevel() {
        return currentBlindLevelIndex() + 1;
    }

    public int getHandsSinceBlindReset() {
        return handsSinceBlindReset;
    }

    // 전체 블라인드 구조표(고정 6단계) — 지금 몇 판째인지와 무관하게 항상 같은 값을 돌려준다.
    // 프론트의 "블라인드 구조" 패널이 레벨별 스몰/빅블라인드/앤티를 한 번에 보여주는 데 쓴다.
    public List<BlindLevelInfo> getBlindStructure() {
        List<BlindLevelInfo> levels = new ArrayList<>();
        for (int i = 0; i < BLIND_LEVEL_MULTIPLIERS.length; i++) {
            int levelBigBlind = bigBlindForLevelIndex(i);
            int levelAnte = i >= ANTE_START_LEVEL_INDEX ? levelBigBlind : 0;
            levels.add(new BlindLevelInfo(i + 1, levelBigBlind / 2, levelBigBlind, levelAnte));
        }
        return levels;
    }

    private int currentBlindLevelIndex() {
        return Math.min(handsSinceBlindReset / HANDS_PER_LEVEL, BLIND_LEVEL_MULTIPLIERS.length - 1);
    }

    // 빅블라인드 금액은 BET_UNIT의 배수여야 하므로, 배율을 곱한 뒤 가장 가까운 단위로 반올림한다.
    private int bigBlindForLevelIndex(int levelIndex) {
        return Math.round((float) (baseBigBlind * BLIND_LEVEL_MULTIPLIERS[levelIndex]) / BET_UNIT) * BET_UNIT;
    }
}
