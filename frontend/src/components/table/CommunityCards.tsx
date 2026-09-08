import type { CardView } from '../../types/room';
import { containsCard } from '../../lib/handRank';
import { Card } from './Card';

interface CommunityCardsProps {
  cards: CardView[];
  highlightCards?: CardView[];
}

export function CommunityCards({ cards, highlightCards }: CommunityCardsProps) {
  return (
    <div className="flex gap-1.5 sm:gap-2">
      {cards.map((card, i) => (
        <Card key={i} card={card} face="up" highlighted={highlightCards ? containsCard(highlightCards, card) : false} />
      ))}
    </div>
  );
}
