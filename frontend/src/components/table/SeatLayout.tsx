import { useRef, useState } from 'react';
import type { Phase, PlayerView, ShowdownHandView } from '../../types/room';
import type { HighlightedCard } from '../../lib/handRank';
import { computeSeatPositions } from './computeSeatPositions';
import { computePosition } from '../../lib/positions';
import { PlayerSeat } from './PlayerSeat';
import { EmptySeat } from './EmptySeat';

// 고정 좌석 수 — Room.MAX_PLAYERS와 일치. 현재 인원수가 아니라 항상 이 값 기준으로 6자리를 그린다
// (고정 좌석제 — 빈자리도 자리를 차지한다).
const SEAT_COUNT = 6;
// 빈자리를 클릭한 뒤 다시 클릭할 수 있기까지의 쿨다운 — 서버도 같은 값(SEAT_MOVE_THROTTLE_MILLIS)
// 으로 한 번 더 막아주지만, 여기서는 "한 사람이 연타하는" 흔한 경우를 미리 막아 헛된 요청을 줄인다.
const SEAT_CLICK_COOLDOWN_MS = 1000;

interface SeatLayoutProps {
  players: PlayerView[];
  myPlayerId: string | null;
  dealerButtonPosition: number;
  currentActorId: string | null;
  showCards: boolean;
  showdownHands: ShowdownHandView[] | null;
  winnerCards: HighlightedCard[];
  winnerPlayerIds: string[];
  phase: Phase | null;
  dealProgress: Record<string, number> | null;
  turnDeadlineAtMillis: number | null;
  amIOwner: boolean;
  onKick?: (targetId: string) => void;
  ante: number;
  // 빈 좌석을 클릭했을 때: 아직 안 앉았으면 "입장", 이미 앉았으면 "이동" 의미로 호출 측이 처리한다.
  onSeatClick?: (seatIndex: number) => void;
}

export function SeatLayout({
  players,
  myPlayerId,
  dealerButtonPosition,
  currentActorId,
  showCards,
  showdownHands,
  winnerCards,
  winnerPlayerIds,
  phase,
  dealProgress,
  turnDeadlineAtMillis,
  amIOwner,
  onKick,
  ante,
  onSeatClick,
}: SeatLayoutProps) {
  const count = players.length;
  const positions = computeSeatPositions(SEAT_COUNT);
  const me = players.find((p) => p.id === myPlayerId);
  const mySeatIndex = me?.seatIndex ?? 0;

  // 이미 앉아있는 사람이 "이동"하려는 경우에만 핸드 진행 중이면 막는다 — 아직 안 앉은 사람의
  // "입장"은 언제나 허용된다(기존 중간 입장 정책과 동일).
  const handInProgress = phase !== null && phase !== 'SHOWDOWN';
  const seatClickAllowed = Boolean(onSeatClick) && (!me || !handInProgress);

  const [cooldownUntil, setCooldownUntil] = useState(0);
  const cooldownTimerRef = useRef<number | null>(null);
  const handleSeatClick = (seatIndex: number) => {
    if (!onSeatClick || Date.now() < cooldownUntil) {
      return;
    }
    const until = Date.now() + SEAT_CLICK_COOLDOWN_MS;
    setCooldownUntil(until);
    if (cooldownTimerRef.current !== null) {
      window.clearTimeout(cooldownTimerRef.current);
    }
    cooldownTimerRef.current = window.setTimeout(() => setCooldownUntil(0), SEAT_CLICK_COOLDOWN_MS);
    onSeatClick(seatIndex);
  };

  // 유일하게 칩이 가장 많은 사람에게만 왕관을 표시한다 — 전원 동률(예: 첫 판 시작 직후)이거나
  // 공동 1등이면 아무도 표시하지 않는다.
  const maxChips = players.length > 0 ? Math.max(...players.map((p) => p.chips)) : 0;
  const isUniqueChipLeader = players.filter((p) => p.chips === maxChips).length === 1;

  return (
    <>
      {Array.from({ length: SEAT_COUNT }, (_, seatIndex) => {
        const rotatedIndex = (seatIndex - mySeatIndex + SEAT_COUNT) % SEAT_COUNT;
        const position = positions[rotatedIndex];
        const player = players.find((p) => p.seatIndex === seatIndex);

        if (!player) {
          return (
            <EmptySeat
              key={`empty-${seatIndex}`}
              seatIndex={seatIndex}
              position={position}
              onClick={seatClickAllowed ? () => handleSeatClick(seatIndex) : undefined}
            />
          );
        }

        // computePosition은 "점유된 좌석만의 순서"(GameEngine.playerAtOffset과 동일한 리스트 순서)
        // 기준이라, 물리적 seatIndex가 아니라 players 배열 안에서의 인덱스를 넘겨야 한다.
        const listIndex = players.indexOf(player);
        return (
          <PlayerSeat
            key={player.id}
            player={player}
            position={position}
            tablePosition={computePosition(listIndex, dealerButtonPosition, count)}
            isMe={player.id === myPlayerId}
            isCurrentActor={player.id === currentActorId}
            showCards={showCards}
            isChipLeader={isUniqueChipLeader && player.chips === maxChips}
            showdownHand={showdownHands?.find((hand) => hand.playerId === player.id) ?? null}
            winnerCards={winnerCards}
            isWinner={winnerPlayerIds.includes(player.id)}
            handEnded={phase === 'SHOWDOWN'}
            dealtPlaceholderCount={dealProgress ? (dealProgress[player.id] ?? 0) : undefined}
            turnDeadlineAtMillis={turnDeadlineAtMillis}
            amIOwner={amIOwner}
            onKick={onKick}
            ante={ante}
          />
        );
      })}
    </>
  );
}
