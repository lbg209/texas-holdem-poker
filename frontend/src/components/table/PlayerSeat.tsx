import { useEffect, useState } from 'react';
import type { CardView, PlayerView, ShowdownHandView } from '../../types/room';
import type { SeatPosition } from './computeSeatPositions';
import type { Position } from '../../lib/positions';
import type { HighlightedCard, HighlightColor } from '../../lib/handRank';
import { formatMoney } from '../../lib/formatMoney';
import {
  findHighlightColor,
  getHandRankLabel,
  getHandRankTextClassName,
  getHandRankTextPrefix,
  getHighlightColorForHandRank,
} from '../../lib/handRank';
import { Card } from './Card';
import { RevealCard } from './RevealCard';
import { FlipCard } from './FlipCard';
import { ChipStack } from './ChipStack';
import { AllInBadge } from './AllInBadge';
import { useCountdownSeconds } from '../../lib/useCountdownSeconds';
import { SEAT_COLORS } from '../../lib/seatColors';

// 이 초 이하로 남으면 카운트다운을 빨간색으로 강조한다.
const URGENT_SECONDS_THRESHOLD = 10;
// 딜링 후 내 카드가 처음 자동으로 공개될 때(기존 RevealCard와 동일한 타이밍)와, 그 이후 내가
// 직접 클릭해서 확인/가리기를 반복할 때의 뒤집기 속도를 다르게 둔다 — 반복 조작은 빨라야 답답하지 않다.
const MY_CARD_INITIAL_REVEAL_DELAY_MS = 150;
const MY_CARD_INITIAL_REVEAL_DURATION_MS = 500;
const MY_CARD_PEEK_FLIP_DURATION_MS = 150;

// 보유칩 더미는 정보박스 옆(중앙 쪽)에, 고정된 만큼 떨어진 자리에 둔다.
const CHIP_HORIZONTAL_OFFSET = 17;

interface PlayerSeatProps {
  player: PlayerView;
  // 정보박스(닉네임/액션/족보)의 좌표. 카드/보유칩은 여기서 고정된 만큼 떨어진 위치로 계산한다
  // (카드는 바로 위, 칩은 중앙 쪽 옆) — "정보박스 위치는 지금 좋다"는 피드백에 맞춰, 이 좌표
  // 하나만 조정하면 카드/칩도 같이 따라오게 만들었다.
  position: SeatPosition;
  tablePosition: Position | null;
  isMe: boolean;
  isCurrentActor: boolean;
  showCards: boolean;
  isChipLeader: boolean;
  showdownHand: ShowdownHandView | null;
  winnerCards: HighlightedCard[];
  isWinner: boolean;
  handEnded: boolean;
  // 새 핸드 딜링 중일 때만 주어진다(0~2). 주어지면 실제 홀카드 데이터 대신 이 개수만큼 뒷면
  // 카드 자리만 보여준다 — 상대 카드는 서버가 애초에 안 보내주므로 실제 데이터로는 표현할 수 없다.
  dealtPlaceholderCount?: number;
  // displayState 기준(화면에 지금 그려지는 상태와 항상 같은 타이밍에 갱신됨) — 이 좌석이
  // isCurrentActor일 때만 실제로 표시에 쓰인다.
  turnDeadlineAtMillis: number | null;
  // 보고 있는 나 자신이 방장인지 — 강퇴 버튼 노출 여부 판단에 쓰인다(이 좌석의 player.isOwner와는 별개).
  amIOwner: boolean;
  // 이번 핸드에 적용된 앤티 금액(0이면 이번 레벨엔 앤티 없음). 빅블라인드 자리(tablePosition==='BB')
  // 만 실제로 앤티를 내므로, 그 좌석의 "-금액" 표시에만 더해서 보여준다 — 백엔드는 앤티를 일부러
  // totalHandContribution에 안 섞어서(사이드팟 계산이 꼬이는 버그가 있었음) 그대로 쓰면 BB 좌석의
  // "-금액"이 실제 칩 차감액보다 적게 보이는 문제가 있었다.
  ante: number;
  // 방장이 이 좌석 플레이어를 강퇴한다. 너무 일찍 시도하면(레디 안 한 지 3초가 안 지남) 서버가
  // 거부하고 그 메시지가 화면 상단 에러 배너에 뜬다 — 여기서 남은 시간을 따로 보여주지 않는다.
  onKick?: (targetId: string) => void;
}

