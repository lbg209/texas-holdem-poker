import type { CardView } from '../../types/room';

interface CardProps {
  card?: CardView;
  face: 'up' | 'down';
  highlighted?: boolean;
  highlightColor?: 'gold' | 'blue' | 'special';
}

const RED_SUITS = new Set(['HEART', 'DIAMOND']);

const HIGHLIGHT_STYLE: Record<'gold' | 'blue' | 'special', string> = {
  gold: 'border-yellow-400 shadow-[0_0_10px_rgba(250,204,21,0.85)] ring-2 ring-yellow-400',
  blue: 'border-sky-400 shadow-[0_0_10px_rgba(56,189,248,0.85)] ring-2 ring-sky-400',
  special:
    'border-fuchsia-400 shadow-[0_0_16px_rgba(250,204,21,0.9),0_0_10px_rgba(217,70,239,0.7)] ring-2 ring-yellow-300',
};

export function Card({ card, face, highlighted, highlightColor = 'gold' }: CardProps) {
  if (face === 'down' || !card) {
    return (
      <div className="flex h-16 w-11 items-center justify-center rounded border border-slate-500 bg-gradient-to-br from-slate-600 to-slate-800 shadow-md sm:h-20 sm:w-14">
        <div className="h-[70%] w-[70%] rounded-sm bg-gradient-to-br from-slate-400/30 to-transparent" />
      </div>
    );
  }

  const isRed = RED_SUITS.has(card.suit);

  return (
    <div
      className={`flex h-16 w-11 items-center justify-center rounded border bg-gradient-to-b from-white to-slate-100 sm:h-20 sm:w-14 ${
        highlighted ? HIGHLIGHT_STYLE[highlightColor] : 'border-slate-300 shadow-md'
      }`}
    >
      <span className={`text-sm font-bold sm:text-base ${isRed ? 'text-red-600' : 'text-slate-900'}`}>
        {card.display}
      </span>
    </div>
  );
}
