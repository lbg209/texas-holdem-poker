import type { RoomStateResponse } from '../../types/room';
import { getHighlightedCardsForShowdown } from '../../lib/handRank';
import { resolveHandWinners } from '../../state/tableAnimation';
import { useRoom } from '../../state/RoomContext';
import type { ActiveVisualEvent } from '../../state/tableAnimation';
import { useShowBoard } from '../../lib/useShowBoard';
import { useSoundEffects } from '../../lib/useSoundEffects';
import { SeatLayout } from './SeatLayout';
import { CommunityCards } from './CommunityCards';
import { PotDisplay } from './PotDisplay';
import { FlyingChips } from './FlyingChips';
import { BetPiles } from './BetPiles';
import { DealingCards } from './DealingCards';
import { GameOverOverlay } from './GameOverOverlay';

interface PokerTableProps {
  // 액션 표시 -> 칩 이동 -> 카드 등장 순서로 지연 재생되는 상태. RoomContext가 큐를 한 번만
  // 돌려서 ActionBar와 같은 타이밍을 공유하도록, 여기서는 직접 큐를 만들지 않고 props로 받는다.
  displayState: RoomStateResponse;
  activeVisualEvent: ActiveVisualEvent | null;
  dealProgress: Record<string, number> | null;
  myPlayerId: string | null;
}

export function PokerTable({ displayState, activeVisualEvent, dealProgress, myPlayerId }: PokerTableProps) {
  const { kickPlayer, claimSeat, moveSeat } = useRoom();
  // ShowdownDecisionPanel(카드 공개 버튼 등)과 공유하는 "지금 보드를 보여줄지" 상태 — 자세한
  // 규칙은 useShowBoard 참고.
  const { showBoard, resultHoldActive } = useShowBoard(displayState);
  useSoundEffects(
    activeVisualEvent,
    myPlayerId,
    displayState.currentActorId,
    displayState.turnDeadlineAtMillis,
    displayState.winnerId,
  );
  const winnerPlayerIds = resolveHandWinners(displayState);
  const amIOwner = displayState.players.find((p) => p.id === myPlayerId)?.isOwner ?? false;
  // 승자가 실제로 족보를 만드는 데 쓴 카드만, 그 족보 등급에 맞는 색상과 함께 하이라이트한다 —
  // 같은 등급의 진 상대와 실제로 비교해서 몇 번째 킥커까지가 승부를 갈랐는지 계산하므로, 양쪽 다
  // 같은 1등 킥커를 가졌는데 2등 킥커에서 갈린 경우에도 진짜 결정타 킥커가 강조된다(무관한 1등
  // 킥커만 강조되지 않음). 스플릿팟이면 승자 전원의 합집합.
  const winnerCards =
    displayState.showdownHands
      ?.filter((hand) => hand.isWinner)
      .flatMap((hand) => getHighlightedCardsForShowdown(hand, displayState.showdownHands!)) ?? [];

  return (
    <div className="relative mx-auto mt-12 aspect-[16/10] w-full max-w-4xl rounded-[45%] border-8 border-emerald-950 bg-gradient-to-br from-emerald-700 via-emerald-800 to-emerald-950 shadow-[0_20px_50px_rgba(0,0,0,0.6),inset_0_0_70px_rgba(0,0,0,0.55)] sm:mt-16">
      {/* z-40: 팟 금액 텍스트는 근처를 지나가는 베팅 더미(BetPiles, z-20)나 날아가는 칩(FlyingChips, z-30)에
          가려지면 안 되므로 항상 그 위에 그린다. */}
      <div className="absolute left-1/2 top-1/2 z-40 flex -translate-x-1/2 -translate-y-1/2 flex-col items-center gap-2">
        <CommunityCards cards={showBoard ? displayState.communityCards : []} highlightCards={winnerCards} />
        {/* players도 함께 가려야 한다 — PotDisplay가 currentRoundBet 합계("+금액")를 players에서
            직접 계산해서, pots만 비워도 마지막 스트리트의 미정산 베팅액이 남아있으면 여전히 팟
            텍스트가 뜨는 문제가 있었다. */}
        <PotDisplay pots={showBoard ? displayState.pots : []} players={showBoard ? displayState.players : []} />
        {!showBoard && <span className="text-sm text-emerald-200">⏳ 게임 준비 중...</span>}
      </div>

      <SeatLayout
        players={displayState.players}
        myPlayerId={myPlayerId}
        dealerButtonPosition={displayState.dealerButtonPosition}
        currentActorId={displayState.currentActorId}
        showCards={showBoard}
        showdownHands={displayState.showdownHands}
        winnerCards={winnerCards}
        winnerPlayerIds={winnerPlayerIds}
        phase={displayState.phase}
        dealProgress={dealProgress}
        turnDeadlineAtMillis={displayState.turnDeadlineAtMillis}
        amIOwner={amIOwner}
        onKick={(targetId) => void kickPlayer(targetId)}
        ante={displayState.ante}
        onSeatClick={(seatIndex) => void (myPlayerId ? moveSeat(seatIndex) : claimSeat(seatIndex))}
      />

      {/* 대기 화면일 때는 마지막 스트리트의 베팅 더미(currentRoundBet)도 같이 가린다 — 안 그러면
          정산 전 칩 이미지가 좌석 옆에 남아있는 것처럼 보인다. */}
      <BetPiles players={showBoard ? displayState.players : []} myPlayerId={myPlayerId} />
      <FlyingChips event={activeVisualEvent} players={displayState.players} myPlayerId={myPlayerId} />
      <DealingCards event={activeVisualEvent} players={displayState.players} myPlayerId={myPlayerId} />

      {/* resultHoldActive가 끝난 뒤에만 뜬다 — 헤즈업 쇼다운처럼(상대가 머크해서 더 뒤집을 카드가
          없는 경우 등) 애니메이션 스텝이 거의 없어서 결과가 다음 프레임에 바로 반영되는 경우,
          결과를 볼 틈도 없이 오버레이가 곧장 테이블을 덮어버리는 버그가 있었다. */}
      {displayState.winnerId && !resultHoldActive && (
        <GameOverOverlay
          winnerNickname={displayState.winnerNickname ?? '알 수 없음'}
          resetAtMillis={displayState.gameOverResetAtMillis}
        />
      )}
    </div>
  );
}