// 액션 줄: FOLD/ALL-IN은 상태 기준(스트리트가 바뀌어도 유지), CHECK/CALL/BET/RAISE는
// 이번 스트리트의 lastAction 기준(스트리트 전환 시 서버에서 null로 리셋됨)으로 판단한다.
function getActionLine(player: PlayerView): string | null {
  if (player.status === 'BUSTED') {
    return 'BUSTED';
  }
  if (player.status === 'FOLDED') {
    return player.autoFolded ? '⏱ FOLD' : 'FOLD';
  }
  if (player.status === 'ALL_IN') {
    return 'ALL-IN';
  }
  if (player.lastAction === 'CHECK') {
    return 'CHECK';
  }
  if (
    (player.lastAction === 'CALL' || player.lastAction === 'BET' || player.lastAction === 'RAISE') &&
    player.currentRoundBet > 0
  ) {
    return `${player.lastAction} ${formatMoney(player.currentRoundBet)}`;
  }
  return null;
}

// 카드가 막 나타난 순간(0장 -> 실제 카드)에는 뒷면에서 앞면으로 뒤집히는 연출을 재생한다.
// (상대는 쇼다운 공개 때, 나는 새 핸드 딜링이 끝나고 확인할 때 — 둘 다 이 컴포넌트가 처음
// 실제 카드값을 받는 순간이라 자연히 한 번만 재생된다.) 아직 카드가 없으면 뒷면 그대로 둔다.
function HoleCard({
  card,
  faceUp,
  highlightColor,
}: {
  card?: CardView;
  faceUp: boolean;
  highlightColor: HighlightColor | null;
}) {
  if (card) {
    return (
      <RevealCard
        card={card}
        highlighted={highlightColor !== null}
        highlightColor={highlightColor ?? undefined}
        flipDelayMs={150}
        flipDurationMs={500}
      />
    );
  }
  return <Card card={card} face={faceUp ? 'up' : 'down'} highlighted={highlightColor !== null} highlightColor={highlightColor ?? undefined} />;
}

// 내 카드 전용: 딜링이 끝나면 기존과 동일하게 한 번 자동으로 공개되지만, 그 뒤로는 클릭할 때마다
// 뒷면 <-> 앞면을 토글할 수 있다(실제 홀덤에서 패를 확인했다가 다시 덮어두는 것처럼). forceFaceUp이면
// (쇼다운 등 반드시 보여야 하는 시점) 토글 상태와 무관하게 항상 앞면으로 고정하고 클릭도 막는다.
// "새 핸드로 카드가 바뀌면 다시 자동 공개부터"는 상태를 직접 리셋하는 대신, 호출 측(PlayerSeat)이
// 카드 값 기반 key를 줘서 컴포넌트를 통째로 새로 마운트시키는 방식으로 처리한다 — React가 권장하는
// "prop이 바뀌면 상태를 리셋하는" 패턴이고, 이렇게 하면 이 안에서 effect로 직접 리셋할 필요가
// 없어져 StrictMode의 개발 모드 이중 실행(mount->cleanup->mount)과도 충돌할 여지가 없다.
function MyHoleCard({
  card,
  highlightColor,
  forceFaceUp,
}: {
  card: CardView;
  highlightColor: HighlightColor | null;
  forceFaceUp: boolean;
}) {
  const [faceUp, setFaceUp] = useState(false);
  const [hasRevealedOnce, setHasRevealedOnce] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setFaceUp(true);
      setHasRevealedOnce(true);
    }, MY_CARD_INITIAL_REVEAL_DELAY_MS);
    return () => window.clearTimeout(timer);
    // 마운트 시 한 번만 예약한다 — 이 컴포넌트는 카드가 바뀔 때마다(key 덕분에) 통째로 새로
    // 마운트되므로, 매 마운트가 곧 "이 카드에 대한 첫 예약"이다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const displayFaceUp = forceFaceUp || faceUp;
  const canToggle = hasRevealedOnce && !forceFaceUp;

  return (
    <button
      type="button"
      disabled={!canToggle}
      onClick={() => canToggle && setFaceUp((v) => !v)}
      className={`rounded focus:outline-none ${canToggle ? 'cursor-pointer transition-transform hover:scale-105' : ''}`}
      aria-label={displayFaceUp ? '내 카드 가리기' : '내 카드 확인하기'}
    >
      <FlipCard
        card={card}
        faceUp={displayFaceUp}
        flipDurationMs={hasRevealedOnce ? MY_CARD_PEEK_FLIP_DURATION_MS : MY_CARD_INITIAL_REVEAL_DURATION_MS}
        highlighted={highlightColor !== null}
        highlightColor={highlightColor ?? undefined}
      />
    </button>
  );
}

