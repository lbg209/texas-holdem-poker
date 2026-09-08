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
export function startHand(playerId?: string | null): Promise<RoomStateResponse> {
  const query = playerId ? `?playerId=${encodeURIComponent(playerId)}` : '';
  return postJson<RoomStateResponse>(`/api/room/hands${query}`);
}
