import type { PlayerView, PotView } from '../../types/room';
import { formatMoney } from '../../lib/formatMoney';
import { ChipStack } from './ChipStack';

interface PotDisplayProps {
  pots: PotView[];
  players: PlayerView[];
}

// 메인팟/사이드팟을 구분해서 보여주지 않고 하나의 합계로 보여준다(분배 계산 자체는 백엔드가
// 정확히 처리하고, 화면 표시만 단순화한다). 이번 베팅턴에 아직 팟으로 쓸려 들어가지 않은
// 진행 중 베팅액(각 플레이어의 currentRoundBet 합)이 있으면 "+금액"으로 옆에 따로 보여주고,
// 라운드가 끝나 스윕되면 기존 합계에 더해지며 "+" 표시는 사라진다.
export function PotDisplay({ pots, players }: PotDisplayProps) {
  const baseTotal = pots.reduce((sum, pot) => sum + pot.amount, 0);
  const inProgressTotal = players.reduce((sum, p) => sum + p.currentRoundBet, 0);

  if (baseTotal === 0 && inProgressTotal === 0) {
    return null;
  }

  return (
    <div className="flex flex-col items-center gap-1">
      <div className="scale-75">
        <ChipStack amount={baseTotal} />
      </div>
      <span className="rounded-full bg-gradient-to-b from-slate-800 to-slate-950 px-3 py-1 text-sm text-amber-300 shadow-md">
        팟 {formatMoney(baseTotal)}
        {inProgressTotal > 0 && <span className="text-sky-300"> +{formatMoney(inProgressTotal)}</span>}
      </span>
    </div>
  );
}
