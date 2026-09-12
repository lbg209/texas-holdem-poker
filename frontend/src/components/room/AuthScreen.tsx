import { useState } from 'react';
import type { FormEvent } from 'react';
import { useRoom } from '../../state/RoomContext';
import { RulesPage } from './RulesPage';
import { RegisterModal } from './RegisterModal';
import { PokerLogo } from './PokerLogo';

type Mode = 'login' | 'guest';

// 로그인/게스트 진입 화면. 로그인 또는 게스트 닉네임 설정에 성공하면 로비로 넘어간다 — 방 참가는
// 여기서 더 이상 하지 않는다(로비에서 방을 고른 뒤 별도로 "입장"한다).
export function AuthScreen() {
  const { state, enterLobbyAsGuest, loginAndEnterLobby } = useRoom();
  const [mode, setMode] = useState<Mode>('login');
  const [nickname, setNickname] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showRules, setShowRules] = useState(false);
  const [showRegisterModal, setShowRegisterModal] = useState(false);

  const handleGuestSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!nickname.trim()) {
      return;
    }
    enterLobbyAsGuest(nickname.trim());
  };

  const handleLoginSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    void loginAndEnterLobby(username, password);
  };

  if (showRules) {
    return <RulesPage onBack={() => setShowRules(false)} />;
  }

  return (
    <div className="mx-auto mt-16 max-w-sm">
      <PokerLogo />
      <div className="rounded-lg bg-slate-800 p-6">
      <h1 className="mb-4 text-xl font-semibold">포커 로비 입장</h1>

      <div className="mb-4 flex gap-2 text-sm">
        <button
          type="button"
          className={`flex-1 rounded px-3 py-1.5 ${mode === 'login' ? 'bg-emerald-600' : 'bg-slate-700 text-slate-300'}`}
          onClick={() => setMode('login')}
        >
          로그인
        </button>
        <button
          type="button"
          className={`flex-1 rounded px-3 py-1.5 ${mode === 'guest' ? 'bg-emerald-600' : 'bg-slate-700 text-slate-300'}`}
          onClick={() => setMode('guest')}
        >
          게스트로 입장
        </button>
      </div>

      {mode === 'login' ? (
        <form onSubmit={handleLoginSubmit} className="flex flex-col gap-2">
          <input
            className="rounded bg-slate-700 px-3 py-2 outline-none"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="아이디"
            autoComplete="username"
          />
          <input
            className="rounded bg-slate-700 px-3 py-2 outline-none"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="비밀번호"
            type="password"
            autoComplete="current-password"
          />
          <button className="rounded bg-emerald-600 px-4 py-2 font-medium" type="submit">
            로그인
          </button>
          <button
            type="button"
            className="text-xs text-slate-400 underline"
            onClick={() => setShowRegisterModal(true)}
          >
            계정이 없으신가요? 회원가입
          </button>
        </form>
      ) : (
        <form onSubmit={handleGuestSubmit} className="flex gap-2">
          <input
            className="flex-1 rounded bg-slate-700 px-3 py-2 outline-none"
            value={nickname}
            onChange={(e) => setNickname(e.target.value)}
            placeholder="닉네임"
          />
          <button className="rounded bg-emerald-600 px-4 py-2 font-medium" type="submit">
            시작
          </button>
        </form>
      )}

      {state.error && <p className="mt-3 text-sm text-red-400">{state.error}</p>}
      <button className="mt-3 text-sm text-slate-400 underline" onClick={() => setShowRules(true)}>
        게임 규칙
      </button>

      {showRegisterModal && (
        <RegisterModal
          onClose={() => setShowRegisterModal(false)}
          onRegistered={(registeredUsername) => {
            setUsername(registeredUsername);
            setPassword('');
          }}
        />
      )}
      </div>
    </div>
  );
}
