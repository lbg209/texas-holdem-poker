import type { SeatPosition } from './computeSeatPositions';
import { SEAT_COLORS } from '../../lib/seatColors';

interface EmptySeatProps {
  seatIndex: number;
  position: SeatPosition;
  // 지금 이 자리를 클릭할 수 있으면(아직 안 앉았으면 항상, 이미 앉았으면 핸드 진행 중이 아닐
  // 때만) 핸들러가 주어진다. 없으면 버튼이 비활성화된 채로만 빈자리를 표시한다.
  onClick?: () => void;
}

// 고정 좌석제의 빈자리 placeholder. 아직 안 앉은 사람에게는 "여기 앉기", 이미 앉은 사람에게는
// "여기로 옮기기" 버튼 역할을 겸한다(의미는 호출 측이 onClick으로 결정).
export function EmptySeat({ seatIndex, position, onClick }: EmptySeatProps) {
  return (
    <div
      className="absolute"
      style={{
        left: `${position.leftPercent}%`,
        top: `${position.topPercent}%`,
        transform: `translate(-50%, -50%) scale(${position.scale})`,
      }}
    >
      <button
        type="button"
        disabled={!onClick}
        onClick={onClick}
        className={`flex min-w-36 items-center justify-center gap-1.5 rounded-md border-2 border-dashed px-3 py-2 text-sm shadow-md ${
          onClick
            ? 'cursor-pointer border-slate-500 bg-slate-900/60 text-slate-300 hover:border-emerald-400 hover:text-emerald-300'
            : 'border-slate-700 bg-slate-900/40 text-slate-600'
        }`}
      >
        <span className={`h-2 w-2 rounded-full ${SEAT_COLORS[seatIndex]}`} />
        빈 자리
      </button>
    </div>
  );
}
