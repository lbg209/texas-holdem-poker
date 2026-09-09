import { useRoom } from '../../state/RoomContext';
import { useCountdownSeconds } from '../../lib/useCountdownSeconds';

const CONNECTION_LABEL: Record<string, string> = {
  idle: '대기',
  connecting: '연결 중',
  open: '연결됨',
  closed: '연결 끊김',
  reconnecting: '재연결 중',
};

export function TableHeader() {
  const { state, refreshState, toggleReady, reconnect, clearError } = useRoom();
  const roomState = state.roomState;
  const myNickname = roomState?.players.find((p) => p.id === state.myPlayerId)?.nickname;
  // 게임 종료(생존자 1명) 여부/레디 상태/다음 핸드 자동시작 카운트다운 모두 연출 지연 없이 실제
  // 최신 상태(roomState) 기준으로 즉시 반영한다 — 애니메이션이 아직 재생 중이어도 실제 사실은
  // 이미 그렇기 때문(핸드 시작을 더 못 하거나, 곧 자동으로 다음 핸드가 시작되거나).
  const gameOver = roomState?.winnerId != null;
  const myPlayer = roomState?.players.find((p) => p.id === state.myPlayerId);
  const myReady = myPlayer?.ready ?? false;
  // 파산한 플레이어는 더 이상 핸드에 참여하지 않으므로 "전원 레디" 집계에서도 제외한다
  // (백엔드의 GameEngine.isWaitingForNextHandWithEveryoneReady와 같은 기준).
  const contenders = roomState?.players.filter((p) => p.status !== 'BUSTED') ?? [];
  const readyCount = contenders.filter((p) => p.ready).length;
  const nextHandSecondsLeft = useCountdownSeconds(roomState?.nextHandAtMillis ?? null);

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
          {!gameOver && contenders.length > 0 && (
            <>
              <span className="text-xs text-slate-400">
                준비 {readyCount}/{contenders.length}
                {nextHandSecondsLeft !== null && (
                  <span className="ml-1 font-semibold text-emerald-400">· {nextHandSecondsLeft}초 후 자동 시작</span>
                )}
              </span>
              <button
                className={`rounded px-3 py-1.5 text-sm ${
                  myReady ? 'bg-emerald-600 hover:bg-emerald-700' : 'bg-slate-700 hover:bg-slate-600'
                }`}
                onClick={() => void toggleReady(!myReady)}
              >
                {myReady ? '레디 취소' : '레디'}
              </button>
            </>
          )}
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
