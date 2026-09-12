import { getJson, postJson } from './httpClient';
import type {
  CreateRoomResponse,
  HandHistoryEntryView,
  JoinPlayerResponse,
  RoomStateResponse,
  RoomSummaryView,
} from '../types/room';

// GET /api/rooms — 로비 방 목록(비공개방은 제외되어 내려온다).
export function listRooms(): Promise<RoomSummaryView[]> {
  return getJson<RoomSummaryView[]>('/api/rooms');
}

// POST /api/rooms — 방을 만들기만 하고 만든 사람을 자동으로 입장시키지는 않는다. 스몰블라인드는
// 보내지 않는다 — 서버가 항상 빅블라인드의 절반으로 자동 계산한다. 좌석 수는 더 이상 설정할 수
// 없다(고정 좌석제 도입으로 항상 6석).
export function createRoom(
  name: string,
  isPrivate: boolean,
  password: string | undefined,
  startingChips: number,
  bigBlind: number,
): Promise<CreateRoomResponse> {
  return postJson<CreateRoomResponse>('/api/rooms', { name, isPrivate, password, startingChips, bigBlind });
}

// GET /api/rooms/{roomCode}?playerId=... — playerId를 생략하면 관전자 시점으로 조회된다.
// 로비에서 방을 클릭했을 때(또는 "코드로 입장")의 좌측 정보 패널, 그리고 새로고침 후 재접속
// 검증에도 쓰인다.
export function getRoomState(roomCode: string, playerId?: string | null): Promise<RoomStateResponse> {
  const query = playerId ? `?playerId=${encodeURIComponent(playerId)}` : '';
  return getJson<RoomStateResponse>(`/api/rooms/${roomCode}${query}`);
}

// POST /api/rooms/{roomCode}/players — authToken이 유효하면 nickname은 무시되고 계정 닉네임이
// 대신 쓰인다(게스트는 authToken 없이 nickname만 보낸다). password는 비공개방에 입장할 때만 필요하다.
// seatIndex는 고정 좌석제 도입 후 사용자가 클릭한 좌석 번호 — 생략하면 서버가 빈 좌석 중 가장
// 낮은 번호에 자동으로 앉힌다.
export function joinRoom(
  roomCode: string,
  nickname?: string,
  authToken?: string,
  password?: string,
  seatIndex?: number,
): Promise<JoinPlayerResponse> {
  return postJson<JoinPlayerResponse>(`/api/rooms/${roomCode}/players`, { nickname, authToken, password, seatIndex });
}

// POST /api/rooms/{roomCode}/players/by-code — "코드로 입장". roomCode를 직접 입력해서 들어오는
// 경우는 비공개방이라도 비밀번호를 묻지 않는다(roomCode를 안다는 것 자체를 초대로 간주).
export function joinRoomByCode(
  roomCode: string,
  nickname?: string,
  authToken?: string,
  seatIndex?: number,
): Promise<JoinPlayerResponse> {
  return postJson<JoinPlayerResponse>(`/api/rooms/${roomCode}/players/by-code`, { nickname, authToken, seatIndex });
}

// POST /api/rooms/{roomCode}/players/{playerId}/seat?seatIndex=... — 이미 앉아있는 플레이어가
// 다른 빈 좌석으로 옮긴다. 핸드 진행 중이거나, 너무 빠르게 연속으로 옮기려 하거나, 그 좌석이
// 이미 차 있으면 실패한다.
export function moveSeat(roomCode: string, playerId: string, seatIndex: number): Promise<RoomStateResponse> {
  return postJson<RoomStateResponse>(
    `/api/rooms/${roomCode}/players/${encodeURIComponent(playerId)}/seat?seatIndex=${seatIndex}`,
  );
}

