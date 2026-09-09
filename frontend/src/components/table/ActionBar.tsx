import { useRoom } from '../../state/RoomContext';
import { computeLegalActions } from '../../game/legalActions';
import { formatMoney } from '../../lib/formatMoney';
import { useCountdownSeconds } from '../../lib/useCountdownSeconds';
import { BetBuilder } from './BetBuilder';

// 백엔드 TurnTimerService.TURN_LIMIT_SECONDS와 맞춘 값 — 진행바 비율 계산에만 쓰인다.
const TURN_LIMIT_SECONDS = 60;
const URGENT_SECONDS_THRESHOLD = 10;

export function ActionBar() {
  const { state, sendAction } = useRoom();
  // roomState(진짜 최신 상태)가 아니라 displayState를 기준으로 판단한다 — 스트리트가 넘어가는
  // 애니메이션이 재생되는 동안에는 실제로 이미 다음 차례여도, 화면이 따라잡을 때까지 액션 버튼도
  // 같이 "얼려둬야" 미리 눌러도 애니메이션을 건너뛰고 즉시 처리돼버리는 문제가 없다.
  const displayState = state.displayState;
  // Hooks는 조건부 return보다 먼저, 항상 같은 순서로 호출되어야 한다 — displayState가 아직
  // 없을 수도 있으므로 옵셔널 체이닝으로 안전하게 넘긴다.
  const secondsLeft = useCountdownSeconds(displayState?.turnDeadlineAtMillis ?? null);
  if (!displayState) {
    return null;
  }

  const legal = computeLegalActions(displayState, state.myPlayerId);
  if (!legal) {
    // 핸드 종료 후 카드 공개/머크 결정은 ActionBar가 아니라 테이블 오른쪽의
    // ShowdownDecisionPanel이 담당한다(접근성 문제로 위치를 옮김).
    return (
      <div className="rounded-lg bg-gradient-to-b from-slate-800 to-slate-950 px-4 py-3 text-sm text-slate-400 shadow-xl">
        내 차례가 아닙니다.
      </div>
    );
  }

  return (
    <div className="flex flex-col items-end gap-3 rounded-lg bg-gradient-to-b from-slate-800 to-slate-950 p-3 shadow-xl">
      {secondsLeft !== null && (
        <div className="w-full">
          <div
            className={`text-right text-sm font-bold ${
              secondsLeft <= URGENT_SECONDS_THRESHOLD ? 'text-red-500' : 'text-slate-300'
            }`}
          >
            {secondsLeft}초
          </div>
          <div className="mt-1 h-1.5 w-full overflow-hidden rounded-full bg-slate-700">
            <div
              className={`h-full transition-[width] duration-1000 ease-linear ${
                secondsLeft <= URGENT_SECONDS_THRESHOLD ? 'bg-red-500' : 'bg-emerald-500'
              }`}
              style={{ width: `${(secondsLeft / TURN_LIMIT_SECONDS) * 100}%` }}
            />
          </div>
        </div>
      )}
      <div className="flex flex-wrap justify-end gap-2">
        <button
          className="rounded bg-gradient-to-b from-red-600 to-red-800 px-4 py-2 text-sm shadow-md active:shadow-inner"
          onClick={() => sendAction('FOLD', 0)}
        >
          폴드
        </button>
        {legal.canCheck && (
          <button
            className="rounded bg-gradient-to-b from-slate-500 to-slate-700 px-4 py-2 text-sm shadow-md active:shadow-inner"
            onClick={() => sendAction('CHECK', 0)}
          >
            체크
          </button>
        )}
        {legal.canCall && (
          <button
            className="rounded bg-gradient-to-b from-emerald-600 to-emerald-800 px-4 py-2 text-sm shadow-md active:shadow-inner"
            onClick={() => sendAction('CALL', 0)}
          >
            콜 {formatMoney(legal.callAmount)}
          </button>
        )}
        {legal.canAllIn && (
          <button
            className="rounded bg-gradient-to-b from-amber-600 to-amber-800 px-4 py-2 text-sm shadow-md active:shadow-inner"
            onClick={() => sendAction('ALL_IN', 0)}
          >
            올인 {formatMoney(legal.maxTotal)}
          </button>
        )}
      </div>

      {(legal.canBet || legal.canRaise) && (
        <BetBuilder
          minTotal={legal.minTotal}
          maxTotal={legal.maxTotal}
          actionLabel={legal.canBet ? 'BET' : 'RAISE'}
          onConfirm={(amount) => sendAction(legal.canBet ? 'BET' : 'RAISE', amount)}
        />
      )}
    </div>
  );
}
