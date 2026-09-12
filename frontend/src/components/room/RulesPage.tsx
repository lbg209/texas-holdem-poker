import { Card } from '../table/Card';
import { getHandRankTextClassName, getHandRankTextPrefix, getHighlightColorForHandRank } from '../../lib/handRank';
import { HAND_RANKS } from '../../lib/handRankExamples';

interface RulesPageProps {
  onBack: () => void;
}

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
