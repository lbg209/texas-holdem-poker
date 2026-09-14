import { useState } from 'react';
import { computeSeatPositions } from './computeSeatPositions';

const SEAT_COUNT = 6;
// seatIndex 0~5 = 버튼 기준 offset 0~5(GameEngine.playerAtOffset과 동일한 규칙: 버튼(0)->SB(1)->
// BB(2)->UTG(3)->HJ(4)->CO(5)) — 실시간 게임 데이터가 아니라 6인 기준 고정 예시 다이어그램이다.
const POSITION_LABELS = ['BTN', 'SB', 'BB', 'UTG', 'HJ', 'CO'];

// 6개 좌석을 원형으로 배치하고, startOffset(몇 번째 좌석부터 액션이 시작되는지)을 기준으로 각
// 좌석의 행동 순번을 매겨서 보여주는 작은 참고용 다이어그램. 실제 테이블 좌석 배치(computeSeatPositions)
// 를 그대로 재사용해서 실제 게임 화면과 같은 원형 느낌을 준다.
function OrderDiagram({ title, startOffset }: { title: string; startOffset: number }) {
  const positions = computeSeatPositions(SEAT_COUNT);
  return (
    // computeSeatPositions는 원래 큰 테이블 기준이라, 가장 가까운/먼 좌석의 topPercent가 살짝
    // 0%/100% 밖으로 벗어난다(배지 반지름만큼) — 이 작은 다이어그램에서는 그게 바로 위 제목이나
    // 아래 본문 글자와 겹쳐 보이는 원인이었다. 위아래에 여유 패딩을 둬서 배지가 삐져나와도 다른
    // 글자와 안 닿게 한다.
    <div className="pb-8 pt-3">
      <p className="mb-4 text-center text-xs font-medium text-slate-400">{title}</p>
      <div className="relative mx-auto aspect-[4/3] w-full max-w-[190px] rounded-[45%] bg-emerald-900/30">
        {positions.map((pos, seatIndex) => {
          const order = ((seatIndex - startOffset + SEAT_COUNT) % SEAT_COUNT) + 1;
          const isFirst = order === 1;
          return (
            <div
              key={seatIndex}
              className="absolute -translate-x-1/2 -translate-y-1/2"
              style={{ left: `${pos.leftPercent}%`, top: `${pos.topPercent}%` }}
            >
              <div
                className={`flex h-9 w-9 flex-col items-center justify-center rounded-full text-[10px] font-semibold leading-none shadow-md ${
                  isFirst
                    ? 'bg-emerald-500 text-slate-950 ring-2 ring-emerald-300'
                    : 'bg-slate-700 text-slate-100'
                }`}
              >
                <span>{POSITION_LABELS[seatIndex]}</span>
                <span className="mt-0.5 text-[8px] opacity-80">{order}번째</span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

// 족보 토글(HandRankToggle)과 같은 패턴 — 게임 중 실시간으로 바뀌는 정보가 아니라 고정된 규칙
// 설명이라, 작은 코너 드롭다운 대신 화면 중앙 모달로 띄운다.
export function BettingOrderToggle() {
  const [open, setOpen] = useState(false);

  return (
    <>
      <button
        className="rounded bg-slate-800/90 px-3 py-1.5 text-sm text-slate-300 shadow hover:bg-slate-700"
        onClick={() => setOpen(true)}
      >
        🔄 베팅 순서
      </button>
      {open && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/60"
          onClick={() => setOpen(false)}
          role="presentation"
        >
          <div
            className="max-h-[85vh] w-full max-w-xl overflow-y-auto rounded-lg bg-slate-800 p-4"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
          >
            <div className="mb-3 flex items-center justify-between">
              <h2 className="text-lg font-semibold">베팅 순서</h2>
              <button
                className="text-slate-400 hover:text-slate-200"
                onClick={() => setOpen(false)}
                aria-label="닫기"
              >
                ✕
              </button>
            </div>

            {/* 초록 배지(1번째)가 각 상황에서 실제로 먼저 액션하는 자리다 — 6인 기준 예시라, 인원이
                다르면 번호만 달라질 뿐 "프리플랍은 UTG부터, 그 외는 SB부터"라는 규칙 자체는 동일하다. */}
            <div className="mb-4 grid grid-cols-2 gap-8">
              <OrderDiagram title="프리플랍" startOffset={3} />
              <OrderDiagram title="플랍 / 턴 / 리버" startOffset={1} />
            </div>

            <div className="space-y-4 text-sm text-slate-200">
              <section>
                <h3 className="mb-1.5 font-semibold text-emerald-400">기본 순서</h3>
                <ul className="space-y-1.5">
                  <li>
                    <span className="font-medium text-slate-100">프리플랍</span> — UTG(빅블라인드 다음
                    자리)부터 시작해서 <span className="font-medium text-slate-100">빅블라인드가 마지막</span>
                    으로 행동합니다.
                  </li>
                  <li>
                    <span className="font-medium text-slate-100">플랍 / 턴 / 리버</span> — 세 스트리트
                    모두 동일하게 <span className="font-medium text-slate-100">스몰블라인드부터</span>{' '}
                    시작합니다.
                  </li>
                </ul>
              </section>

              <section>
                <h3 className="mb-1.5 font-semibold text-amber-400">예외 상황</h3>
                <ul className="space-y-1.5">
                  <li>스몰블라인드가 이미 폴드했다면, 그다음으로 살아있는 플레이어부터 시작합니다.</li>
                  <li>
                    <span className="font-medium text-slate-100">2인 대결(헤즈업)</span>일 때만 순서가
                    반대입니다 — 프리플랍은 버튼(=스몰블라인드)이 먼저, 플랍/턴/리버는 반대로
                    빅블라인드가 먼저 행동합니다.
                  </li>
                  <li>한 명만 남고 나머지가 전부 폴드하면, 그 즉시 핸드가 종료됩니다.</li>
                </ul>
              </section>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
