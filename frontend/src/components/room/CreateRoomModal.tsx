import { useState } from 'react';
import type { FormEvent } from 'react';
import { useRoom } from '../../state/RoomContext';

interface CreateRoomModalProps {
  onClose: () => void;
}

const DEFAULT_STARTING_CHIPS = 30_000;
const DEFAULT_BIG_BLIND = 200;
const DEFAULT_MAX_PLAYERS = 6;
const MAX_PLAYER_OPTIONS = [2, 3, 4, 5, 6];

export function CreateRoomModal({ onClose }: CreateRoomModalProps) {
  const { createNewRoom } = useRoom();
  const [name, setName] = useState('');
  const [isPrivate, setIsPrivate] = useState(false);
  const [password, setPassword] = useState('');
  const [startingChips, setStartingChips] = useState(DEFAULT_STARTING_CHIPS);
  const [bigBlind, setBigBlind] = useState(DEFAULT_BIG_BLIND);
  const [maxPlayers, setMaxPlayers] = useState(DEFAULT_MAX_PLAYERS);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!name.trim()) {
      setError('방 이름을 입력해주세요.');
      return;
    }
    if (isPrivate && !password.trim()) {
      setError('비공개방은 비밀번호를 입력해야 합니다.');
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await createNewRoom(name.trim(), isPrivate, isPrivate ? password : undefined, startingChips, bigBlind, maxPlayers);
      onClose();
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60"
      onClick={onClose}
      role="presentation"
    >
      <div
        className="w-full max-w-sm rounded-lg bg-slate-800 p-6"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
      >
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold">방 만들기</h2>
          <button className="text-slate-400 hover:text-slate-200" onClick={onClose} aria-label="닫기">
            ✕
          </button>
        </div>
        <form onSubmit={handleSubmit} className="flex flex-col gap-3">
          <label className="flex flex-col gap-1 text-sm text-slate-300">
            방 이름
            <input
              className="rounded bg-slate-700 px-3 py-2 outline-none"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="예: 초보만 오세요"
              maxLength={30}
              autoFocus
            />
          </label>
          <label className="flex items-center gap-2 text-sm text-slate-300">
            <input type="checkbox" checked={isPrivate} onChange={(e) => setIsPrivate(e.target.checked)} />
            비공개방(목록엔 보이지만, 비밀번호나 방 코드로만 입장 가능)
          </label>
          {isPrivate && (
            <label className="flex flex-col gap-1 text-sm text-slate-300">
              비밀번호
              <input
                className="rounded bg-slate-700 px-3 py-2 outline-none"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                type="password"
                placeholder="비밀번호"
              />
            </label>
          )}
          <label className="flex flex-col gap-1 text-sm text-slate-300">
            시작 칩
            <input
              className="rounded bg-slate-700 px-3 py-2 outline-none"
              type="number"
              min={10_000}
              step={10_000}
              value={startingChips}
              onChange={(e) => setStartingChips(Number(e.target.value))}
            />
          </label>
          <label className="flex flex-col gap-1 text-sm text-slate-300">
            빅블라인드
            <input
              className="rounded bg-slate-700 px-3 py-2 outline-none"
              type="number"
              min={100}
              step={100}
              value={bigBlind}
              onChange={(e) => setBigBlind(Number(e.target.value))}
            />
            <span className="text-xs text-slate-500">스몰블라인드는 자동으로 절반({bigBlind / 2})으로 설정돼요.</span>
          </label>
          <label className="flex flex-col gap-1 text-sm text-slate-300">
            최대 인원
            <select
              className="rounded bg-slate-700 px-3 py-2 outline-none"
              value={maxPlayers}
              onChange={(e) => setMaxPlayers(Number(e.target.value))}
            >
              {MAX_PLAYER_OPTIONS.map((n) => (
                <option key={n} value={n}>
                  {n}명
                </option>
              ))}
            </select>
          </label>
          {error && <p className="text-sm text-red-400">{error}</p>}
          <button
            className="mt-1 rounded bg-emerald-600 px-4 py-2 font-medium disabled:opacity-50"
            type="submit"
            disabled={submitting}
          >
            방 만들기
          </button>
        </form>
      </div>
    </div>
  );
}
