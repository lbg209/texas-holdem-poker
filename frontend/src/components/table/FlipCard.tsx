import { motion } from 'framer-motion';
import type { CardView } from '../../types/room';
import type { HighlightColor } from '../../lib/handRank';
import { Card } from './Card';

interface FlipCardProps {
  card: CardView;
  // RevealCard(마운트 시 한 번, 내부 타이머로만 앞면 전환)와 달리 외부에서 직접 제어한다 —
  // 홀카드 peek 토글처럼 "지금 앞면이어야 하는지"를 부모가 결정하는 경우에 쓴다.
  faceUp: boolean;
  flipDurationMs: number;
  highlighted?: boolean;
  highlightColor?: HighlightColor;
}

// CSS 3D(rotateY)로 카드를 뒤집는 핵심 메커니즘만 떼어낸 컴포넌트. RevealCard가 "마운트 후
// 한 번 자동으로 뒤집기"용으로 이걸 감싸서 쓰고, PlayerSeat의 내 카드 peek 토글은 이 컴포넌트를
// 직접 써서 faceUp을 그때그때 원하는 대로 넘긴다.
export function FlipCard({ card, faceUp, flipDurationMs, highlighted, highlightColor }: FlipCardProps) {
  return (
    <motion.div
      animate={{ rotateY: faceUp ? 180 : 0 }}
      transition={{ duration: flipDurationMs / 1000, ease: 'easeInOut' }}
      style={{ transformStyle: 'preserve-3d', position: 'relative', perspective: 1000 }}
    >
      <div style={{ backfaceVisibility: 'hidden' }}>
        <Card face="down" />
      </div>
      <div style={{ backfaceVisibility: 'hidden', transform: 'rotateY(180deg)', position: 'absolute', inset: 0 }}>
        <Card card={card} face="up" highlighted={highlighted} highlightColor={highlightColor} />
      </div>
    </motion.div>
  );
}
