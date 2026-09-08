import { useRoom } from '../../state/RoomContext';
import { computeLegalActions } from '../../game/legalActions';
import { formatMoney } from '../../lib/formatMoney';
import { BetBuilder } from './BetBuilder';

export function ActionBar() {
  const { state, sendAction } = useRoom();
  const roomState = state.roomState;
  if (!roomState) {
    return null;
  }

  const legal = computeLegalActions(roomState, state.myPlayerId);
  if (!legal) {
    return (
      <div className="rounded-lg bg-gradient-to-b from-slate-800 to-slate-950 px-4 py-3 text-sm text-slate-400 shadow-xl">
        내 차례가 아닙니다.
      </div>
    );
  }

  return (
    <div className="flex flex-col items-end gap-3 rounded-lg bg-gradient-to-b from-slate-800 to-slate-950 p-3 shadow-xl">
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
