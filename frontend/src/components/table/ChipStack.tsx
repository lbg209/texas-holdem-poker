import { motion } from 'framer-motion';

interface ChipStackProps {
  amount: number;
  // 주어지면 같은 layoutId를 가진 다른 ChipStack(예: FlyingChips)과의 전환을 Framer Motion이
  // 자동으로 위치/크기 보간 애니메이션(FLIP)으로 이어준다 — "이 더미가 그대로 이동한다"는 느낌.
  layoutId?: string;
}

interface Tier {
  max: number;
  piles: number;
  chipsPerPile: number;
}

// 실제 개수를 다 그리는 게 아니라, 금액 구간별로 "더미가 몇 개, 얼마나 높은지"만 등급으로 표현한다.
const TIERS: Tier[] = [
  { max: 1_000, piles: 1, chipsPerPile: 2 },
  { max: 10_000, piles: 1, chipsPerPile: 4 },
  { max: 30_000, piles: 2, chipsPerPile: 4 },
  { max: 50_000, piles: 2, chipsPerPile: 6 },
  { max: 100_000, piles: 3, chipsPerPile: 6 },
  { max: Infinity, piles: 3, chipsPerPile: 8 },
];

function pickTier(amount: number): Tier {
  return TIERS.find((tier) => amount <= tier.max) ?? TIERS[TIERS.length - 1];
}

// 빨간 바탕에 흰색 가장자리 점무늬(edge spots)가 있는 전형적인 겜블 칩 디자인.
// conic-gradient로 테두리에 굵은 빨강/흰색 줄무늬를 만들고, 안쪽엔 단색 원판을 덮어서
// 테두리 무늬만 링 형태로 보이게 한다. 하이라이트/그림자로 원통형 입체감을 준다.
function Chip() {
  return (
    <div
      className="h-6 w-6 rounded-full border border-red-950/60"
      style={{
        background: `
          radial-gradient(circle at 32% 26%, rgba(255,255,255,0.75), rgba(255,255,255,0) 42%),
          radial-gradient(circle, #dc2626 50%, transparent 51%),
          repeating-conic-gradient(#dc2626 0deg 22.5deg, #ffffff 22.5deg 45deg)
        `,
        boxShadow:
          '0 2px 3px rgba(0,0,0,0.5), inset 0 -2px 3px rgba(0,0,0,0.3), inset 0 1px 1px rgba(255,255,255,0.45)',
      }}
    />
  );
}

export function ChipStack({ amount, layoutId }: ChipStackProps) {
  if (amount <= 0) {
    return null;
  }

  const tier = pickTier(amount);

  return (
    <motion.div layoutId={layoutId} className="flex items-end gap-1.5">
      {Array.from({ length: tier.piles }).map((_, pileIndex) => (
        <div key={pileIndex} className="flex flex-col-reverse">
          {Array.from({ length: tier.chipsPerPile }).map((_, chipIndex) => (
            <div key={chipIndex} className={chipIndex === 0 ? '' : '-mt-3.5'}>
              <Chip />
            </div>
          ))}
        </div>
      ))}
    </motion.div>
  );
}
