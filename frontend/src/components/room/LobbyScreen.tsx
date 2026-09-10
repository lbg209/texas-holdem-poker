import { useEffect, useMemo, useState } from 'react';
import { useRoom } from '../../state/RoomContext';
import { CreateRoomModal } from './CreateRoomModal';
import { JoinByCodeModal } from './JoinByCodeModal';
import { RulesPage } from './RulesPage';
import type { RoomSummaryView } from '../../types/room';

type SortField = 'title' | 'players';
type SortDir = 'asc' | 'desc';

// 인원 내림차순일 때는 꽉 찬 방(더 이상 입장할 수 없음)을 일반 정렬(N-1→...→1)에서 빼고 맨 아래로
// 몰아준다 — 어차피 못 들어가는 방을 "인원이 많다"는 이유로 상단에 보여주는 게 덜 유용하기 때문.
function sortRooms(rooms: RoomSummaryView[], field: SortField | null, dir: SortDir): RoomSummaryView[] {
  if (!field) {
    return rooms;
  }
  if (field === 'title') {
    const sorted = [...rooms].sort((a, b) => a.name.localeCompare(b.name));
    return dir === 'asc' ? sorted : sorted.reverse();
  }
  // field === 'players'
  if (dir === 'asc') {
    return [...rooms].sort((a, b) => a.playerCount - b.playerCount);
  }
  const full = rooms.filter((r) => r.playerCount === r.maxPlayers);
  const joinable = rooms.filter((r) => r.playerCount !== r.maxPlayers).sort((a, b) => b.playerCount - a.playerCount);
  return [...joinable, ...full];
}

function SortHeaderButton({
  label,
  field,
  activeField,
  activeDir,
  onClick,
}: {
  label: string;
  field: SortField;
  activeField: SortField | null;
  activeDir: SortDir;
  onClick: () => void;
}) {
  const active = activeField === field;
  return (
    <button
      className={`text-xs font-medium ${active ? 'text-emerald-400' : 'text-slate-400 hover:text-slate-300'}`}
      onClick={onClick}
    >
      {label} {active ? (activeDir === 'asc' ? '▲' : '▼') : ''}
    </button>
  );
}

