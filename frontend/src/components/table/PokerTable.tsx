import type { RoomStateResponse } from '../../types/room';
import { getHighlightedCardsForShowdown } from '../../lib/handRank';
import { resolveHandWinners } from '../../state/tableAnimation';
import type { ActiveVisualEvent } from '../../state/tableAnimation';
import { SeatLayout } from './SeatLayout';
import { CommunityCards } from './CommunityCards';
import { PotDisplay } from './PotDisplay';
import { FlyingChips } from './FlyingChips';
import { BetPiles } from './BetPiles';
import { DealingCards } from './DealingCards';

interface PokerTableProps {
  // 액션 표시 -> 칩 이동 -> 카드 등장 순서로 지연 재생되는 상태. RoomContext가 큐를 한 번만
  // 돌려서 ActionBar와 같은 타이밍을 공유하도록, 여기서는 직접 큐를 만들지 않고 props로 받는다.
  displayState: RoomStateResponse;
  activeVisualEvent: ActiveVisualEvent | null;
  dealProgress: Record<string, number> | null;
  myPlayerId: string | null;
}

export function PokerTable({ displayState, activeVisualEvent, dealProgress, myPlayerId }: PokerTableProps) {
  const handInProgress = displayState.phase !== null;
  const winnerPlayerIds = resolveHandWinners(displayState);
  // 승자가 실제로 족보를 만드는 데 쓴 카드만 하이라이트한다 — 같은 등급의 진 상대와 실제로
  // 비교해서 몇 번째 킥커까지가 승부를 갈랐는지 계산하므로, 양쪽 다 같은 1등 킥커를 가졌는데
  // 2등 킥커에서 갈린 경우에도 진짜 결정타 킥커가 강조된다(무관한 1등 킥커만 강조되지 않음).
  // 스플릿팟이면 승자 전원의 합집합.
  const winnerCards =
    displayState.showdownHands
      ?.filter((hand) => hand.isWinner)
      .flatMap((hand) => getHighlightedCardsForShowdown(hand, displayState.showdownHands!)) ?? [];

  return (
    <div className="relative mx-auto mt-12 aspect-[16/10] w-full max-w-4xl rounded-[45%] border-8 border-emerald-950 bg-gradient-to-br from-emerald-700 via-emerald-800 to-emerald-950 shadow-[0_20px_50px_rgba(0,0,0,0.6),inset_0_0_70px_rgba(0,0,0,0.55)] sm:mt-16">
      {/* z-40: 팟 금액 텍스트는 근처를 지나가는 베팅 더미(BetPiles, z-20)나 날아가는 칩(FlyingChips, z-30)에
          가려지면 안 되므로 항상 그 위에 그린다. */}
      <div className="absolute left-1/2 top-1/2 z-40 flex -translate-x-1/2 -translate-y-1/2 flex-col items-center gap-2">
        <CommunityCards cards={displayState.communityCards} highlightCards={winnerCards} />
        <PotDisplay pots={displayState.pots} players={displayState.players} />
        {!handInProgress && <span className="text-sm text-emerald-200">참가자 대기 중</span>}
      </div>

      <SeatLayout
        players={displayState.players}
        myPlayerId={myPlayerId}
        dealerButtonPosition={displayState.dealerButtonPosition}
        currentActorId={displayState.currentActorId}
        showCards={handInProgress}
        showdownHands={displayState.showdownHands}
        winnerCards={winnerCards}
        winnerPlayerIds={winnerPlayerIds}
        phase={displayState.phase}
        dealProgress={dealProgress}
      />

      <BetPiles players={displayState.players} myPlayerId={myPlayerId} />
      <FlyingChips event={activeVisualEvent} players={displayState.players} myPlayerId={myPlayerId} />
      <DealingCards event={activeVisualEvent} players={displayState.players} myPlayerId={myPlayerId} />
    </div>
  );
}
