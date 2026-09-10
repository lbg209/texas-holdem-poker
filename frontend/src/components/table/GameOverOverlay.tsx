import { useCountdownSeconds } from '../../lib/useCountdownSeconds';

interface GameOverOverlayProps {
  winnerNickname: string;
  resetAtMillis: number | null;
}

// 게임 종료(생존자 1명) 오버레이. 테이블 전체를 덮어 더 이상 진행 중인 핸드가 없음을 명확히 한다.
// displayState 기준으로만 렌더링되므로(호출 측 조건), 마지막 핸드의 팟 이동/승자 하이라이트
// 연출이 다 끝난 뒤에만 나타난다.
export function GameOverOverlay({ winnerNickname, resetAtMillis }: GameOverOverlayProps) {
  const secondsLeft = useCountdownSeconds(resetAtMillis);

  return (
    <div className="absolute inset-0 z-50 flex flex-col items-center justify-center gap-2 rounded-[45%] bg-slate-950/80 text-center">
      <span className="text-4xl">🏆</span>
      <span className="text-2xl font-bold tracking-wide text-yellow-400 drop-shadow">WINNER</span>
      <span className="text-lg font-semibold text-slate-100">{winnerNickname}</span>
      {secondsLeft !== null && (
        <span className="mt-2 text-sm text-slate-400">{secondsLeft}초 후 칩이 리필됩니다 (전원 레디하면 재대결)</span>
      )}
    </div>
  );
}
