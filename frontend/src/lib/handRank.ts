import type { CardView, HandRank } from '../types/room';

const LABELS: Record<HandRank, string> = {
  HIGH_CARD: '하이카드',
  ONE_PAIR: '원페어',
  TWO_PAIR: '투페어',
  THREE_OF_A_KIND: '트리플',
  STRAIGHT: '스트레이트',
  FLUSH: '플러시',
  FULL_HOUSE: '풀하우스',
  FOUR_OF_A_KIND: '포카드',
  STRAIGHT_FLUSH: '스트레이트 플러시',
};

const ROYAL_RANKS = ['TEN', 'JACK', 'QUEEN', 'KING', 'ACE'];

// 백엔드는 로열플러시를 별도 값 없이 STRAIGHT_FLUSH(에이스 하이)로 통합해서 내려주므로,
// 베스트 5장이 정확히 10-J-Q-K-A인지로 프론트에서 구분해 더 특별하게 표시한다.
export function isRoyalFlush(handRank: HandRank, bestFive: CardView[]): boolean {
  if (handRank !== 'STRAIGHT_FLUSH' || bestFive.length !== 5) {
    return false;
  }
  const ranks = new Set(bestFive.map((card) => card.rank));
  return ROYAL_RANKS.every((rank) => ranks.has(rank));
}

export function getHandRankLabel(handRank: HandRank, bestFive: CardView[]): string {
  return isRoyalFlush(handRank, bestFive) ? '로열 플러시' : LABELS[handRank];
}

// 특별 취급할 등급(스트레이트 플러시 이상)인지 — 강조 스타일 적용 여부 판단용.
export function isSpecialHandRank(handRank: HandRank): boolean {
  return handRank === 'STRAIGHT_FLUSH';
}

// card가 cards 목록에 포함되는지 suit+rank로 판단한다(같은 카드는 무늬+숫자가 같으면 같은 카드).
export function containsCard(cards: CardView[], card: CardView): boolean {
  return cards.some((c) => c.suit === card.suit && c.rank === card.rank);
}

const RANK_VALUE: Record<string, number> = {
  TWO: 2,
  THREE: 3,
  FOUR: 4,
  FIVE: 5,
  SIX: 6,
  SEVEN: 7,
  EIGHT: 8,
  NINE: 9,
  TEN: 10,
  JACK: 11,
  QUEEN: 12,
  KING: 13,
  ACE: 14,
};

function groupByRank(cards: CardView[]): CardView[][] {
  const groups = new Map<string, CardView[]>();
  for (const card of cards) {
    const group = groups.get(card.rank) ?? [];
    group.push(card);
    groups.set(card.rank, group);
  }
  return [...groups.values()];
}

// bestFive 중 "족보를 실제로 결정한" 카드만 골라낸다. 원페어/트리플/포카드처럼 킥커가 붙는 족보는
// 만든 조합(페어/트리플/포카드) 카드만 강조하고, 킥커는 승부에 실제로 영향을 준 첫 번째(가장 높은) 것만
// 원페어에 한해 같이 강조한다 — 나머지 무관한 킥커까지 강조되면 "왜 이겼는지"가 헷갈리기 때문이다.
// 스트레이트/플러시/풀하우스/스트레이트플러시는 5장 전부가 조합 자체라 그대로 5장 다 강조한다.
export function getHighlightedCards(handRank: HandRank, bestFive: CardView[]): CardView[] {
  const groups = groupByRank(bestFive);

  switch (handRank) {
    case 'HIGH_CARD': {
      const top = [...bestFive].sort((a, b) => RANK_VALUE[b.rank] - RANK_VALUE[a.rank])[0];
      return top ? [top] : [];
    }
    case 'ONE_PAIR': {
      const pair = groups.find((g) => g.length === 2) ?? [];
      const kickers = groups.filter((g) => g.length === 1).map((g) => g[0]);
      const topKicker = kickers.sort((a, b) => RANK_VALUE[b.rank] - RANK_VALUE[a.rank])[0];
      return topKicker ? [...pair, topKicker] : pair;
    }
    case 'TWO_PAIR':
      return groups.filter((g) => g.length === 2).flat();
    case 'THREE_OF_A_KIND':
      return groups.find((g) => g.length === 3) ?? [];
    case 'FOUR_OF_A_KIND':
      return groups.find((g) => g.length === 4) ?? [];
    default:
      // STRAIGHT, FLUSH, FULL_HOUSE, STRAIGHT_FLUSH
      return bestFive;
  }
}
