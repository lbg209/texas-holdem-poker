import type { RoomStateResponse } from '../../types/room';
import { getHighlightedCards } from '../../lib/handRank';
import { SeatLayout } from './SeatLayout';
import { CommunityCards } from './CommunityCards';
import { PotDisplay } from './PotDisplay';

interface PokerTableProps {
  roomState: RoomStateResponse;
  myPlayerId: string | null;
}

export function PokerTable({ roomState, myPlayerId }: PokerTableProps) {
  const handInProgress = roomState.phase !== null;
  // 승자가 실제로 족보를 만드는 데 쓴 카드만 하이라이트한다(무관한 킥커는 제외).
  // 스플릿팟이면 승자 전원의 합집합.
  const winnerCards =
    roomState.showdownHands
      ?.filter((hand) => hand.isWinner)
      .flatMap((hand) => getHighlightedCards(hand.handRank, hand.bestFive)) ?? [];

  return (
    <div className="relative mx-auto mt-24 aspect-[16/10] w-full max-w-3xl rounded-[45%] border-8 border-emerald-950 bg-gradient-to-br from-emerald-700 via-emerald-800 to-emerald-950 shadow-[0_20px_50px_rgba(0,0,0,0.6),inset_0_0_70px_rgba(0,0,0,0.55)] sm:mt-32">
      <div className="absolute left-1/2 top-1/2 flex -translate-x-1/2 -translate-y-1/2 flex-col items-center gap-2">
        <CommunityCards cards={roomState.communityCards} highlightCards={winnerCards} />
        <PotDisplay pots={roomState.pots} />
        {!handInProgress && <span className="text-sm text-emerald-200">참가자 대기 중</span>}
      </div>

      <SeatLayout
        players={roomState.players}
        myPlayerId={myPlayerId}
        dealerButtonPosition={roomState.dealerButtonPosition}
        currentActorId={roomState.currentActorId}
        showCards={handInProgress}
        showdownHands={roomState.showdownHands}
        winnerCards={winnerCards}
        phase={roomState.phase}
      />
    </div>
  );
}
