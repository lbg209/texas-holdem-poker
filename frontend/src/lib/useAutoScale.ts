import { useEffect, useRef, useState } from 'react';

// 지금까지 PokerTable은 w-full max-w-4xl(896px)이라 좁은 화면에서는 이미 잘 줄어들지만, 그보다
// 넓은 화면(예: QHD 모니터)에서는 896px 위로는 절대 안 커져서 화면 대부분이 빈 공간으로 남는다.
// 이 훅은 "줄어드는 것"은 기존 반응형 클래스에 맡기고, "여유 공간이 있을 때 키우는 것"만 추가로
// 담당한다 — 그래서 MIN_SCALE이 1이다(1 밑으로는 절대 내려가지 않고, 기존 축소 동작을 그대로 둠).
const MIN_SCALE = 1;
const MAX_SCALE = 1.6;
// 화면 높이도 같이 고려한다(가로는 넓은데 세로가 짧은 창에서 테이블이 화면 밖으로 넘치지 않게) —
// 헤더/토글/액션바 등이 차지하는 공간을 대략 감안해 뷰포트 높이의 70%까지만 테이블 높이로 쓴다.
const VIEWPORT_HEIGHT_BUDGET_RATIO = 0.7;

export function useAutoScale(designWidth: number, designHeight: number) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(1);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) {
      return;
    }

    const compute = () => {
      const availableWidth = el.clientWidth;
      const availableHeight = window.innerHeight * VIEWPORT_HEIGHT_BUDGET_RATIO;
      const next = Math.min(availableWidth / designWidth, availableHeight / designHeight, MAX_SCALE);
      setScale(Math.max(MIN_SCALE, next));
    };

    compute();
    const observer = new ResizeObserver(compute);
    observer.observe(el);
    window.addEventListener('resize', compute);
    return () => {
      observer.disconnect();
      window.removeEventListener('resize', compute);
    };
  }, [designWidth, designHeight]);

  return { containerRef, scale };
}