// POST /api/rooms/{roomCode}/hands?playerId=... — playerId를 주면 응답 자체에 본인 홀카드가 바로
// 포함된다. 프론트엔드에는 이제 이걸 직접 호출하는 버튼이 없다(레디 시스템의 자동 시작으로 대체) —
// 디버깅용으로만 남겨둠.
export function startHand(roomCode: string, playerId?: string | null): Promise<RoomStateResponse> {
  const query = playerId ? `?playerId=${encodeURIComponent(playerId)}` : '';
  return postJson<RoomStateResponse>(`/api/rooms/${roomCode}/hands${query}`);
}

// POST /api/rooms/{roomCode}/ready?playerId=...&ready=... — 다음 핸드 자동 시작에 동의하는지
// 토글한다. 핸드 진행 중에도 호출 가능하다(이번 핸드에는 영향 없음).
export function setReady(roomCode: string, playerId: string, ready: boolean): Promise<RoomStateResponse> {
  return postJson<RoomStateResponse>(
    `/api/rooms/${roomCode}/ready?playerId=${encodeURIComponent(playerId)}&ready=${ready}`,
  );
}

// POST /api/rooms/{roomCode}/leave?playerId=...&leaving=... — "나가기"를 예약(true)하거나
// 취소(false)한다. 핸드 진행 중이 아니면 즉시 방에서 빠지고, 진행 중이면 이번 핸드가 끝나는 시점에 빠진다.
export function requestLeave(roomCode: string, playerId: string, leaving: boolean): Promise<RoomStateResponse> {
  return postJson<RoomStateResponse>(
    `/api/rooms/${roomCode}/leave?playerId=${encodeURIComponent(playerId)}&leaving=${leaving}`,
  );
}

// GET /api/rooms/{roomCode}/history?playerId=... — 이 방의 최근 핸드 히스토리(최대 30개, 최신순).
// playerId를 주면 본인 카드 + 그 핸드에서 실제로 공개됐던 카드만 보이고, 생략하면 관전자 시점으로
// 내려간다(아무도 공개 안 한 카드는 전부 숨김).
export function getHandHistory(roomCode: string, playerId?: string | null): Promise<HandHistoryEntryView[]> {
  const query = playerId ? `?playerId=${encodeURIComponent(playerId)}` : '';
  return getJson<HandHistoryEntryView[]>(`/api/rooms/${roomCode}/history${query}`);
}

// POST /api/rooms/{roomCode}/players/{targetId}/kick?requesterId=... — 방장이 레디 안 한 플레이어를
// 강퇴한다. 핸드 진행 중이거나, 대상이 레디했거나, 레디 안 한 지 3초가 안 지났으면 실패한다(구체적인
// 남은 시간은 응답에 노출되지 않는다 — 너무 일찍 시도하면 그냥 실패 메시지만 온다).
export function kickPlayer(roomCode: string, requesterId: string, targetId: string): Promise<RoomStateResponse> {
  return postJson<RoomStateResponse>(
    `/api/rooms/${roomCode}/players/${encodeURIComponent(targetId)}/kick?requesterId=${encodeURIComponent(requesterId)}`,
  );
}

// POST /api/rooms/{roomCode}/reveal?playerId=... — 폴드로 이긴 핸드의 승자가 자기 카드를 자원해서 공개한다.
export function revealFoldWinHand(roomCode: string, playerId: string): Promise<RoomStateResponse> {
  return postJson<RoomStateResponse>(`/api/rooms/${roomCode}/reveal?playerId=${encodeURIComponent(playerId)}`);
}

// POST /api/rooms/{roomCode}/showdown-decision?playerId=...&reveal=... — 헤즈업 쇼다운 결정자가
// 공개(true) 또는 머크(false)를 선택한다.
export function decideShowdownReveal(
  roomCode: string,
  playerId: string,
  reveal: boolean,
): Promise<RoomStateResponse> {
  return postJson<RoomStateResponse>(
    `/api/rooms/${roomCode}/showdown-decision?playerId=${encodeURIComponent(playerId)}&reveal=${reveal}`,
  );
}
