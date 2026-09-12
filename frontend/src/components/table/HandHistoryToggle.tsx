import { useEffect, useState } from 'react';
import { useRoom } from '../../state/RoomContext';
import { getHandHistory } from '../../api/roomApi';
import type { CardView, HandHistoryEntryView } from '../../types/room';
import { formatMoney } from '../../lib/formatMoney';
import { getHandRankLabel } from '../../lib/handRank';

// 좌측 상단 "방 정보" 토글과 한 행에 나란히 놓이는 두 번째 on/off 패널(배치는 App.tsx가 담당) —
// 켜면 이 방의 최근 핸드 히스토리(최신순, 최대 30개)를 불러온다. 한 줄 요약만 보이다가 클릭하면
// 그 핸드의 상세(보드/공개된 패/팟)가 펼쳐진다. 카드 공개 규칙은 실시간 조회와 동일하다 — 머크한
// 패는 지난 핸드라도 안 보인다.
function MiniCard({ card }: { card: CardView }) {
  const isRed = card.suit === 'HEART' || card.suit === 'DIAMOND';
  return (
    <span
      className={`inline-flex h-6 min-w-6 items-center justify-center rounded border border-slate-600 bg-slate-900 px-1 text-xs font-semibold ${
        isRed ? 'text-red-400' : 'text-slate-100'
      }`}
    >
      {card.display}
    </span>
  );
}

function CardRow({ cards }: { cards: CardView[] }) {
  if (cards.length === 0) {
    return <span className="text-xs text-slate-500">MUCK</span>;
  }
  return (
    <span className="flex gap-1">
      {cards.map((c) => (
        <MiniCard key={`${c.suit}_${c.rank}`} card={c} />
      ))}
    </span>
  );
}

// 요약 줄에 쓸 "누가/무엇으로 이겼는지"를 계산한다. 폴드승은 족보가 없으니 "폴드승"으로 대신한다.
function summarizeWinners(entry: HandHistoryEntryView): { winnerLabel: string; rankLabel: string } {
  if (entry.wonByFold) {
    return { winnerLabel: entry.foldWinWinnerNickname ?? '알 수 없음', rankLabel: '폴드승' };
  }
  const winners = entry.hands.filter((h) => h.isWinner);
  const winnerLabel = winners.map((h) => h.nickname).join(' / ') || '알 수 없음';
  const shown = winners.find((h) => h.handRank !== null && h.bestFive !== null);
  const rankLabel = shown ? getHandRankLabel(shown.handRank!, shown.bestFive!) : '공개 안 함';
  return { winnerLabel, rankLabel };
}

function HandHistoryRow({ entry }: { entry: HandHistoryEntryView }) {
  const [expanded, setExpanded] = useState(false);
  const totalPot = entry.pots.reduce((sum, p) => sum + p.amount, 0);
  const { winnerLabel, rankLabel } = summarizeWinners(entry);

  return (
    <div className="border-b border-slate-700 last:border-b-0">
      <button
        type="button"
        className="w-full px-2 py-1.5 text-left text-xs hover:bg-slate-700/60"
        onClick={() => setExpanded((v) => !v)}
      >
        #{entry.handNumber} · {winnerLabel} 승 · {rankLabel} · {formatMoney(totalPot)}
      </button>
      {expanded && (
        <div className="space-y-2 bg-slate-900/60 px-2 py-2 text-xs">
          {entry.communityCards.length > 0 && (
            <div>
              <div className="mb-1 text-slate-500">보드</div>
              <CardRow cards={entry.communityCards} />
            </div>
          )}
          {entry.wonByFold ? (
            <div className="text-slate-400">전원 폴드로 종료 — {entry.foldWinWinnerNickname}님이 팟을 가져감</div>
          ) : (
            <div className="space-y-1.5">
              {entry.hands.map((hand) => (
                <div key={hand.playerId} className="flex items-center justify-between gap-2">
                  <span className={hand.isWinner ? 'font-semibold text-yellow-400' : 'text-slate-300'}>
                    {hand.nickname}
                    {hand.isWinner && ' 🏆'}
                  </span>
                  <CardRow cards={hand.holeCards} />
                </div>
              ))}
            </div>
          )}
          <div className="border-t border-slate-700 pt-1.5 text-slate-400">
            {entry.pots.map((pot, i) => (
              <div key={i}>
                팟{entry.pots.length > 1 ? ` ${i + 1}` : ''} {formatMoney(pot.amount)}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

export function HandHistoryToggle() {
  const { state } = useRoom();
  const [open, setOpen] = useState(false);
  const [entries, setEntries] = useState<HandHistoryEntryView[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const roomCode = state.roomCode;

  useEffect(() => {
    if (!open || !roomCode) {
      return;
    }
    setLoading(true);
    setError(null);
    getHandHistory(roomCode, state.myPlayerId)
      .then((result) => setEntries(result))
      .catch((e) => setError((e as Error).message))
      .finally(() => setLoading(false));
    // roomCode/myPlayerId가 열려있는 동안 바뀔 일은 사실상 없다 — 열 때마다 한 번만 불러온다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  if (!roomCode) {
    return null;
  }

  return (
    <div>
      <button
        className="rounded bg-slate-800/90 px-2 py-1 text-xs text-slate-300 shadow hover:bg-slate-700"
        onClick={() => setOpen((prev) => !prev)}
      >
        📜 핸드 히스토리
      </button>
      {open && (
        <div className="mt-1 max-h-96 w-72 overflow-y-auto rounded-lg bg-slate-800 shadow-lg">
          {loading && <p className="p-3 text-xs text-slate-400">불러오는 중...</p>}
          {error && <p className="p-3 text-xs text-red-400">{error}</p>}
          {!loading && !error && entries && entries.length === 0 && (
            <p className="p-3 text-xs text-slate-400">아직 끝난 핸드가 없습니다.</p>
          )}
          {!loading && !error && entries && entries.length > 0 && (
            <div>
              {entries.map((entry) => (
                <HandHistoryRow key={entry.handNumber} entry={entry} />
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
