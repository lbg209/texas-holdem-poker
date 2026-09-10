import type { CardView, HandRank, ShowdownHandView } from '../types/room';

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

// 하이라이트 색상 = 족보 등급에 따른 화려함 단계. gold가 기본(하이카드~트리플)이고, 등급이
// 올라갈수록 특별해진다: cyan(스트레이트) < indigo(플러시) < orange(풀하우스) < purple(포카드) <
// special(스트레이트 플러시) < royal(로열 플러시). 스트레이트보다 플러시가 더 높은 족보라
// 2026-09-11에 둘을 분리했다(원래는 같은 색이었음). blue는 실제 게임에서는 안 쓰이고
// RulesPage(족보 설명) 예시에서만 임의로 쓰인다.
// (cyan은 원래 silver였는데, 카드가 흰 배경이라 거의 안 보인다는 피드백을 받고 2026-09-11에 바꿈.)
export type HighlightColor = 'gold' | 'blue' | 'cyan' | 'indigo' | 'orange' | 'purple' | 'special' | 'royal';

export function getHighlightColorForHandRank(handRank: HandRank, bestFive: CardView[]): HighlightColor {
  switch (handRank) {
    case 'STRAIGHT':
      return 'cyan';
    case 'FLUSH':
      return 'indigo';
    case 'FULL_HOUSE':
      return 'orange';
    case 'FOUR_OF_A_KIND':
      return 'purple';
    case 'STRAIGHT_FLUSH':
      return isRoyalFlush(handRank, bestFive) ? 'royal' : 'special';
    default:
      return 'gold';
  }
}

// 족보 이름 텍스트("풀하우스" 등)에 적용할 스타일 — 카드 하이라이트와 같은 등급 체계를 그대로 쓴다.
const HAND_RANK_TEXT_STYLE: Record<HighlightColor, string> = {
  gold: 'font-medium text-sky-300',
  blue: 'font-medium text-sky-300',
  cyan: 'font-semibold text-cyan-300',
  indigo: 'font-semibold text-indigo-300',
  orange: 'font-semibold text-orange-300',
  purple: 'font-bold text-purple-300',
  special: 'bg-gradient-to-r from-fuchsia-300 via-amber-200 to-yellow-300 bg-clip-text font-bold text-transparent drop-shadow',
  royal:
    'bg-gradient-to-r from-yellow-200 via-fuchsia-300 to-sky-300 bg-clip-text font-extrabold text-transparent drop-shadow-lg',
};

const HAND_RANK_TEXT_PREFIX: Partial<Record<HighlightColor, string>> = {
  purple: '🔥 ',
  special: '✨ ',
  royal: '👑✨ ',
};

export function getHandRankTextClassName(color: HighlightColor): string {
  return HAND_RANK_TEXT_STYLE[color];
}

export function getHandRankTextPrefix(color: HighlightColor): string {
  return HAND_RANK_TEXT_PREFIX[color] ?? '';
}

// 승자의 하이라이트 카드 하나와 그 카드에 적용할 색상(족보 등급)을 함께 담는다.
export interface HighlightedCard {
  card: CardView;
  color: HighlightColor;
}

