import { useEffect, useState } from 'react';

function secondsUntil(deadlineMillis: number): number {
  return Math.max(0, Math.ceil((deadlineMillis - Date.now()) / 1000));
}

// deadlineMillis(서버 절대 시각 기준 마감)로부터 남은 초는 항상 렌더링 시점에 순수 계산한다
// (useState에 값 자체를 담지 않음) — tick만 상태로 둬서 1초마다 리렌더를 강제해 시간이 흐르는
// 걸 보여준다. deadlineMillis가 null이면(지금 시간 제한 중인 액션자가 없음) null을 반환한다.
export function useCountdownSeconds(deadlineMillis: number | null): number | null {
  const [, forceTick] = useState(0);

  useEffect(() => {
    if (deadlineMillis === null) {
      return;
    }
    const interval = window.setInterval(() => forceTick((n) => n + 1), 1000);
    return () => window.clearInterval(interval);
  }, [deadlineMillis]);

  return deadlineMillis === null ? null : secondsUntil(deadlineMillis);
}
