import { useState } from 'react';
import type { FormEvent } from 'react';
import { register } from '../../api/authApi';

interface RegisterModalProps {
  onClose: () => void;
  // 가입에 성공하면 로그인 폼에 아이디를 미리 채워줄 수 있도록 username을 넘겨준다.
  onRegistered: (username: string) => void;
}

export function RegisterModal({ onClose, onRegistered }: RegisterModalProps) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [nickname, setNickname] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await register(username, password, nickname);
      onRegistered(username);
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
          <h2 className="text-lg font-semibold">회원가입</h2>
          <button className="text-slate-400 hover:text-slate-200" onClick={onClose} aria-label="닫기">
            ✕
          </button>
        </div>
        <form onSubmit={handleSubmit} className="flex flex-col gap-2">
          <input
            className="rounded bg-slate-700 px-3 py-2 outline-none"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="아이디 (영문/숫자, 3~20자)"
            autoComplete="username"
            pattern="[A-Za-z0-9]{3,20}"
            title="영문/숫자만 사용할 수 있어요 (3~20자)"
            autoFocus
          />
          <input
            className="rounded bg-slate-700 px-3 py-2 outline-none"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="비밀번호 (4자 이상)"
            type="password"
            autoComplete="new-password"
          />
          <input
            className="rounded bg-slate-700 px-3 py-2 outline-none"
            value={nickname}
            onChange={(e) => setNickname(e.target.value)}
            placeholder="닉네임 (테이블에 표시될 이름)"
            maxLength={20}
          />
          {error && <p className="text-sm text-red-400">{error}</p>}
          <button
            className="mt-1 rounded bg-emerald-600 px-4 py-2 font-medium disabled:opacity-50"
            type="submit"
            disabled={submitting}
          >
            가입하기
          </button>
        </form>
      </div>
    </div>
  );
}
