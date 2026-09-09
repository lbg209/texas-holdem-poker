import { motion } from 'framer-motion';
import type { CardView } from '../../types/room';
import type { HighlightColor } from '../../lib/handRank';

interface CardProps {
  card?: CardView;
  face: 'up' | 'down';
  highlighted?: boolean;
  highlightColor?: HighlightColor;
}

// public/cards/svg-cards.svg (LGPL-2.1, 출처는 같은 폴더 README/LICENSE 참고)의 심볼 id 규칙.
const SVG_SUIT: Record<string, string> = {
  SPADE: 'spade',
  HEART: 'heart',
  DIAMOND: 'diamond',
  CLUB: 'club',
};

const SVG_RANK: Record<string, string> = {
  TWO: '2',
  THREE: '3',
  FOUR: '4',
  FIVE: '5',
  SIX: '6',
  SEVEN: '7',
  EIGHT: '8',
  NINE: '9',
  TEN: '10',
  JACK: 'jack',
  QUEEN: 'queen',
  KING: 'king',
  ACE: '1',
};

const CARDS_SPRITE = '/cards/svg-cards.svg';
const CARD_VIEW_BOX = '0 0 169.075 244.640';

// ring-inset: 링을 카드 바깥이 아니라 안쪽(테두리와 겹치는 자리)에 그려서, 카드가 커져도
// 테두리와 글로우가 뜬 것처럼 벌어져 보이지 않고 카드 가장자리에 딱 붙어 보이게 한다.
// 등급이 올라갈수록 색이 진해지고 광채(shadow blur) 레이어가 하나씩 늘어난다.
const HIGHLIGHT_STYLE: Record<HighlightColor, string> = {
  gold: 'border-yellow-400 shadow-[0_0_14px_rgba(250,204,21,0.85)] ring-[4px] ring-inset ring-yellow-400',
  blue: 'border-sky-400 shadow-[0_0_14px_rgba(56,189,248,0.85)] ring-[4px] ring-inset ring-sky-400',
  silver: 'border-slate-200 shadow-[0_0_16px_rgba(226,232,240,0.85)] ring-[4px] ring-inset ring-slate-200',
  orange: 'border-orange-400 shadow-[0_0_18px_rgba(251,146,60,0.9)] ring-[4px] ring-inset ring-orange-400',
  purple: 'border-purple-400 shadow-[0_0_20px_rgba(192,132,252,0.9)] ring-[4px] ring-inset ring-purple-400',
  special:
    'border-fuchsia-400 shadow-[0_0_22px_rgba(250,204,21,0.9),0_0_16px_rgba(217,70,239,0.75)] ring-[4px] ring-inset ring-yellow-300',
  royal:
    'border-yellow-200 shadow-[0_0_30px_rgba(250,204,21,1),0_0_20px_rgba(217,70,239,0.9),0_0_12px_rgba(56,189,248,0.8)] ring-[4px] ring-inset ring-yellow-100',
};

// 살짝 커졌다가 천천히 원래 크기로 돌아오는 승리 카드 펄스 — 등급이 올라갈수록 더 크게 튄다.
// 배열/객체 값이 매 렌더 새로 만들어져도 Framer Motion은 값(내용)이 같으면 재생을 다시 시작하지
// 않으므로, highlighted가 true로 유지되는 동안(부모가 리렌더돼도) 계속 반복 재생되지 않는다.
const WINNER_PULSE_SCALE: Record<HighlightColor, number[]> = {
  gold: [1, 1.25, 1],
  blue: [1, 1.25, 1],
  silver: [1, 1.25, 1],
  orange: [1, 1.28, 1],
  purple: [1, 1.32, 1],
  special: [1, 1.38, 1],
  royal: [1, 1.45, 1],
};
const WINNER_PULSE_TRANSITION = { duration: 0.7, times: [0, 0.25, 1], ease: 'easeOut' as const };

export function Card({ card, face, highlighted, highlightColor = 'gold' }: CardProps) {
  if (face === 'down' || !card) {
    return (
      <div className="h-28 w-20 overflow-hidden rounded border-[4px] border-transparent shadow-md sm:h-32 sm:w-24">
        <svg viewBox={CARD_VIEW_BOX} className="h-full w-full">
          <use href={`${CARDS_SPRITE}#back`} fill="#7f1d1d" />
        </svg>
      </div>
    );
  }

  const cardId = `${SVG_SUIT[card.suit]}_${SVG_RANK[card.rank]}`;

  return (
    <motion.div
      animate={{ scale: highlighted ? WINNER_PULSE_SCALE[highlightColor] : 1 }}
      transition={highlighted ? WINNER_PULSE_TRANSITION : { duration: 0.2 }}
      className={`h-28 w-20 overflow-hidden rounded border-[4px] shadow-md sm:h-32 sm:w-24 ${
        highlighted ? HIGHLIGHT_STYLE[highlightColor] : 'border-transparent'
      }`}
    >
      <svg viewBox={CARD_VIEW_BOX} className="h-full w-full">
        <use href={`${CARDS_SPRITE}#${cardId}`} />
      </svg>
    </motion.div>
  );
}
