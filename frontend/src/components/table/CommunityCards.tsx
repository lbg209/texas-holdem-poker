import type { CardView } from '../../types/room';
import type { HighlightedCard } from '../../lib/handRank';
import { findHighlightColor } from '../../lib/handRank';
import { RevealCard } from './RevealCard';

interface CommunityCardsProps {
  cards: CardView[];
  highlightCards?: HighlightedCard[];
}

const NORMAL_FLIP_DELAY_MS = 150;
const NORMAL_FLIP_DURATION_MS = 350;
// 턴/리버는 뒷면으로 좀 더 오래 머물렀다가 천천히 뒤집혀서 긴장감을 준다. 플랍(첫 3장)만 빠르게.
const SLOW_FLIP_DELAY_MS = 500;
const SLOW_FLIP_DURATION_MS = 800;
// 텍사스 홀덤은 커뮤니티 카드가 항상 5장(플랍 3 + 턴 1 + 리버 1)이라 인덱스가 고정된다.
const FLOP_CARD_COUNT = 3;

export function CommunityCards({ cards, highlightCards }: CommunityCardsProps) {
  return (
    // 홀카드와 크기를 공유하는 Card 자체는 안 건드리고, 커뮤니티 카드 쪽만 살짝 축소한다
    // (중앙에 5장이 한 줄로 몰려서 너무 커 보인다는 피드백 — RulesPage의 scale-75 축소와 같은 패턴).
    <div className="flex scale-90 gap-1.5 sm:gap-2">
      {cards.map((card, i) => {
        const isSlow = i >= FLOP_CARD_COUNT;
        const color = highlightCards ? findHighlightColor(highlightCards, card) : null;
        return (
          // key가 인덱스라 배열에 새 카드가 추가될 때만 새로 마운트되고(핸드 중 카드는 줄거나
          // 순서가 바뀌지 않음), 그때 RevealCard의 딜인->뒤집기 연출이 한 번만 재생된다.
          <RevealCard
            key={i}
            card={card}
            highlighted={color !== null}
            highlightColor={color ?? undefined}
            flipDelayMs={isSlow ? SLOW_FLIP_DELAY_MS : NORMAL_FLIP_DELAY_MS}
            flipDurationMs={isSlow ? SLOW_FLIP_DURATION_MS : NORMAL_FLIP_DURATION_MS}
          />
        );
      })}
    </div>
  );
}
