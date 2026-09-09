import type { Phase, PlayerView, ShowdownHandView } from '../../types/room';
import type { HighlightedCard } from '../../lib/handRank';
import { computeSeatPositions } from './computeSeatPositions';
import { computePosition } from '../../lib/positions';
import { PlayerSeat } from './PlayerSeat';

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
}: SeatLayoutProps) {
  const count = players.length;
  const positions = computeSeatPositions(count);
  const myIndex = players.findIndex((p) => p.id === myPlayerId);

  // 유일하게 칩이 가장 많은 사람에게만 왕관을 표시한다 — 전원 동률(예: 첫 판 시작 직후)이거나
  // 공동 1등이면 아무도 표시하지 않는다.
  const maxChips = players.length > 0 ? Math.max(...players.map((p) => p.chips)) : 0;
  const isUniqueChipLeader = players.filter((p) => p.chips === maxChips).length === 1;

  return (
    <>
      {players.map((player, i) => {
        const rotatedIndex = myIndex === -1 ? i : (i - myIndex + count) % count;
        return (
          <PlayerSeat
            key={player.id}
            player={player}
            position={positions[rotatedIndex]}
            tablePosition={computePosition(i, dealerButtonPosition, count)}
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
          />
        );
      })}
    </>
  );
}
