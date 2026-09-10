import type { CardView, HandRank } from '../../types/room';
import { Card } from '../table/Card';
import { getHandRankTextClassName, getHandRankTextPrefix, getHighlightColorForHandRank } from '../../lib/handRank';

interface RulesPageProps {
  onBack: () => void;
}

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

interface HandRankExample {
  label: string;
  handRank: HandRank;
  example: CardView[];
  // 족보를 실제로 구성하는 카드 인덱스(강조 대상 — 색상은 handRank로 자동 결정돼 실제 게임과 항상
  // 같은 등급 체계를 쓴다). 투페어는 두 번째 페어만 별도(blue)로 강조해 구분을 보여준다
  // (blue는 실제 게임에서는 안 쓰이고 여기서만 교육용으로 쓴다 — lib/handRank.ts 참고).
  highlightIndices: number[];
  blueIndices?: number[];
}

const HAND_RANKS: HandRankExample[] = [
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

export function RulesPage({ onBack }: RulesPageProps) {
  return (
    <div className="mx-auto mt-12 max-w-2xl space-y-8 rounded-lg bg-slate-800 p-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">게임 규칙</h1>
        <button className="rounded bg-slate-700 px-3 py-1.5 text-sm" onClick={onBack}>
          뒤로
        </button>
      </div>

      <section>
        <h2 className="mb-2 text-lg font-medium text-emerald-400">기본 진행</h2>
        <ul className="list-disc space-y-1 pl-5 text-sm text-slate-300">
          <li>각자 홀카드 2장을 받고, 공용카드 5장(플랍 3장 → 턴 1장 → 리버 1장)이 순서대로 공개된다.</li>
          <li>쇼다운에서는 홀카드 2장 + 공용카드 5장 중 가장 좋은 5장 조합으로 승부를 가른다.</li>
          <li>베팅 라운드 순서: 프리플랍 → 플랍 → 턴 → 리버 → 쇼다운.</li>
        </ul>
      </section>

      <section>
        <h2 className="mb-3 text-lg font-medium text-emerald-400">족보 순위 (낮음 → 높음)</h2>
        <div className="space-y-2">
          {HAND_RANKS.map(({ label, handRank, example, highlightIndices, blueIndices }) => {
            // 실제 게임과 완전히 같은 소스(lib/handRank.ts)로 색상/텍스트 스타일을 정하므로,
            // 나중에 등급 체계가 또 바뀌어도 이 페이지가 따로 낡을 일이 없다.
            const color = getHighlightColorForHandRank(handRank, example);
            const isFancyRow = color === 'special' || color === 'royal';
            return (
              <div
                key={label}
                className={`flex items-center gap-3 rounded p-2 ${
                  isFancyRow ? 'bg-gradient-to-r from-fuchsia-950/50 via-slate-900/60 to-yellow-950/50' : 'bg-slate-900/60'
                }`}
              >
                <span className={`w-28 shrink-0 text-sm ${getHandRankTextClassName(color)}`}>
                  {getHandRankTextPrefix(color)}
                  {label}
                </span>
                <div className="flex gap-1">
                  {example.map((card, i) => {
                    const isHighlighted = highlightIndices.includes(i);
                    const isBlue = blueIndices?.includes(i) ?? false;
                    return (
                      <div key={i} className="origin-left scale-75">
                        <Card
                          card={card}
                          face="up"
                          highlighted={isHighlighted || isBlue}
                          highlightColor={isBlue ? 'blue' : color}
                        />
                      </div>
                    );
                  })}
                </div>
              </div>
            );
          })}
        </div>
      </section>

      <section>
        <h2 className="mb-2 text-lg font-medium text-emerald-400">베팅 규칙</h2>
        <ul className="list-disc space-y-1 pl-5 text-sm text-slate-300">
          <li><b>체크(CHECK)</b>: 맞출 금액이 없을 때 아무것도 걸지 않고 차례를 넘긴다.</li>
          <li><b>콜(CALL)</b>: 현재 베팅액만큼 맞춘다. 가진 칩보다 부족하면 그만큼만 내고 올인 처리된다.</li>
          <li><b>벳(BET)</b>: 이번 스트리트에 아직 베팅이 없을 때 처음으로 건다.</li>
          <li><b>레이즈(RAISE)</b>: 기존 베팅보다 더 많이 건다. 최소 레이즈 금액은 직전 베팅/레이즈의 증가폭 이상이어야 한다.</li>
          <li><b>폴드(FOLD)</b>: 패를 포기하고 이번 핸드에서 빠진다.</li>
          <li><b>올인(ALL-IN)</b>: 보유한 칩 전부를 건다.</li>
          <li>베팅/레이즈 금액은 "이번 스트리트에 낼 총액"이며, 100 단위로만 걸 수 있다(단, 보유 칩 전부를 거는 올인은 예외).</li>
          <li>스몰블라인드 100 / 빅블라인드 200, 시작 칩 30,000이 기본 설정이다.</li>
          <li>여러 명이 서로 다른 액수로 올인하면 사이드팟이 나뉘어, 적게 낸 사람은 자신이 낸 만큼의 팟에서만 승부한다.</li>
        </ul>
      </section>
    </div>
  );
}