// highlighted 목록 중 card와 같은 카드(무늬+숫자 일치)를 찾아 그 색상을 반환한다. 없으면 null
// (하이라이트 대상이 아니라는 뜻).
export function findHighlightColor(highlighted: HighlightedCard[], card: CardView): HighlightColor | null {
  const match = highlighted.find((h) => h.card.suit === card.suit && h.card.rank === card.rank);
  return match ? match.color : null;
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

// bestFive를 "동점 비교용 수열"로 바꾼다 — 앞쪽 원소일수록 승부에 더 결정적이다.
// 그룹(같은 랭크끼리)을 개수가 큰 순(포카드>트리플>페어>싱글) -> 개수가 같으면 랭크값 내림차순으로
// 정렬하면, 족보 종류를 따로 안 가려도 항상 올바른 타이브레이커 순서가 나온다.
// (원페어: [페어, 킥커, 킥커, 킥커] / 투페어: [높은페어, 낮은페어, 킥커] / 하이카드: 5장 내림차순 등)
function tiebreakSequence(bestFive: CardView[]): { rankValue: number; cards: CardView[] }[] {
  return groupByRank(bestFive)
    .map((cards) => ({ rankValue: RANK_VALUE[cards[0].rank], cards }))
    .sort((a, b) => (b.cards.length !== a.cards.length ? b.cards.length - a.cards.length : b.rankValue - a.rankValue));
}

// bestFive 중 "족보를 실제로 결정한" 카드만 골라내고, 그 족보 등급에 맞는 하이라이트 색상을
// 함께 붙여서 반환한다. 페어/트리플/포카드처럼 만든 조합(그룹 크기 2 이상) 자체는 항상 강조한다
// — 그 조합이 있어야 이 족보 등급 자체가 성립하기 때문이다. 킥커(그룹 크기 1)는 무조건 강조하지
// 않고, 같은 등급의 진 상대와 타이브레이커 수열을 "처음부터 순서대로" 비교해서 실제로 값이
// 갈리는 지점만 찾는다 — 그 지점이 킥커면 그 킥커를 강조 목록에 추가하고(조합 그룹은 이미
// 강조돼 있으므로 별도 처리 불필요), 그 상대와는 그 지점 이후를 더 비교하지 않는다(이미 승부가
// 갈렸으므로). 이렇게 처음부터 순서대로 비교해야 하는 이유: 예를 들어 투페어끼리 비교할 때
// "1등 페어는 같고 2등 페어 랭크 자체가 다른" 경우(예: AA+KK vs AA+1010), 남은 킥커(각각 10, K)만
// 따로 떼어 비교하면 마치 킥커 차이로 이긴 것처럼 잘못 강조되는데, 실제 승부는 2등 페어(KK vs
// 1010)에서 이미 갈렸으므로 킥커는 강조하면 안 된다 — 처음부터 순서대로 비교하다가 "조합 그룹"에서
// 먼저 갈리면 그걸로 끝(킥커는 비교조차 하지 않음)이라 이 문제가 없다. 비교할 같은 등급의 진 상대가
// 아예 없으면(예: 투페어가 원페어들을 이긴 경우처럼 등급 자체로 이긴 경우) 킥커는 승부에 전혀
// 관여하지 않았으므로 하나도 강조하지 않는다 — 만든 조합만 강조한다. 스트레이트/플러시/풀하우스/
// 스트레이트플러시는 5장 전부가 조합 자체라 비교 없이 그대로 다 강조한다.
// hand는 항상 승자의 패여야 한다(호출 측이 isWinner로 필터링) — 승자는 머크 대상이 아니라
// handRank/bestFive가 항상 채워져 있다고 가정한다. allHands 중 패자는 머크했으면 null일 수
// 있는데, 그 경우 비교 대상에서 제외한다 — 머크한 패와 정확히 왜 승부가 갈렸는지 보여주면
// 숨긴 패를 역으로 추측할 수 있게 되므로, 이건 머크 기능 자체의 의도된 한계다.
export function getHighlightedCardsForShowdown(hand: ShowdownHandView, allHands: ShowdownHandView[]): HighlightedCard[] {
  const handRank = hand.handRank;
  const bestFive = hand.bestFive;
  if (handRank === null || bestFive === null) {
    return [];
  }
  const color = getHighlightColorForHandRank(handRank, bestFive);
  const withColor = (cards: CardView[]): HighlightedCard[] => cards.map((card) => ({ card, color }));

  if (handRank === 'STRAIGHT' || handRank === 'FLUSH' || handRank === 'FULL_HOUSE' || handRank === 'STRAIGHT_FLUSH') {
    return withColor(bestFive);
  }

  const sequence = tiebreakSequence(bestFive);
  const comboCards = sequence.filter((g) => g.cards.length >= 2).flatMap((g) => g.cards);

  const losingSameRank = allHands.filter(
    (h): h is ShowdownHandView & { bestFive: CardView[] } => h.handRank === handRank && !h.isWinner && h.bestFive !== null,
  );
  const decisiveKickerIndexes = new Set<number>();

  for (const opponent of losingSameRank) {
    const opponentSequence = tiebreakSequence(opponent.bestFive);
    for (let i = 0; i < sequence.length; i++) {
      const mine = sequence[i]?.rankValue ?? -1;
      const theirs = opponentSequence[i]?.rankValue ?? -1;
      if (mine !== theirs) {
        if (sequence[i].cards.length === 1) {
          decisiveKickerIndexes.add(i);
        }
        break; // 이 상대와는 여기서 이미 승부가 갈렸으니, 그 뒤(조합이든 킥커든)는 이 상대 기준으론
        // 비교할 필요가 없다 — 조합 단계에서 갈렸다면 킥커는 애초에 비교 대상이 아니다.
      }
    }
  }

  const decisiveKickers = sequence
    .filter((g, i) => g.cards.length === 1 && decisiveKickerIndexes.has(i))
    .flatMap((g) => g.cards);
  return withColor([...comboCards, ...decisiveKickers]);
}
