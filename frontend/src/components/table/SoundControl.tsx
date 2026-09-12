import { useRef, useState } from 'react';
import type { ChangeEvent } from 'react';
import { getSoundVolume, isSoundMuted, setSoundMuted, setSoundVolume } from '../../lib/sounds';

// 버튼과 볼륨 바 사이 작은 틈을 마우스가 지나가는 순간(대각선으로 움직이거나 살짝만 벗어나도)
// CSS group-hover만으로는 순간적으로 hover가 끊겨서 바로 사라져버리는 문제가 있었다 — 그래서 JS로
// 직접 관리한다: 버튼이든 바든 그 위에 있는 동안은 계속 열려 있고, 완전히 벗어나도 바로 안 닫히고
// 이 시간만큼 유예를 둬서, 그사이 다시 들어오면(둘 사이 틈을 지나가는 것 포함) 안 닫힌다.
const HIDE_DELAY_MS = 1500;

// 닉네임 [ID] 배지 옆에 놓이는 음소거 버튼 — 클릭하면 켜기/끄기, 마우스를 올리면(hover) 오른쪽에
// 볼륨 조절 바가 나타난다. 음소거(on/off)와 볼륨(0~100%)은 서로 다른 저장값이다 — 음소거는 볼륨
// 크기와 무관하게 전체를 끄고 켜는 스위치, 볼륨은 "켜져 있을 때 얼마나 크게 들릴지"만 조절한다.
export function SoundControl() {
  const [muted, setMuted] = useState(() => isSoundMuted());
  const [volume, setVolume] = useState(() => Math.round(getSoundVolume() * 100));
  const [open, setOpen] = useState(false);
  const hideTimerRef = useRef<number | null>(null);

  const cancelHide = () => {
    if (hideTimerRef.current !== null) {
      window.clearTimeout(hideTimerRef.current);
      hideTimerRef.current = null;
    }
  };

  const handleEnter = () => {
    cancelHide();
    setOpen(true);
  };

  const handleLeave = () => {
    cancelHide();
    hideTimerRef.current = window.setTimeout(() => setOpen(false), HIDE_DELAY_MS);
  };

  const toggleMuted = () => {
    const next = !muted;
    setSoundMuted(next);
    setMuted(next);
  };

  const handleVolumeChange = (e: ChangeEvent<HTMLInputElement>) => {
    const next = Number(e.target.value);
    setVolume(next);
    setSoundVolume(next / 100);
  };

  return (
    <span className="relative inline-flex items-center">
      <button
        type="button"
        className="ml-1 rounded bg-slate-700 px-1.5 py-0.5 text-xs hover:bg-slate-600"
        onClick={toggleMuted}
        onMouseEnter={handleEnter}
        onMouseLeave={handleLeave}
        aria-label={muted ? '효과음 켜기' : '효과음 끄기'}
        title={muted ? '효과음 켜기' : '효과음 끄기'}
      >
        {muted ? '🔇' : '🔊'}
      </button>
      {open && (
        <div
          className="absolute left-full top-1/2 z-30 ml-1 flex -translate-y-1/2 items-center gap-1.5 whitespace-nowrap rounded bg-slate-700 px-2 py-1 shadow-lg"
          onMouseEnter={handleEnter}
          onMouseLeave={handleLeave}
        >
          <input
            type="range"
            min={0}
            max={100}
            value={volume}
            onChange={handleVolumeChange}
            className="w-20 accent-emerald-500"
            aria-label="효과음 볼륨"
          />
          <span className="w-8 text-right text-[10px] text-slate-300">{volume}%</span>
        </div>
      )}
    </span>
  );
}
