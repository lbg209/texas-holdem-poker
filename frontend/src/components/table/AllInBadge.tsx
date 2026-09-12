// 실제 포커 방송 중계에서 올인한 사람 칩 스택 옆에 놓이는 작은 삼각형(페넌트) "ALL IN" 표식을
// 흉내낸다. clip-path로 위는 평평하고 아래로 갈수록 뾰족해지는 삼각형 모양을 만들고, 글자는 위쪽
// 평평한 부분에 배치한다(완전한 삼각형 안에 글자를 넣으면 너무 좁아서 안 보이므로).
export function AllInBadge() {
  return (
    <div
      className="flex h-9 w-9 flex-col items-center justify-start bg-gradient-to-b from-red-500 to-red-700 pt-1 leading-none text-white shadow-md"
      style={{ clipPath: 'polygon(0% 0%, 100% 0%, 100% 55%, 50% 100%, 0% 55%)' }}
      title="올인"
    >
      <span className="text-[8px] font-extrabold">ALL</span>
      <span className="text-[8px] font-extrabold">IN</span>
    </div>
  );
}
