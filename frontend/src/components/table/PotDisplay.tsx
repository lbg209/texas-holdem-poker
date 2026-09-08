import type { PotView } from '../../types/room';
import { formatMoney } from '../../lib/formatMoney';
import { ChipStack } from './ChipStack';

interface PotDisplayProps {
  pots: PotView[];
}

export function PotDisplay({ pots }: PotDisplayProps) {
  if (pots.length === 0) {
    return null;
  }

  const total = pots.reduce((sum, pot) => sum + pot.amount, 0);

  return (
    <div className="flex flex-col items-center gap-1">
      <ChipStack amount={total} />
      {pots.map((pot, i) => (
        <span
          key={i}
          className="rounded-full bg-gradient-to-b from-slate-800 to-slate-950 px-3 py-1 text-sm text-amber-300 shadow-md"
        >
          {pots.length > 1 ? (i === 0 ? '메인 팟' : `사이드 팟 ${i}`) : '팟'} {formatMoney(pot.amount)}
        </span>
      ))}
    </div>
  );
}
