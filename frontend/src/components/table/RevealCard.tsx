import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import type { CardView } from '../../types/room';
import type { HighlightColor } from '../../lib/handRank';
import { FlipCard } from './FlipCard';

interface RevealCardProps {
  card: CardView;
  highlighted: boolean;
  highlightColor?: HighlightColor;
  flipDelayMs: number;
  flipDurationMs: number;
}

// 커뮤니티 카드가 새로 등장할 때: 먼저 뒷면으로 나타났다가(딜인), flipDelayMs 후 flipDurationMs
// 동안 앞면으로 뒤집힌다. 실제 뒤집기 메커니즘(CSS 3D rotateY)은 FlipCard가 담당하고, 여기서는
// "마운트 후 한 번만, 정해진 딜레이 뒤에 자동으로" 뒤집히는 타이밍만 관리한다 — 리버는 두 값을
// 더 크게 줘서(CommunityCards) 더 천천히, 긴장감 있게 공개되도록 한다.
export function RevealCard({ card, highlighted, highlightColor, flipDelayMs, flipDurationMs }: RevealCardProps) {
  const [faceUp, setFaceUp] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => setFaceUp(true), flipDelayMs);
    return () => window.clearTimeout(timer);
    // 마운트 시 한 번만 예약한다 — flipDelayMs는 카드마다 고정값이라 재실행할 이유가 없다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <motion.div
      initial={{ opacity: 0, y: -12, scale: 0.85 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      transition={{ duration: 0.25, ease: 'easeOut' }}
    >
      <FlipCard
        card={card}
        faceUp={faceUp}
        flipDurationMs={flipDurationMs}
        highlighted={highlighted}
        highlightColor={highlightColor}
      />
    </motion.div>
  );
}
