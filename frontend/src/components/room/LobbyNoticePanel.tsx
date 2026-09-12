interface NoticeItem {
  date: string;
  text: string;
}

// 로비 왼쪽 패널의 기본 상태(아직 방을 고르지 않았을 때) 콘텐츠. 지금은 업데이트 소식을 보여주지만,
// 이 칸을 나중에 다른 용도(광고 등)로 바꾸더라도 LobbyScreen이 이미 항상 고정폭 칼럼으로 이 자리를
// 마련해두고 있어서, 컴포넌트만 바꿔 끼우면 된다.
const NOTICES: NoticeItem[] = [
  { date: '2026-09-12', text: '고정 6인 좌석제 도입 — 빈 자리를 클릭해서 입장하거나 옮길 수 있어요.' },
  { date: '2026-09-12', text: '게임 중에도 왼쪽 토글에서 족보표를 바로 확인할 수 있어요.' },
  { date: '2026-09-12', text: '판 수에 따라 블라인드가 자동으로 오르고 앤티도 걷혀요 — "블라인드 구조" 토글에서 확인하세요.' },
];

export function LobbyNoticePanel() {
  return (
    <div>
      <h2 className="mb-3 text-lg font-semibold">📢 공지</h2>
      <ul className="space-y-3">
        {NOTICES.map((notice) => (
          <li key={notice.text}>
            <p className="text-[11px] text-slate-500">{notice.date}</p>
            <p className="text-sm text-slate-300">{notice.text}</p>
          </li>
        ))}
      </ul>
    </div>
  );
}
