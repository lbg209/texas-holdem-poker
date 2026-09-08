import { AnimatePresence, motion } from 'framer-motion';
import type { PlayerView } from '../../types/room';
import type { ActiveVisualEvent } from '../../state/tableAnimation';
import { getCardPosition, getRotatedSeatPosition } from '../../lib/seatGeometry';
import { Card } from './Card';

interface DealingCardsProps {
  event: ActiveVisualEvent | null;
  players: PlayerView[];
  myPlayerId: string | null;
}

const CENTER = { leftPercent: 50, topPercent: 50 };

// 딜링 중 카드 한 장이 중앙(덱 위치)에서 해당 좌석으로 날아가는 오버레이. 도착 즉시(스텝이 끝나면)
// PlayerSeat가 dealtPlaceholderCount로 그 자리에 뒷면 카드를 "고정" 표시하므로, 여기서는 순간적으로
// 지나가는 궤적만 보여주면 된다.
export function DealingCards({ event, players, myPlayerId }: DealingCardsProps) {
  const dealEvent = event?.type === 'DEAL_CARD' ? event : null;
  const basePos = dealEvent ? getRotatedSeatPosition(players, myPlayerId, dealEvent.playerId) : null;
  const to = basePos ? getCardPosition(basePos) : null;

  return (
    <AnimatePresence>
      {dealEvent && to && (
        <motion.div
          key={`deal-${dealEvent.sequence}`}
          className="pointer-events-none absolute z-30 -translate-x-1/2 -translate-y-1/2 scale-75"
          initial={{ left: `${CENTER.leftPercent}%`, top: `${CENTER.topPercent}%`, opacity: 1 }}
          animate={{ left: `${to.leftPercent}%`, top: `${to.topPercent}%`, opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.18, ease: 'easeOut' }}
        >
          <Card face="down" />
        </motion.div>
      )}
    </AnimatePresence>
  );
}
