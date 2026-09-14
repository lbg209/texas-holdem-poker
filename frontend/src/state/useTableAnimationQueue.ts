import { useEffect, useRef, useState } from 'react';
import type { RoomStateResponse } from '../types/room';
import { deriveSteps, RESULT_HOLD_MS } from './tableAnimation';
import type { ActiveVisualEvent, AnimationStep } from './tableAnimation';

interface TableAnimationQueue {
  displayState: RoomStateResponse | null;
  activeVisualEvent: ActiveVisualEvent | null;
  // 새 핸드 딜링 중에만 값이 있다(좌석별로 지금까지 뒷면으로 몇 장 놓였는지). 실제 홀카드 데이터와는
  // 무관한 연출 전용 카운터 — 딜링이 끝나면(리빌 스텝에서) null로 돌아간다.
  dealProgress: Record<string, number> | null;
}

// roomState(서버가 보낸 진짜 최신 상태)는 즉시 갱신되지만, 화면에 실제로 그려지는(그리고 "지금 내
// 차례인지" 판단에도 쓰이는) displayState는 이 훅이 관리하는 큐를 통해 딜링 → 액션 표시 → 칩 이동 →
// 카드 등장 순서로 지연 재생된다. RoomContext에서 한 번만 호출해서 PokerTable/ActionBar가 같은
// displayState를 보게 해야 한다 — 각자 따로 호출하면 서로 다른 타이밍으로 어긋난다.
// roomState가 큐 재생 중에 또 바뀌면(빠른 연타) 하나만 대기시키고, 그마저도 밀리면(대기 중이던 걸
// 덮어쓰게 되면) 다음 사이클은 애니메이션 없이 최신 상태로 바로 스냅한다.
export function useTableAnimationQueue(roomState: RoomStateResponse | null): TableAnimationQueue {
  const [displayState, setDisplayState] = useState(roomState);
  const [activeVisualEvent, setActiveVisualEvent] = useState<ActiveVisualEvent | null>(null);
  const [dealProgress, setDealProgress] = useState<Record<string, number> | null>(null);

  const settledRef = useRef(roomState);
  const pendingRef = useRef<RoomStateResponse | null>(null);
  const droppedRef = useRef(false);
  const playingRef = useRef(false);
  // 쇼다운 공개 연출이 끝난 직후 RESULT_HOLD_MS 동안 true — playingRef와 마찬가지로 이 동안
  // 들어오는 새 상태는 즉시 재생하지 않고 pendingRef에 대기시킨다(아래 holdResult 참고).
  const holdingResultRef = useRef(false);
  const timeoutRef = useRef<number | null>(null);
  const holdTimeoutRef = useRef<number | null>(null);

  useEffect(
    () => () => {
      if (timeoutRef.current !== null) {
        window.clearTimeout(timeoutRef.current);
      }
      if (holdTimeoutRef.current !== null) {
        window.clearTimeout(holdTimeoutRef.current);
      }
    },
    [],
  );

  useEffect(() => {
    if (roomState === settledRef.current) {
      return;
    }

    // roomState가 null이거나(참가 전/초기화) settledRef가 아직 null이면(처음 방 상태를 받는 순간)
    // 애니메이션 없이 그대로 스냅한다 — 비교할 "직전 상태"가 없거나 의미가 없다.
    if (roomState === null || settledRef.current === null) {
      settledRef.current = roomState;
      setDisplayState(roomState);
      setActiveVisualEvent(null);
      setDealProgress(null);
      return;
    }

    if (playingRef.current || holdingResultRef.current) {
      if (pendingRef.current !== null) {
        // 이미 대기 중이던 상태를 또 덮어쓴다 = 그 사이 상태 하나는 애니메이션 없이 건너뛰게 된다.
        droppedRef.current = true;
      }
      pendingRef.current = roomState;
      return;
    }

    playCycle(roomState);
    // playCycle/runSteps/finishCycle은 ref만 참조하는 안정된 클로저라 의존성에 넣지 않는다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomState]);

  function playCycle(next: RoomStateResponse) {
    playingRef.current = true;
    runSteps(deriveSteps(settledRef.current!, next), next);
  }

  function runSteps(steps: AnimationStep[], next: RoomStateResponse) {
    if (steps.length === 0) {
      finishCycle(next);
      return;
    }

    const [step, ...rest] = steps;
    setDisplayState((state) => step.patch(state!));
    setActiveVisualEvent(step.visualEvent);
    if (step.dealProgress !== undefined) {
      setDealProgress(step.dealProgress);
    }

    timeoutRef.current = window.setTimeout(() => {
      runSteps(rest, next);
    }, step.durationMs);
  }

  function finishCycle(next: RoomStateResponse) {
    settledRef.current = next;
    setDisplayState(next);
    setActiveVisualEvent(null);
    // 안전망 — 사이클이 정상적으로 끝났다면 이미 리빌 스텝에서 null로 돌아갔겠지만, 혹시 몰라 확실히 한다.
    setDealProgress(null);
    playingRef.current = false;

    if (next.phase === 'SHOWDOWN') {
      // 쇼다운 공개 연출(카드를 한 장씩 순서대로 뒤집는 스텝들)이 방금 다 끝난 시점이다. 인원이
      // 많으면 이 연출 자체가 이미 몇 초씩 걸리는데, 그동안 서버가 다음 핸드를 자동으로 시작해서
      // pendingRef에 새 상태가 먼저 도착해 있는 경우가 많다 — 여기서 바로 consumePending을 부르면
      // 결과 화면이 뜨자마자(다음 렌더에) 바로 다음 핸드로 넘어가버린다. 공개 연출이 끝난 뒤에도
      // RESULT_HOLD_MS만큼은 결과를 그대로 붙잡아두고, 그 사이/이후 도착하는 상태는 계속
      // pendingRef에 쌓아뒀다가 시간이 다 되면 재생한다.
      holdingResultRef.current = true;
      holdTimeoutRef.current = window.setTimeout(() => {
        holdingResultRef.current = false;
        consumePending();
      }, RESULT_HOLD_MS);
      return;
    }

    consumePending();
  }

  function consumePending() {
    if (pendingRef.current === null) {
      return;
    }
    const pending = pendingRef.current;
    pendingRef.current = null;

    if (droppedRef.current) {
      // 밀린 상태가 있었다 — 애니메이션 없이 바로 최신 상태로 스냅한다.
      droppedRef.current = false;
      settledRef.current = pending;
      setDisplayState(pending);
    } else {
      playCycle(pending);
    }
  }

  return { displayState, activeVisualEvent, dealProgress };
}
