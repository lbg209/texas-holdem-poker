import { useState } from 'react';
import type { FormEvent } from 'react';
import { useRoom } from '../../state/RoomContext';
import { RulesPage } from './RulesPage';

export function JoinForm() {
  const { state, join } = useRoom();
  const [nickname, setNickname] = useState('');
  const [showRules, setShowRules] = useState(false);

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    void join(nickname);
  };

  if (showRules) {
    return <RulesPage onBack={() => setShowRules(false)} />;
  }

  return (
    <div className="mx-auto mt-24 max-w-sm rounded-lg bg-slate-800 p-6">
      <h1 className="mb-4 text-xl font-semibold">방 참가</h1>
      <form onSubmit={handleSubmit} className="flex gap-2">
        <input
          className="flex-1 rounded bg-slate-700 px-3 py-2 outline-none"
          value={nickname}
          onChange={(e) => setNickname(e.target.value)}
          placeholder="닉네임"
        />
        <button className="rounded bg-emerald-600 px-4 py-2 font-medium" type="submit">
          참가
        </button>
      </form>
      {state.error && <p className="mt-3 text-sm text-red-400">{state.error}</p>}
      <button className="mt-3 text-sm text-slate-400 underline" onClick={() => setShowRules(true)}>
        게임 규칙
      </button>
    </div>
  );
}
