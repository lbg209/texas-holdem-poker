import type { PlayerView, PotView } from '../../types/room';
import { formatMoney } from '../../lib/formatMoney';
import { ChipStack } from './ChipStack';

interface PotDisplayProps {
  pots: PotView[];
  players: PlayerView[];
}

// 메인팟/사이드팟을 구분해서 보여주지 않고 하나의 합계로 보여준다(분배 계산 자체는 백엔드가
// 정확히 처리하고, 화면 표시만 단순화한다). pots는 백엔드가 매 응답마다 실시간으로 다시 계산해서
// 내려주기 때문에(팟이 실시간으로 안 보이던 예전 버그를 고치며 이렇게 됨) 이번 스트리트의 진행
// 중인 베팅액(각 플레이어의 currentRoundBet 합)까지 이미 포함돼 있다. 여기서 그 진행 중 베팅액을
// 한 번 더 빼서 "이미 걷힌(이전 스트리트까지의) 팟"만 중앙 이미지/숫자로 보여주고, 진행 중
// 베팅액은 "+금액"으로 옆에 따로 보여준다 — 안 그러면 좌석 앞 베팅 더미(BetPiles)와 같은 돈이
// 중앙에도 다시 반영돼 두 번 표시되는 것처럼 보인다. 라운드가 끝나 스윕되면 자연스럽게 settledTotal
// 쪽으로 합쳐지며 "+" 표시는 사라진다.
export function PotDisplay({ pots, players }: PotDisplayProps) {
  const liveTotal = pots.reduce((sum, pot) => sum + pot.amount, 0);
  const inProgressTotal = players.reduce((sum, p) => sum + p.currentRoundBet, 0);
  const settledTotal = liveTotal - inProgressTotal;

  if (settledTotal === 0 && inProgressTotal === 0) {
    return null;
  }

  return (
    <div className="flex flex-col items-center gap-1">
      <div className="scale-75">
        <ChipStack amount={settledTotal} />
      </div>
      <span className="rounded-full bg-gradient-to-b from-slate-800 to-slate-950 px-3 py-1 text-sm text-amber-300 shadow-md">
        팟 {formatMoney(settledTotal)}
        {inProgressTotal > 0 && <span className="text-sky-300"> +{formatMoney(inProgressTotal)}</span>}
      </span>
    </div>
  );
}
