import { useRoom } from '../../state/RoomContext';

const CONNECTION_LABEL: Record<string, string> = {
  idle: '대기',
  connecting: '연결 중',
  open: '연결됨',
  closed: '연결 끊김',
  reconnecting: '재연결 중',
};

export function TableHeader() {
  const { state, refreshState, startNewHand, reconnect, clearError } = useRoom();
  const myNickname = state.roomState?.players.find((p) => p.id === state.myPlayerId)?.nickname;

  return (
    <div className="mx-auto mb-4 max-w-3xl space-y-2">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-sm text-slate-400">
          {myNickname ?? '나'}
          {state.myPlayerId && (
            <span
              className="ml-1 rounded bg-slate-700 px-1 text-[10px] text-slate-300"
              title={state.myPlayerId}
            >
              [{state.myPlayerId.slice(0, 6)}]
            </span>
          )}
        </span>
        <div className="flex items-center gap-2">
          <span className="rounded bg-slate-700 px-2 py-1 text-xs">
            {CONNECTION_LABEL[state.connectionStatus] ?? state.connectionStatus}
          </span>
          {state.connectionStatus === 'closed' && (
            <button className="rounded bg-amber-600 px-3 py-1.5 text-sm" onClick={reconnect}>
              재연결
            </button>
          )}
          <button className="rounded bg-slate-700 px-3 py-1.5 text-sm" onClick={() => void refreshState()}>
            상태 새로고침
          </button>
          <button className="rounded bg-emerald-600 px-3 py-1.5 text-sm" onClick={() => void startNewHand()}>
            핸드 시작
          </button>
        </div>
      </div>

      {state.error && (
        <div className="flex items-center justify-between rounded bg-red-900/60 px-3 py-2 text-sm" aria-live="polite">
          <span>{state.error}</span>
          <button className="ml-3 text-red-300" onClick={clearError} aria-label="에러 메시지 닫기">
            ✕
          </button>
        </div>
      )}
    </div>
  );
}
