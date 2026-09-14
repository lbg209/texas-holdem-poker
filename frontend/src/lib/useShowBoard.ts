import { useState } from 'react';
import type { RoomStateResponse } from '../types/room';
import { useCountdownSeconds } from './useCountdownSeconds';
import { RESULT_HOLD_MS } from '../state/tableAnimation';

// 핸드가 끝나면(다음 핸드 자동 시작 여부와 무관하게) 결과를 최소 RESULT_HOLD_MS만큼은 보여준다 —
// 그 뒤에도 자동 시작 카운트다운이 안 걸려 있으면(누군가 STOP을 눌러 멈춘 상태) 대기 화면으로
// 넘어간다. GAME OVER 오버레이도 이 시간만큼은 뜨지 않고 기다린다(아래 resultHoldActive 참고).
// 실제로 다음 핸드 데이터를 언제부터 재생할지는 useTableAnimationQueue가 같은 상수로 별도로
// 보류하므로, 여기서는 "결과 화면을 가리지 않는다"는 표시 역할만 한다.

export interface ShowBoardState {
  // "지금 화면에 보드(커뮤니티 카드/팟/좌석 카드/카드 공개 버튼 등 이번 핸드의 잔여물)를 보여줄지".
  showBoard: boolean;
  // 핸드가 막 끝난 뒤 결과 보여주기 창(RESULT_HOLD_MS)이 아직 활성 상태인지. GAME OVER 오버레이는
  // 이 값이 true인 동안은 뜨지 않는다 — 헤즈업 쇼다운처럼(상대가 머크해서 더 이상 새로 뒤집을 카드가
  // 없는 경우 등) 애니메이션 스텝이 거의/전혀 없어서 결과 상태(승자 하이라이트 등)가 다음 프레임에
  // 바로(딜레이 없이) 반영되는 경우, 오버레이가 결과를 볼 틈도 없이 곧바로 테이블을 덮어버리는
  // 문제가 있었다 — 항상 최소 RESULT_HOLD_MS만큼은 보드가 가려지지 않게 보장한다.
  resultHoldActive: boolean;
}

// PokerTable과 ShowdownDecisionPanel(그리고 GAME OVER 오버레이 노출 여부)이 공유하는 단일 소스.
// 이 컴포넌트들은 항상 함께 마운트/언마운트되므로(App.tsx에서 같은 조건으로 렌더링됨) 각자 이
// 훅을 호출해도 같은 displayState를 기준으로 항상 같은 값을 얻는다.
export function useShowBoard(displayState: RoomStateResponse | null): ShowBoardState {
  const phase = displayState?.phase ?? null;

  // "prop이 바뀌면 상태를 리셋"하는 React 공식 패턴(useEffect 대신 렌더링 도중 바로 setState) —
  // useEffect로 했더니 phase가 SHOWDOWN으로 막 바뀐 바로 그 렌더에서는 아직 resultHoldUntil이
  // 갱신되기 전이라 showBoard가 한 프레임 동안 false로 계산되는 순간이 있었다. 그 한 프레임 사이
  // 카드/좌석 블록이 통째로 언마운트→리마운트되면서, 헤즈업 쇼다운 카드 공개(플립) 애니메이션이
  // 끊기고 카드가 전부 이미 뒤집힌 채로 툭 나타나는 버그가 있었다 — 렌더링 도중 바로 상태를
  // 갱신하면 그 중간 프레임 자체가 화면에 그려지지 않고 바로 최신 상태로 리렌더되어 없어진다.
  const [prevPhase, setPrevPhase] = useState(phase);
  const [resultHoldUntil, setResultHoldUntil] = useState<number | null>(null);
  if (phase !== prevPhase) {
    if (phase === 'SHOWDOWN' && prevPhase !== 'SHOWDOWN') {
      setResultHoldUntil(Date.now() + RESULT_HOLD_MS);
    }
    setPrevPhase(phase);
  }

  const resultHoldSecondsLeft = useCountdownSeconds(resultHoldUntil);
  const resultHoldActive = (resultHoldSecondsLeft ?? 0) > 0;

  if (!displayState || phase === null) {
    return { showBoard: false, resultHoldActive: false };
  }
  // 핸드가 실제로 진행 중이거나, 결과 보여주기 창(위)이 아직 안 끝났거나, GAME OVER 화면(별도
  // 오버레이 + 자체 카운트다운)이면 보드를 보여준다. 그 외(핸드가 끝나고 결과도 충분히 보여줬는데
  // 누군가 레디를 꺼서 자동 진행이 멈춘 경우)는 첫 화면과 같은 "게임 준비 중" 대기 화면으로
  // 되돌린다 — 일부러 nextHandAtMillis(다음 핸드 자동 시작 카운트다운)는 조건에 넣지 않는다.
  // 한 번 대기 화면으로 넘어간 뒤 다시 레디해서 카운트다운이 새로 걸려도, 핸드가 진짜 시작되기
  // (phase가 SHOWDOWN을 벗어나기) 전까지는 이전 핸드의 카드/결과가 잠깐 다시 나타나는 게 마치
  // 버그처럼 보이기 때문 — 그 카운트다운 동안은 계속 대기 화면을 보여주다가, 새 핸드가 실제로
  // 시작되면 방금 만든 방에서 첫 판을 시작하는 것과 같은 화면으로 자연스럽게 넘어간다.
  const showBoard = phase !== 'SHOWDOWN' || resultHoldActive || displayState.winnerId !== null;
  return { showBoard, resultHoldActive };
}
