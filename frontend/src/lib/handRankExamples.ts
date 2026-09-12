import type { CardView, HandRank } from '../types/room';

// 카드 랭크/무늬 기호("A♠" 등)를 백엔드 enum 이름(ACE/SPADE 등)으로 바꾼다 — Card 컴포넌트는
// CardView.rank/suit가 실제 enum 이름일 거라고 가정하고 SVG 심볼 id를 찾으므로, 기호 문자열을
// 그대로 넣으면(예전 버그) 매칭되는 심볼이 없어서 카드 그림이 아예 안 그려진다.
const RANK_SYMBOL_TO_ENUM: Record<string, string> = {
  A: 'ACE', K: 'KING', Q: 'QUEEN', J: 'JACK',
  '10': 'TEN', '9': 'NINE', '8': 'EIGHT', '7': 'SEVEN',
  '6': 'SIX', '5': 'FIVE', '4': 'FOUR', '3': 'THREE', '2': 'TWO',
};

const SUIT_SYMBOL_TO_ENUM: Record<string, string> = {
  '♠': 'SPADE', '♥': 'HEART', '♦': 'DIAMOND', '♣': 'CLUB',
};

function cards(...display: string[]): CardView[] {
  return display.map((d) => {
    const suitSymbol = d.slice(-1);
    const rankSymbol = d.slice(0, -1);
    return { suit: SUIT_SYMBOL_TO_ENUM[suitSymbol], rank: RANK_SYMBOL_TO_ENUM[rankSymbol], display: d };
  });
}

export interface HandRankExample {
  label: string;
  handRank: HandRank;
  example: CardView[];
  // 족보를 실제로 구성하는 카드 인덱스(강조 대상 — 색상은 handRank로 자동 결정돼 실제 게임과 항상
  // 같은 등급 체계를 쓴다). 투페어는 두 번째 페어만 별도(blue)로 강조해 구분을 보여준다
  // (blue는 실제 게임에서는 안 쓰이고 여기서만 교육용으로 쓴다 — lib/handRank.ts 참고).
  highlightIndices: number[];
  blueIndices?: number[];
}

// RulesPage(로비 진입 전 "게임 규칙")와 게임방 안의 HandRankToggle이 공유한다 — 족보 예시 카드를
// 두 군데서 따로 정의하면 나중에 등급 체계가 바뀔 때 하나를 빠뜨릴 수 있어서 한 곳에 모았다.
export const HAND_RANKS: HandRankExample[] = [
  { label: '하이카드', handRank: 'HIGH_CARD', example: cards('A♠', 'K♥', '9♦', '5♣', '2♠'), highlightIndices: [0] },
  { label: '원페어', handRank: 'ONE_PAIR', example: cards('K♠', 'K♥', '9♦', '5♣', '2♠'), highlightIndices: [0, 1] },
  {
    label: '투페어',
    handRank: 'TWO_PAIR',
    example: cards('K♠', 'K♥', '9♦', '9♣', '2♠'),
    highlightIndices: [0, 1],
    blueIndices: [2, 3],
  },
  { label: '트리플', handRank: 'THREE_OF_A_KIND', example: cards('K♠', 'K♥', 'K♦', '5♣', '2♠'), highlightIndices: [0, 1, 2] },
  { label: '스트레이트', handRank: 'STRAIGHT', example: cards('9♠', '8♥', '7♦', '6♣', '5♠'), highlightIndices: [0, 1, 2, 3, 4] },
  { label: '플러시', handRank: 'FLUSH', example: cards('A♠', 'J♠', '9♠', '5♠', '2♠'), highlightIndices: [0, 1, 2, 3, 4] },
  { label: '풀하우스', handRank: 'FULL_HOUSE', example: cards('K♠', 'K♥', 'K♦', '5♣', '5♠'), highlightIndices: [0, 1, 2, 3, 4] },
  { label: '포카드', handRank: 'FOUR_OF_A_KIND', example: cards('K♠', 'K♥', 'K♦', 'K♣', '5♠'), highlightIndices: [0, 1, 2, 3] },
  {
    label: '스트레이트 플러시',
    handRank: 'STRAIGHT_FLUSH',
    example: cards('9♠', '8♠', '7♠', '6♠', '5♠'),
    highlightIndices: [0, 1, 2, 3, 4],
  },
  {
    label: '로열 플러시',
    handRank: 'STRAIGHT_FLUSH',
    example: cards('A♠', 'K♠', 'Q♠', 'J♠', '10♠'),
    highlightIndices: [0, 1, 2, 3, 4],
  },
];
