import { useState } from 'react';
import type { FormEvent } from 'react';
import { useRoom } from '../../state/RoomContext';

interface JoinByCodeModalProps {
  onClose: () => void;
}

// 방 코드를 직접 입력해서 관전을 시작한다(빈 좌석을 클릭해야 실제로 앉는다) — 비공개방이라도
// 비밀번호를 묻지 않는다(roomCode를 안다는 것 자체를 초대로 간주한다, spectateByCode 참고).
export function JoinByCodeModal({ onClose }: JoinByCodeModalProps) {
  const { spectateByCode } = useRoom();
  const [code, setCode] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const trimmed = code.trim().toUpperCase();
    if (!trimmed) {
      return;
    }
    setSubmitting(true);
    try {
      await spectateByCode(trimmed);
    } finally {
      setSubmitting(false);
      onClose();
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60"
      onClick={onClose}
      role="presentation"
    >
      <div
        className="w-full max-w-xs rounded-lg bg-slate-800 p-6"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
      >
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold">코드로 입장</h2>
          <button className="text-slate-400 hover:text-slate-200" onClick={onClose} aria-label="닫기">
            ✕
          </button>
        </div>
        <form onSubmit={handleSubmit} className="flex flex-col gap-3">
          <input
            className="rounded bg-slate-700 px-3 py-2 text-center font-mono uppercase tracking-widest outline-none"
            value={code}
            onChange={(e) => setCode(e.target.value)}
            placeholder="방 코드"
            autoFocus
          />
          <button
            className="rounded bg-emerald-600 px-4 py-2 font-medium disabled:opacity-50"
            type="submit"
            disabled={submitting}
          >
            입장
          </button>
        </form>
      </div>
    </div>
  );
}
