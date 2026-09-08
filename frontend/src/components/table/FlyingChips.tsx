import { AnimatePresence, motion } from 'framer-motion';
import type { PlayerView } from '../../types/room';
import type { ActiveVisualEvent } from '../../state/tableAnimation';
import { getBetPilePosition, getCardPosition, getRotatedSeatPosition } from '../../lib/seatGeometry';
import { ChipStack } from './ChipStack';

interface FlyingChipsProps {
  event: ActiveVisualEvent | null;
  players: PlayerView[];
  myPlayerId: string | null;
}

const POT_POSITION = { leftPercent: 50, topPercent: 50 };

export function FlyingChips({ event, players, myPlayerId }: FlyingChipsProps) {
  if (event?.type === 'CHIPS_TO_POT') {
    // 베팅 더미(BetPiles)가 같은 layoutId를 가진 ChipStack을 팟 위치에 그리면, Framer Motion이
    // 좌석 옆 더미 -> 팟이라는 위치/크기 변화를 자동으로 보간해서 "더미가 그대로 이동"하는 것처럼 보인다.
    return (
      <AnimatePresence>
        {event.contributions.map(({ playerId, amount }) => (
          <div
            key={`chips-to-pot-${playerId}`}
            className="pointer-events-none absolute z-30 -translate-x-1/2 -translate-y-1/2 scale-75"
            style={{ left: `${POT_POSITION.leftPercent}%`, top: `${POT_POSITION.topPercent}%` }}
          >
            <ChipStack amount={amount} layoutId={`bet-pile-${playerId}`} />
          </div>
        ))}
      </AnimatePresence>
    );
  }

  if (event?.type === 'BLIND_FLOURISH') {
    // 실제 블라인드 베팅 더미(BetPiles)는 이 연출과 무관하게 그대로 자리에 남아있다(프리플랍
    // 베팅이 실제로 끝나야 진짜로 스윕됨) — 그래서 여기선 layoutId를 공유하지 않는, 순수 장식용
    // "그림자 복사본"만 중앙으로 날아갔다 사라지게 한다.
    return (
      <AnimatePresence>
        {event.contributions.map(({ playerId, amount }) => {
          const basePos = getRotatedSeatPosition(players, myPlayerId, playerId);
          if (!basePos) {
            return null;
          }
          const from = getBetPilePosition(getCardPosition(basePos));
          return (
            <motion.div
              key={`blind-flourish-${playerId}`}
              className="pointer-events-none absolute z-30 -translate-x-1/2 -translate-y-1/2 scale-75 opacity-70"
              initial={{ left: `${from.leftPercent}%`, top: `${from.topPercent}%`, opacity: 0.7 }}
              animate={{ left: `${POT_POSITION.leftPercent}%`, top: `${POT_POSITION.topPercent}%`, opacity: 0 }}
              transition={{ duration: 0.55, ease: 'easeInOut' }}
            >
              <ChipStack amount={amount} />
            </motion.div>
          );
        })}
      </AnimatePresence>
    );
  }

  if (event?.type === 'POT_TO_WINNERS') {
    // 팟 -> 승자 이동은 아직 간단한 점 하나로 표시한다.
    return (
      <AnimatePresence>
        {event.winnerPlayerIds.map((id) => {
          const basePos = getRotatedSeatPosition(players, myPlayerId, id);
          const to = basePos ? getCardPosition(basePos) : null;
          if (!to) {
            return null;
          }
          return (
            <motion.div
              key={`pot-to-winner-${id}`}
              className="pointer-events-none absolute z-30 h-4 w-4 rounded-full border border-red-950/60"
              style={{
                background:
                  'radial-gradient(circle at 32% 26%, rgba(255,255,255,0.8), rgba(255,255,255,0) 45%), #dc2626',
              }}
              initial={{ left: `${POT_POSITION.leftPercent}%`, top: `${POT_POSITION.topPercent}%`, opacity: 1 }}
              animate={{ left: `${to.leftPercent}%`, top: `${to.topPercent}%`, opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.5, ease: 'easeInOut' }}
            />
          );
        })}
      </AnimatePresence>
    );
  }

  return null;
}
