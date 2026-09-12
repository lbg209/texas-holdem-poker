// 고정 좌석제 — 좌석 번호(0~Room.MAX_PLAYERS-1)마다 항상 같은 색을 써서, 닉네임이 같아도(중복
// 허용) 좌석으로 구분할 수 있게 하는 보조 표시. 누가 앉든, 자리를 옮기든 이 색은 좌석 번호만
// 따라간다.
export const SEAT_COLORS = [
  'bg-rose-500',
  'bg-amber-500',
  'bg-lime-500',
  'bg-teal-500',
  'bg-sky-500',
  'bg-violet-500',
];