export function PlayerSeat({
  player,
  position,
  tablePosition,
  isMe,
  isCurrentActor,
  showCards,
  isChipLeader,
  showdownHand,
  winnerCards,
  isWinner,
  handEnded,
  dealtPlaceholderCount,
  turnDeadlineAtMillis,
  amIOwner,
  onKick,
  ante,
}: PlayerSeatProps) {
  const [hole0, hole1] = player.holeCards;
  const faceUp = player.holeCards.length > 0;
  const secondsLeft = useCountdownSeconds(isCurrentActor ? turnDeadlineAtMillis : null);
  const isBusted = player.status === 'BUSTED';
  // BUSTED는 이번 핸드 한정이 아니라 계속 이어지는 실제 상태라 showCards와 무관하게 항상 보여준다.
  // 그 외(FOLD/CHECK/CALL/BET/RAISE/ALL-IN)는 방금 끝난 핸드의 결과라, showCards가 꺼지면
  // (자동 진행이 멈춰 대기 화면으로 넘어가면) 같이 사라져야 "방금 만든 방" 같은 화면이 된다.
  const actionLine = showCards || isBusted ? getActionLine(player) : null;
  const handRankColor: HighlightColor | null =
    showCards && showdownHand && showdownHand.handRank !== null && showdownHand.bestFive !== null
      ? getHighlightColorForHandRank(showdownHand.handRank, showdownHand.bestFive)
      : null;
  // 승자 하이라이트(테두리 글로우/WINNER 배지)도 이번 핸드 한정 결과라 showCards를 따라간다.
  const showWinnerHighlight = isWinner && showCards;

  // 파산한 좌석은 이번 핸드에 아예 참여하지 않으므로 카드를 아예 그리지 않는다 — 홀카드가
  // 항상 비어 있어(백엔드가 애초에 안 나눠줌) HoleCard의 "아직 안 왔으면 뒷면 표시" 기본 동작을
  // 그대로 두면, 딜링 애니메이션이 끝난 뒤에도 뒷면 카드 두 장이 계속 남아 마치 참여 중인
  // 것처럼 보이는 문제가 있었다.
  const cards = isBusted
    ? null
    : // 딜링 중이면(dealtPlaceholderCount가 주어짐) 실제 홀카드 데이터는 무시하고 그 개수만큼
      // 뒷면 카드만 보여준다. 그 외엔 평소대로(하이라이트는 "이긴 조합"만 — 승자의 bestFive에
      // 실제로 포함된 카드만 강조).
      dealtPlaceholderCount !== undefined ? (
        <div className="flex gap-1">
          {Array.from({ length: dealtPlaceholderCount }).map((_, i) => (
            <Card key={i} face="down" />
          ))}
        </div>
      ) : (
        showCards && (
          <div className="flex gap-1">
            {isMe ? (
              <>
                {hole0 ? (
                  <MyHoleCard
                    key={`${hole0.suit}_${hole0.rank}`}
                    card={hole0}
                    highlightColor={findHighlightColor(winnerCards, hole0)}
                    forceFaceUp={handEnded}
                  />
                ) : (
                  <Card face="down" />
                )}
                {hole1 ? (
                  <MyHoleCard
                    key={`${hole1.suit}_${hole1.rank}`}
                    card={hole1}
                    highlightColor={findHighlightColor(winnerCards, hole1)}
                    forceFaceUp={handEnded}
                  />
                ) : (
                  <Card face="down" />
                )}
              </>
            ) : (
              <>
                <HoleCard
                  card={hole0}
                  faceUp={faceUp}
                  highlightColor={hole0 ? findHighlightColor(winnerCards, hole0) : null}
                />
                <HoleCard
                  card={hole1}
                  faceUp={faceUp}
                  highlightColor={hole1 ? findHighlightColor(winnerCards, hole1) : null}
                />
              </>
            )}
          </div>
        )
      );

  // 핸드가 끝난 뒤(쇼다운 또는 폴드 종료)에만 이번 핸드의 손익을 +/-로 보여준다. 이것도 이번
  // 핸드 한정 결과라 showCards가 꺼지면(대기 화면) 같이 사라진다.
  const showNetChange = showCards && handEnded && player.netChipChange !== 0;
  // 이번 핸드 누적 베팅액은 정보박스의 별도 줄 대신, 중앙 팟의 "+금액" 표시와 같은 방식으로
  // 칩 스택 숫자 옆에 "-금액"으로 붙인다. 핸드가 끝나면(손익 +/-로 대체되므로) 더는 보여주지 않는다.
  // 빅블라인드 자리(앤티를 실제로 내는 사람)는 totalHandContribution에 앤티를 더해서 보여준다 —
  // 안 그러면 실제 칩 차감액(블라인드+앤티)보다 이 숫자가 적게 보여서 "왜 칩이 더 빠졌지?" 헷갈린다.
  const isAntePayer = tablePosition === 'BB' && ante > 0;
  const displayedContribution = player.totalHandContribution + (isAntePayer ? ante : 0);
  const showContribution = !handEnded && displayedContribution > 0;

  // 보유칩 더미는 정보박스 옆(중앙 쪽)에 둔다. 좌석이 테이블 왼쪽(leftPercent<=50, 예: 10시
  // 방향)이면 정보박스 오른쪽(중앙 쪽)에, 오른쪽(1시 방향)이면 정보박스 왼쪽(중앙 쪽)에.
  // 내 좌석(하단 정중앙, leftPercent=50)도 오른쪽에 온다.
  const chipOnRight = position.leftPercent <= 50;
  const chipPosition: SeatPosition = {
    leftPercent: position.leftPercent + (chipOnRight ? 1 : -1) * CHIP_HORIZONTAL_OFFSET * position.scale,
    topPercent: position.topPercent,
    scale: position.scale,
  };

  return (
    <>
      {/* 카드 + 정보박스: 같은 컨테이너에 묶어서, 카드는 정보박스의 실제 DOM 위치를 기준으로
          "바로 위, 수평 중앙"에 절대배치한다 — 좌표를 따로 계산해서 둘 다 배치하면 미세하게
          어긋날 여지가 있어서, 카드는 정보박스와 같은 부모를 공유하게 만들어 어긋날 수 없게 했다. */}
      <div
        className="absolute"
        style={{
          left: `${position.leftPercent}%`,
          top: `${position.topPercent}%`,
          transform: `translate(-50%, -50%) scale(${position.scale})`,
        }}
      >
        <div className="relative">
          <div className="absolute bottom-full left-1/2 mb-2 -translate-x-1/2">{cards}</div>
          <div
            className={`relative flex min-w-36 flex-col items-center gap-0.5 rounded-md px-3 py-2 text-sm shadow-md sm:text-base ${
              player.status === 'BUSTED'
                ? // 파산: 폴드(단순 반투명)와 다르게, 회색조 + 붉은 빗금 균열 무늬 + 점선 테두리로
                  // "깨진" 느낌을 낸다 — 더는 이번 핸드에 존재하지 않는 좌석임을 한눈에 구분되게.
                  'grayscale border-2 border-dashed border-red-900/60 bg-slate-950/90 bg-[repeating-linear-gradient(135deg,rgba(127,29,29,0.35)_0px,rgba(127,29,29,0.35)_2px,transparent_2px,transparent_10px)] opacity-70'
                : showWinnerHighlight
                  ? 'border-2 border-yellow-400 bg-gradient-to-b from-yellow-900/60 to-slate-900 shadow-[0_0_12px_rgba(250,204,21,0.6)]'
                  : isCurrentActor
                    ? 'border-2 border-emerald-400 bg-gradient-to-b from-slate-700 to-slate-900 shadow-emerald-400/40'
                    : 'border border-slate-600 bg-gradient-to-b from-slate-700/90 to-slate-900/90'
            } ${showCards && player.status === 'FOLDED' ? 'opacity-50' : ''}`}
          >
            <span className={`font-medium ${isMe ? 'text-emerald-400' : 'text-slate-100'}`}>
              {/* 좌석 번호 고정 색 — 닉네임이 같아도(중복 허용) 어느 자리인지로 구분할 수 있게 하는
                  보조 표시. 자리를 옮기면 색도 그 좌석 번호를 따라간다. */}
              <span
                className={`mr-1 inline-block h-2 w-2 rounded-full ${SEAT_COLORS[player.seatIndex]}`}
                title={`좌석 ${player.seatIndex + 1}`}
              />
              {isChipLeader && <span title="칩리더">👑</span>}
              {player.isOwner && <span title="방장">🎖️</span>}
              {player.nickname}
              {tablePosition && (
                <span className="ml-1 rounded bg-slate-200 px-1 text-[10px] text-slate-900">{tablePosition}</span>
              )}
              {player.leaving ? (
                <span
                  className="ml-1 rounded bg-red-600 px-1 text-[10px] text-white"
                  title="핸드가 끝나면 방에서 나갑니다"
                >
                  나가기 예약
                </span>
              ) : (
                player.ready && (
                  <span className="ml-1 rounded bg-emerald-600 px-1 text-[10px] text-white" title="다음 핸드 자동 시작에 동의함">
                    READY
                  </span>
                )
              )}
              {/* 방장 전용 강퇴 버튼: 본인 제외, 레디 안 한 사람에게만 노출. 너무 일찍 누르면 서버가
                  거부하고 그 메시지가 상단 에러 배너에 뜬다 — 남은 유예 시간은 여기서 보여주지 않는다. */}
              {amIOwner && !isMe && !player.leaving && !player.ready && onKick && (
                <button
                  type="button"
                  className="ml-1 rounded bg-slate-600 px-1 text-[10px] text-white hover:bg-red-700"
                  onClick={() => onKick(player.id)}
                  title="레디 안 한 플레이어를 강퇴합니다"
                >
                  강퇴
                </button>
              )}
            </span>
            {actionLine && (
              <span
                className={
                  player.status === 'BUSTED'
                    ? 'font-bold text-red-500'
                    : player.autoFolded
                      ? 'text-orange-400'
                      : 'text-amber-300'
                }
              >
                {actionLine}
              </span>
            )}
            {showdownHand && showdownHand.handRank !== null && showdownHand.bestFive !== null && handRankColor && (
              <span className={getHandRankTextClassName(handRankColor)}>
                {getHandRankTextPrefix(handRankColor)}
                {getHandRankLabel(showdownHand.handRank, showdownHand.bestFive)}
              </span>
            )}
            {/* 헤즈업 쇼다운에서 이 사람이 아직 공개 전이거나 머크했으면(handRank가 없으면) 카드
                내용 대신 실제 포커 용어로 표시한다. */}
            {showCards && showdownHand && showdownHand.handRank === null && (
              <span className="font-medium text-slate-400">MUCK</span>
            )}
            {showWinnerHighlight && <span className="font-bold text-yellow-400">WINNER</span>}
            {isCurrentActor && (
              <span className="font-semibold text-emerald-400">
                턴
                {secondsLeft !== null && (
                  <span className={secondsLeft <= URGENT_SECONDS_THRESHOLD ? 'text-red-500' : ''}>
                    {' '}
                    · {secondsLeft}초
                  </span>
                )}
              </span>
            )}
          </div>
        </div>
      </div>

      {/* 보유칩 더미: 정보박스 옆(중앙 쪽). */}
      <div
        className="absolute"
        style={{
          left: `${chipPosition.leftPercent}%`,
          top: `${chipPosition.topPercent}%`,
          transform: `translate(-50%, -50%) scale(${chipPosition.scale})`,
        }}
      >
        <div className="flex flex-col items-center gap-1">
          <div className="flex items-center gap-1">
            <ChipStack amount={player.chips} />
            {/* 실제 방송 중계처럼, 올인한 동안(이번 핸드가 끝날 때까지) 칩 스택 오른쪽에 계속 떠 있다. */}
            {player.status === 'ALL_IN' && <AllInBadge />}
          </div>
          <span className="text-sm font-semibold text-amber-200">
            {formatMoney(player.chips)}
            {showContribution && (
              <span
                className="text-red-400"
                title={
                  isAntePayer
                    ? `블라인드/베팅 ${formatMoney(player.totalHandContribution)} + 앤티 ${formatMoney(ante)}`
                    : undefined
                }
              >
                {' '}
                -{formatMoney(displayedContribution)}
                {isAntePayer && <span className="ml-0.5 text-[9px] text-orange-300">(+앤티)</span>}
              </span>
            )}
          </span>
          {showNetChange && (
            <span className={`text-sm font-bold ${player.netChipChange > 0 ? 'text-sky-400' : 'text-red-500'}`}>
              {player.netChipChange > 0 ? '+' : ''}
              {formatMoney(player.netChipChange)}
            </span>
          )}
        </div>
      </div>
    </>
  );
}
