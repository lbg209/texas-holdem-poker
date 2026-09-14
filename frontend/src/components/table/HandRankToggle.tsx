import { useState } from 'react';
import { Card } from './Card';
import { getHandRankTextClassName, getHandRankTextPrefix, getHighlightColorForHandRank } from '../../lib/handRank';
import { HAND_RANKS } from '../../lib/handRankExamples';

// 다른 토글(방 정보/핸드 히스토리/블라인드 구조)들과 같은 행(App.tsx의 flex-col 컨테이너)에 놓이는
// 버튼이지만, 이 내용은 게임 중 실시간으로 바뀌는 정보가 아니라 고정된 참고 자료라 작은 코너
// 드롭다운 대신 화면 중앙 모달로 띄운다 — RulesPage의 족보 섹션과 완전히 같은 데이터
// (lib/handRankExamples.ts)를 재사용한다.
export function HandRankToggle() {
  const [open, setOpen] = useState(false);

  return (
    <>
      <button
        className="rounded bg-slate-800/90 px-3 py-1.5 text-sm text-slate-300 shadow hover:bg-slate-700"
        onClick={() => setOpen(true)}
      >
        🃏 족보
      </button>
      {open && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/60"
          onClick={() => setOpen(false)}
          role="presentation"
        >
          <div
            className="max-h-[85vh] w-full max-w-3xl overflow-y-auto rounded-lg bg-slate-800 p-4"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
          >
            <div className="mb-3 flex items-center justify-between">
              <h2 className="text-lg font-semibold">족보 (낮음 → 높음)</h2>
              <button
                className="text-slate-400 hover:text-slate-200"
                onClick={() => setOpen(false)}
                aria-label="닫기"
              >
                ✕
              </button>
            </div>
            {/* 넓어진 가로 폭을 활용해 2열로 배치 — 세로 한 줄로 쌓았을 때보다 높이가 절반으로
                줄어서 스크롤 없이 한눈에 다 보인다. */}
            <div className="grid grid-cols-1 gap-2 md:grid-cols-2">
              {HAND_RANKS.map(({ label, handRank, example, highlightIndices, blueIndices }) => {
                const color = getHighlightColorForHandRank(handRank, example);
                const isFancyRow = color === 'special' || color === 'royal';
                return (
                  <div
                    key={label}
                    className={`flex items-center gap-3 rounded p-2 ${
                      isFancyRow
                        ? 'bg-gradient-to-r from-fuchsia-950/50 via-slate-900/60 to-yellow-950/50'
                        : 'bg-slate-900/60'
                    }`}
                  >
                    <span className={`w-20 shrink-0 text-xs ${getHandRankTextClassName(color)}`}>
                      {getHandRankTextPrefix(color)}
                      {label}
                    </span>
                    <div className="flex gap-0.5">
                      {example.map((card, i) => {
                        const isHighlighted = highlightIndices.includes(i);
                        const isBlue = blueIndices?.includes(i) ?? false;
                        return (
                          // Card는 항상 원래 크기(h-28 w-20, sm:h-32 sm:w-24)로 그려지고 scale은
                          // 시각적으로만 줄인다(레이아웃 박스는 그대로) — 그래서 바깥에 실제로 그
                          // 절반 크기만큼만 공간을 차지하는 래퍼를 씌워서 카드 사이 여백 낭비를 없앤다.
                          // 2열 배치에서 한 줄에 카드 5장 + 라벨이 다 들어가려면 이게 필요하다.
                          <div key={i} className="h-14 w-10 overflow-hidden sm:h-16 sm:w-12">
                            <div className="origin-top-left scale-50">
                              <Card
                                card={card}
                                face="up"
                                highlighted={isHighlighted || isBlue}
                                highlightColor={isBlue ? 'blue' : color}
                              />
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      )}
    </>
  );
}
