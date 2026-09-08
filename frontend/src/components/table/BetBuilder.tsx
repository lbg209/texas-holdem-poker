import { useEffect, useState } from 'react';
import { formatMoney } from '../../lib/formatMoney';

const CHIP_PRESETS = [100, 500, 1000, 5000, 10000];

interface BetBuilderProps {
  minTotal: number;
  maxTotal: number;
  actionLabel: 'BET' | 'RAISE';
  onConfirm: (amount: number) => void;
}

// amount는 백엔드 계약대로 "이번 스트리트에 낼 총액"이 아니라, 칩 버튼으로 쌓아올린 "추가로 걸고
// 싶은 금액"이다(항상 0부터 시작 — 최소 총액에서 시작하면 +1,000을 눌러도 애매한 숫자가 나와
// 암산이 힘들다는 피드백으로 바꿈). 실제로 보낼 금액(effectiveAmount)은 확정 버튼에서만 계산한다:
// amount가 최소 총액(minTotal)보다 작으면 minTotal 그대로, 그 이상이면 amount 그대로 보낸다.
export function BetBuilder({ minTotal, maxTotal, actionLabel, onConfirm }: BetBuilderProps) {
  const [amount, setAmount] = useState(0);

  // 상대 액션으로 최소 합법 총액이 바뀌면(예: 재레이즈) 지금까지 쌓던 금액은 그 상황엔 안 맞을 수
  // 있으니 다시 0부터 시작한다.
  useEffect(() => {
    setAmount(0);
  }, [minTotal]);

  const addChip = (chip: number) => setAmount((prev) => Math.min(prev + chip, maxTotal));
  const reset = () => setAmount(0);

  const effectiveAmount = Math.max(amount, minTotal);
  const isMinimum = amount < minTotal;

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
        onClick={() => onConfirm(effectiveAmount)}
      >
        {actionLabel === 'BET' ? '베팅' : '레이즈'} {formatMoney(effectiveAmount)}
        {isMinimum && ' (최소)'}
      </button>
    </div>
  );
}
