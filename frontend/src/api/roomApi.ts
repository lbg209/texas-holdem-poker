import { getJson, postJson } from './httpClient';
import type { JoinPlayerResponse, RoomStateResponse } from '../types/room';

// GET /api/room?playerId=... — playerId를 생략하면 관전자 시점으로 조회된다(백엔드 규칙과 동일).
export function getRoomState(playerId?: string | null): Promise<RoomStateResponse> {
  const query = playerId ? `?playerId=${encodeURIComponent(playerId)}` : '';
  return getJson<RoomStateResponse>(`/api/room${query}`);
}

// POST /api/room/players
export function joinRoom(nickname: string): Promise<JoinPlayerResponse> {
  return postJson<JoinPlayerResponse>('/api/room/players', { nickname });
}

// POST /api/room/hands?playerId=... — playerId를 주면 응답 자체에 본인 홀카드가 바로 포함된다.
// 프론트엔드에는 이제 이걸 직접 호출하는 버튼이 없다(레디 시스템의 자동 시작으로 대체) — 디버깅용으로만 남겨둠.
export function startHand(playerId?: string | null): Promise<RoomStateResponse> {
  const query = playerId ? `?playerId=${encodeURIComponent(playerId)}` : '';
  return postJson<RoomStateResponse>(`/api/room/hands${query}`);
}

// POST /api/room/ready?playerId=...&ready=... — 다음 핸드 자동 시작에 동의하는지 토글한다.
// 핸드 진행 중에도 호출 가능하다(이번 핸드에는 영향 없음).
export function setReady(playerId: string, ready: boolean): Promise<RoomStateResponse> {
  return postJson<RoomStateResponse>(
    `/api/room/ready?playerId=${encodeURIComponent(playerId)}&ready=${ready}`,
  );
}

// POST /api/room/reveal?playerId=... — 폴드로 이긴 핸드의 승자가 자기 카드를 자원해서 공개한다.
export function revealFoldWinHand(playerId: string): Promise<RoomStateResponse> {
  return postJson<RoomStateResponse>(`/api/room/reveal?playerId=${encodeURIComponent(playerId)}`);
}

// POST /api/room/showdown-decision?playerId=...&reveal=... — 헤즈업 쇼다운 결정자가 공개(true)
// 또는 머크(false)를 선택한다.
export function decideShowdownReveal(playerId: string, reveal: boolean): Promise<RoomStateResponse> {
  return postJson<RoomStateResponse>(
    `/api/room/showdown-decision?playerId=${encodeURIComponent(playerId)}&reveal=${reveal}`,
  );
}