// 로그인/게스트 진입 이후의 로비 화면. 방 목록을 한 줄씩 보여주고, 클릭하면 왼쪽에 정보 패널이
// 열리며 "입장" 버튼으로 실제로 들어갈 수 있다.
export function LobbyScreen() {
  const { state, refreshRoomList, selectRoom, clearSelectedRoom, joinSelectedRoom, logout } = useRoom();
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showJoinByCodeModal, setShowJoinByCodeModal] = useState(false);
  const [showRules, setShowRules] = useState(false);
  const [titleFilter, setTitleFilter] = useState('');
  const [sortField, setSortField] = useState<SortField | null>(null);
  const [sortDir, setSortDir] = useState<SortDir>('asc');
  const [password, setPassword] = useState('');

  useEffect(() => {
    void refreshRoomList();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    setPassword('');
  }, [state.selectedRoomDetail?.roomCode]);

  const handleSort = (field: SortField) => {
    if (sortField !== field) {
      setSortField(field);
      setSortDir('asc');
      return;
    }
    setSortDir((prev) => (prev === 'asc' ? 'desc' : 'asc'));
  };

  const visibleRooms = useMemo(() => {
    const filtered = state.roomList.filter((room) =>
      room.name.toLowerCase().includes(titleFilter.trim().toLowerCase()),
    );
    return sortRooms(filtered, sortField, sortDir);
  }, [state.roomList, titleFilter, sortField, sortDir]);

  const detail = state.selectedRoomDetail;
  const identityLabel = state.loggedInNickname ?? (state.guestNickname ? `${state.guestNickname} (Guest)` : null);

  if (showRules) {
    return <RulesPage onBack={() => setShowRules(false)} />;
  }

  return (
    <div className="mx-auto max-w-3xl">
      <div className="mt-6 flex items-center justify-between text-sm">
        <button className="text-slate-400 underline hover:text-slate-300" onClick={() => setShowRules(true)}>
          게임 규칙
        </button>
        <div className="flex items-center gap-2 text-slate-300">
          {identityLabel && <span>{identityLabel}</span>}
          <button className="rounded bg-slate-700 px-2 py-1 text-xs hover:bg-slate-600" onClick={logout}>
            로그아웃
          </button>
        </div>
      </div>

      <div className="mt-4 flex gap-4">
      {detail && (
        <div className="w-64 shrink-0 rounded-lg bg-slate-800 p-4">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-lg font-semibold">{detail.name}</h2>
            <button className="text-slate-400 hover:text-slate-200" onClick={clearSelectedRoom} aria-label="닫기">
              ✕
            </button>
          </div>
          <dl className="space-y-1 text-sm text-slate-300">
            <div className="flex justify-between">
              <dt className="text-slate-500">인원</dt>
              <dd>{detail.players.length} / {detail.maxPlayers}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">시작 칩</dt>
              <dd>{detail.startingChips.toLocaleString()}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">블라인드</dt>
              <dd>{detail.smallBlind.toLocaleString()} / {detail.bigBlind.toLocaleString()}</dd>
            </div>
            {detail.isPrivate && (
              <div className="flex justify-between">
                <dt className="text-slate-500">공개 여부</dt>
                <dd>🔒 비공개</dd>
              </div>
            )}
          </dl>

          {detail.isPrivate && (
            <input
              className="mt-3 w-full rounded bg-slate-700 px-3 py-2 text-sm outline-none"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="비밀번호"
            />
          )}

          <button
            className="mt-3 w-full rounded bg-emerald-600 px-4 py-2 font-medium disabled:opacity-50"
            disabled={detail.players.length >= detail.maxPlayers}
            onClick={() => void joinSelectedRoom(detail.isPrivate ? password : undefined)}
          >
            {detail.players.length >= detail.maxPlayers ? '정원 초과' : '입장'}
          </button>
        </div>
      )}

      <div className="flex-1 rounded-lg bg-slate-800 p-4">
        <div className="mb-3 flex items-center justify-between">
          <h1 className="text-xl font-semibold">로비</h1>
          <div className="flex gap-2">
            <button
              className="rounded bg-slate-700 px-3 py-1.5 text-sm hover:bg-slate-600"
              onClick={() => setShowJoinByCodeModal(true)}
            >
              코드로 입장
            </button>
            <button className="rounded bg-slate-700 px-3 py-1.5 text-sm hover:bg-slate-600" onClick={() => void refreshRoomList()}>
              새로고침
            </button>
            <button
              className="rounded bg-emerald-600 px-3 py-1.5 text-sm font-medium"
              onClick={() => setShowCreateModal(true)}
            >
              방 만들기
            </button>
          </div>
        </div>

        <input
          className="mb-3 w-full rounded bg-slate-700 px-3 py-2 text-sm outline-none"
          value={titleFilter}
          onChange={(e) => setTitleFilter(e.target.value)}
          placeholder="방 제목 검색"
        />

        <div className="mb-1 flex items-center justify-between px-2">
          <SortHeaderButton label="제목" field="title" activeField={sortField} activeDir={sortDir} onClick={() => handleSort('title')} />
          <SortHeaderButton label="인원" field="players" activeField={sortField} activeDir={sortDir} onClick={() => handleSort('players')} />
        </div>

        {visibleRooms.length === 0 ? (
          <p className="py-6 text-center text-sm text-slate-500">
            {state.roomList.length === 0 ? '아직 만들어진 방이 없어요.' : '검색 결과가 없어요.'}
          </p>
        ) : (
          <ul className="divide-y divide-slate-700">
            {visibleRooms.map((room) => (
              <li key={room.roomCode}>
                <button
                  className={`flex w-full items-center justify-between px-2 py-2.5 text-left text-sm hover:bg-slate-700 ${
                    detail?.roomCode === room.roomCode ? 'bg-slate-700' : ''
                  }`}
                  onClick={() => void selectRoom(room.roomCode)}
                >
                  <span className="flex items-center gap-2">
                    <span>{room.isPrivate ? `🔒 ${room.name}` : room.name}</span>
                    <span className={`rounded px-1.5 py-0.5 text-[10px] ${room.inProgress ? 'bg-red-700' : 'bg-emerald-700'}`}>
                      {room.inProgress ? '게임중' : '대기중'}
                    </span>
                  </span>
                  <span className="text-slate-400">
                    {room.playerCount}/{room.maxPlayers}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
      </div>

      {state.error && (
        <div className="fixed bottom-4 left-1/2 -translate-x-1/2 rounded bg-red-900/80 px-4 py-2 text-sm" aria-live="polite">
          {state.error}
        </div>
      )}

      {showCreateModal && <CreateRoomModal onClose={() => setShowCreateModal(false)} />}
      {showJoinByCodeModal && <JoinByCodeModal onClose={() => setShowJoinByCodeModal(false)} />}
    </div>
  );
}
