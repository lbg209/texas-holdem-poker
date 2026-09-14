import { useState } from 'react';
import { useRoom } from '../../state/RoomContext';

// 좌측 상단의 작은 on/off 토글(다른 토글과 한 행에 나란히 놓임, 배치는 App.tsx가 담당) — 켜면
// 방 제목/시작 칩/빅블라인드/방 코드를 보여준다. 방 코드를 다른 사람에게 공유할 때 쓴다.
export function RoomInfoToggle() {
  const { state } = useRoom();
  const [open, setOpen] = useState(false);
  const roomState = state.roomState;

  if (!roomState) {
    return null;
  }

  return (
    <div>
      <button
        className="rounded bg-slate-800/90 px-3 py-1.5 text-sm text-slate-300 shadow hover:bg-slate-700"
        onClick={() => setOpen((prev) => !prev)}
      >
        ℹ️ 방 정보
      </button>
      {open && (
        <div className="mt-1 w-56 rounded-lg bg-slate-800 p-3 text-sm shadow-lg">
          <p className="mb-2 font-semibold">{roomState.isPrivate ? `🔒 ${roomState.name}` : roomState.name}</p>
          <dl className="space-y-1 text-xs text-slate-300">
            <div className="flex justify-between">
              <dt className="text-slate-500">방 코드</dt>
              <dd className="font-mono">{roomState.roomCode}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">시작 칩</dt>
              <dd>{roomState.startingChips.toLocaleString()}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">블라인드</dt>
              <dd>{roomState.smallBlind.toLocaleString()} / {roomState.bigBlind.toLocaleString()}</dd>
            </div>
            {/* 판 수 기준 블라인드 상승이 마지막 단계에 도달해야만 걷히므로, 그 전까지는 0이라 줄
                자체를 안 보여준다. */}
            {roomState.ante > 0 && (
              <div className="flex justify-between">
                <dt className="text-slate-500">앤티</dt>
                <dd>{roomState.ante.toLocaleString()}</dd>
              </div>
            )}
          </dl>
        </div>
      )}
    </div>
  );
}
