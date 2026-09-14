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

interface ChipColor {
  base: string;
  border: string;
}

// 실제 카지노 칩 배색 — 더미(pile)가 여러 개일 때 첫째/둘째/셋째 줄을 각각 다른 색으로 구분한다
// (지금 최대 3더미까지 있음 — 위 TIERS 참고). 초록 테이블 위에서 눈에 잘 띄도록 초록 계열은 뺐다.
const PILE_COLORS: ChipColor[] = [
  { base: '#dc2626', border: 'rgba(127,29,29,0.6)' }, // 첫째 줄: 빨강
  { base: '#2563eb', border: 'rgba(30,58,138,0.6)' }, // 둘째 줄: 파랑
  { base: '#27272a', border: 'rgba(82,82,91,0.6)' }, // 셋째 줄: 검정
];

// 더미마다 살짝 다른 높이/기울기를 줘서, 격자처럼 딱 맞춰 정렬된 느낌 대신 대충 모아 쌓아둔 듯한
// 느낌을 낸다. Math.random 대신 고정된 값을 써서 리렌더될 때마다 모양이 바뀌지 않게 한다.
const PILE_TILT_DEG = [-4, 5, -3];
const PILE_OFFSET_Y = [0, -3, 2];

// 흰색 가장자리 점무늬(edge spots)가 있는 전형적인 겜블 칩 디자인. conic-gradient로 테두리에
// 굵은 원색/흰색 줄무늬를 만들고, 안쪽엔 단색 원판을 덮어서 테두리 무늬만 링 형태로 보이게 한다.
// 하이라이트/그림자로 원통형 입체감을 준다.
function Chip({ color }: { color: ChipColor }) {
  return (
    <div
      className="h-6 w-6 rounded-full"
      style={{
        border: `1px solid ${color.border}`,
        background: `
          radial-gradient(circle at 32% 26%, rgba(255,255,255,0.75), rgba(255,255,255,0) 42%),
          radial-gradient(circle, ${color.base} 50%, transparent 51%),
          repeating-conic-gradient(${color.base} 0deg 22.5deg, #ffffff 22.5deg 45deg)
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
    <motion.div layoutId={layoutId} className="flex items-end">
      {Array.from({ length: tier.piles }).map((_, pileIndex) => (
        <div
          key={pileIndex}
          // 더미끼리 gap 대신 음수 마진으로 겹치게 붙여서 한 뭉치처럼 보이게 한다.
          className={`flex flex-col-reverse ${pileIndex === 0 ? '' : '-ml-2.5'}`}
          style={{
            transform: `translateY(${PILE_OFFSET_Y[pileIndex % PILE_OFFSET_Y.length]}px) rotate(${PILE_TILT_DEG[pileIndex % PILE_TILT_DEG.length]}deg)`,
          }}
        >
          {Array.from({ length: tier.chipsPerPile }).map((_, chipIndex) => (
            <div key={chipIndex} className={chipIndex === 0 ? '' : '-mt-3.5'}>
              <Chip color={PILE_COLORS[pileIndex % PILE_COLORS.length]} />
            </div>
          ))}
        </div>
      ))}
    </motion.div>
  );
}
