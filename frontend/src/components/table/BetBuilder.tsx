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
  // 쌓인 금액보다 큰 칩으로 빼려고 해도(예: 500원인 상태에서 -1,000) 음수로는 안 내려가고 0에서 멈춘다.
  const subtractChip = (chip: number) => setAmount((prev) => Math.max(prev - chip, 0));
  const reset = () => setAmount(0);

  const effectiveAmount = Math.max(amount, minTotal);
  const isMinimum = amount < minTotal;

  return (
    <div className="flex flex-col items-center gap-2 rounded-lg bg-gradient-to-b from-slate-700 to-slate-900 p-3 shadow-inner">
      <span className="text-lg font-semibold text-amber-300 drop-shadow">{formatMoney(amount)}</span>
      <div className="flex items-stretch gap-2">
        {/* w-14처럼 모든 버튼을 같은 고정 너비로 강제하면 열은 맞아도 작은 금액 버튼까지 커져서
            원래 크기가 망가진다. 대신 두 줄을 하나의 grid(5열)로 묶으면, 같은 열(같은 금액)의
            +/- 버튼끼리만 서로 폭을 맞추고(max-content) 다른 금액대의 버튼 크기는 원래처럼
            제각각 자기 내용물 크기를 유지한다. */}
        <div className="grid grid-cols-[repeat(5,max-content)] gap-1">
          {CHIP_PRESETS.map((chip) => (
            <button
              key={`add-${chip}`}
              className="rounded bg-gradient-to-b from-slate-500 to-slate-700 px-2 py-1 text-center text-xs shadow-md active:shadow-inner disabled:opacity-40"
              disabled={amount >= maxTotal}
              onClick={() => addChip(chip)}
            >
              +{formatMoney(chip)}
            </button>
          ))}
          {CHIP_PRESETS.map((chip) => (
            <button
              key={`sub-${chip}`}
              className="rounded bg-gradient-to-b from-slate-500 to-slate-700 px-2 py-1 text-center text-xs shadow-md active:shadow-inner disabled:opacity-40"
              disabled={amount <= 0}
              onClick={() => subtractChip(chip)}
            >
              -{formatMoney(chip)}
            </button>
          ))}
        </div>
        {/* items-stretch 덕분에 옆 두 줄(+/-)을 합친 높이만큼 자동으로 늘어난다. */}
        <button
          className="rounded bg-gradient-to-b from-slate-400 to-slate-600 px-3 text-sm font-medium shadow-md active:shadow-inner"
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
