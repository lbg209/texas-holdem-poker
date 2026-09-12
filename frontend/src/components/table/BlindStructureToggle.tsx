import { useState } from 'react';
import { useRoom } from '../../state/RoomContext';

// 다른 토글들과 한 행(App.tsx의 flex-col 컨테이너)에 나란히 놓이는 세 번째 on/off 패널 — 켜면
// 지금 몇 판째/몇 레벨인지와, 고정 6단계 블라인드 구조표 전체를 보여준다. 실제 대회에서 이걸
// "블라인드 구조(Blind Structure)"라고 부른다.
export function BlindStructureToggle() {
  const { state } = useRoom();
  const [open, setOpen] = useState(false);
  const roomState = state.roomState;

  if (!roomState) {
    return null;
  }

  return (
    <div>
      <button
        className="rounded bg-slate-800/90 px-2 py-1 text-xs text-slate-300 shadow hover:bg-slate-700"
        onClick={() => setOpen((prev) => !prev)}
      >
        📊 블라인드 구조
      </button>
      {open && (
        <div className="mt-1 w-56 rounded-lg bg-slate-800 p-3 text-sm shadow-lg">
          <p className="mb-2 font-semibold">
            {roomState.handsSinceBlindReset}판째 · Level {roomState.currentBlindLevel}
          </p>
          <table className="w-full text-xs text-slate-300">
            <thead>
              <tr className="text-slate-500">
                <th className="pb-1 text-left font-normal">레벨</th>
                <th className="pb-1 text-right font-normal">스몰/빅</th>
                <th className="pb-1 text-right font-normal">앤티</th>
              </tr>
            </thead>
            <tbody>
              {roomState.blindStructure.map((row) => {
                const isCurrent = row.level === roomState.currentBlindLevel;
                return (
                  <tr
                    key={row.level}
                    className={isCurrent ? 'rounded bg-emerald-900/60 font-semibold text-emerald-300' : ''}
                  >
                    <td className="py-0.5">Level {row.level}</td>
                    <td className="py-0.5 text-right">
                      {row.smallBlind.toLocaleString()}/{row.bigBlind.toLocaleString()}
                    </td>
                    <td className="py-0.5 text-right">{row.ante > 0 ? row.ante.toLocaleString() : '-'}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
