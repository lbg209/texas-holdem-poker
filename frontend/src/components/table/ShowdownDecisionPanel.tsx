import { useRoom } from '../../state/RoomContext';
import { useCountdownSeconds } from '../../lib/useCountdownSeconds';

// 백엔드 HeadsUpRevealTimerService.DECISION_TIME_LIMIT_SECONDS와 맞춘 값 — 진행바/색 전환에만 쓰인다.
const DECISION_TIME_LIMIT_SECONDS = 8;
const URGENT_SECONDS_THRESHOLD = 3;

// 핸드 종료 후 "카드를 공개할지" 결정하는 UI. 액션바(베팅 버튼)와는 별개로 테이블 오른쪽에
// 고정 배치한다 — 베팅 버튼 영역은 화면 아래쪽이라 접근성이 떨어진다는 피드백을 반영했다.
// 두 가지 경우를 다룬다:
//  1) 폴드로 이긴 핸드의 승자 — 카드를 자원해서 공개할지(안 하면 계속 비공개) 하나만 선택.
//  2) 헤즈업(2인) 쇼다운 — 무작위로 한 명은 이미 자동 공개됐고, 나머지 한 명(나)이 공개/머크를
//     제한 시간(8초) 안에 선택. 시간 초과면 서버가 알아서 강제 공개 처리한다.
export function ShowdownDecisionPanel() {
  const { state, revealHand, decideReveal } = useRoom();
  // ActionBar와 같은 이유로 displayState 기준 — 연출이 다 따라잡은 뒤에만 패널이 뜬다.
  const displayState = state.displayState;
  const secondsLeft = useCountdownSeconds(displayState?.headsUpRevealDeadlineAtMillis ?? null);
  const myId = state.myPlayerId;

  if (!displayState || !myId) {
    return null;
  }

  const isHeadsUpDecider = displayState.headsUpDeciderPlayerId === myId;
  const canRevealFoldWin = displayState.foldWinWinnerId === myId && !displayState.revealedPlayerIds.includes(myId);
  const isWaitingOnOpponentDecision =
    displayState.headsUpDeciderPlayerId !== null && displayState.headsUpDeciderPlayerId !== myId;

  if (!isHeadsUpDecider && !canRevealFoldWin && !isWaitingOnOpponentDecision) {
    return null;
  }

  return (
    <div className="flex w-48 flex-col items-center gap-2 rounded-lg bg-gradient-to-b from-slate-800 to-slate-950 p-3 text-center text-sm shadow-xl">
      {isHeadsUpDecider && (
        <>
          <span className="text-slate-200">상대 카드가 공개됐습니다!</span>
          <span className="text-xs text-slate-400">당신의 패를 공개할까요?</span>
          {secondsLeft !== null && (
            <>
              <span
                className={`text-lg font-bold ${secondsLeft <= URGENT_SECONDS_THRESHOLD ? 'text-red-500' : 'text-slate-300'}`}
              >
                {secondsLeft}초
              </span>
              <div className="h-1.5 w-full overflow-hidden rounded-full bg-slate-700">
                <div
                  className={`h-full transition-[width] duration-1000 ease-linear ${
                    secondsLeft <= URGENT_SECONDS_THRESHOLD ? 'bg-red-500' : 'bg-emerald-500'
                  }`}
                  style={{ width: `${(secondsLeft / DECISION_TIME_LIMIT_SECONDS) * 100}%` }}
                />
              </div>
            </>
          )}
          <div className="flex w-full gap-2">
            <button
              className="flex-1 rounded bg-gradient-to-b from-sky-600 to-sky-800 px-3 py-2 text-white shadow-md active:shadow-inner"
              onClick={() => void decideReveal(true)}
            >
              공개
            </button>
            <button
              className="flex-1 rounded bg-gradient-to-b from-slate-600 to-slate-800 px-3 py-2 text-white shadow-md active:shadow-inner"
              onClick={() => void decideReveal(false)}
            >
              머크
            </button>
          </div>
        </>
      )}

      {canRevealFoldWin && (
        <button
          className="w-full rounded bg-gradient-to-b from-sky-600 to-sky-800 px-3 py-2 text-white shadow-md active:shadow-inner"
          onClick={() => void revealHand()}
        >
          카드 공개
        </button>
      )}

      {isWaitingOnOpponentDecision && <span className="text-slate-400">상대가 공개 여부를 결정 중입니다...</span>}
    </div>
  );
}
