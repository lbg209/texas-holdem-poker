import { useEffect, useState } from 'react';
import { formatMoney } from '../../lib/formatMoney';

const CHIP_PRESETS = [100, 500, 1000, 5000, 10000];

interface BetBuilderProps {
  minTotal: number;
  maxTotal: number;
  actionLabel: 'BET' | 'RAISE';
  onConfirm: (amount: number) => void;
}

// amount는 백엔드 계약대로 "이번 스트리트에 낼 총액"이다 — 증가분이 아니라 항상 최종 총액을 그대로 보낸다.
// 초기값은 최소 합법 총액(minTotal)이고, 칩 버튼은 여기에 더해진다 (예: 최소 2,000에서 +500 -> 2,500).
export function BetBuilder({ minTotal, maxTotal, actionLabel, onConfirm }: BetBuilderProps) {
  const [amount, setAmount] = useState(minTotal);

  // 상대 액션으로 최소 합법 총액이 바뀌면(예: 재레이즈) 빌더도 새 최소치로 다시 맞춘다.
  useEffect(() => {
    setAmount(minTotal);
  }, [minTotal]);

  const addChip = (chip: number) => setAmount((prev) => Math.min(prev + chip, maxTotal));
  const reset = () => setAmount(minTotal);

  return (
    <div className="flex flex-col items-center gap-2 rounded-lg bg-gradient-to-b from-slate-700 to-slate-900 p-3 shadow-inner">
      <span className="text-lg font-semibold text-amber-300 drop-shadow">{formatMoney(amount)}</span>
      <div className="flex flex-wrap justify-center gap-1">
        {CHIP_PRESETS.map((chip) => (
          <button
            key={chip}
            className="rounded bg-gradient-to-b from-slate-500 to-slate-700 px-2 py-1 text-xs shadow-md active:shadow-inner disabled:opacity-40"
            disabled={amount >= maxTotal}
            onClick={() => addChip(chip)}
          >
            +{formatMoney(chip)}
          </button>
        ))}
        <button
          className="rounded bg-gradient-to-b from-slate-400 to-slate-600 px-2 py-1 text-xs shadow-md active:shadow-inner"
          onClick={reset}
        >
          초기화
        </button>
      </div>
      <button
        className="rounded bg-gradient-to-b from-sky-500 to-sky-700 px-4 py-1.5 text-sm font-medium shadow-md active:shadow-inner"
        onClick={() => onConfirm(amount)}
      >
        {actionLabel === 'BET' ? '베팅' : '레이즈'} {formatMoney(amount)}
      </button>
    </div>
  );
}
