import { createContext, useCallback, useContext, useEffect, useReducer, useRef } from 'react';
import type { ReactNode } from 'react';
import {
  createRoom as createRoomApi,
  decideShowdownReveal,
  getRoomState,
  joinRoom as joinRoomApi,
  joinRoomByCode as joinRoomByCodeApi,
  kickPlayer as kickPlayerApi,
  listRooms as listRoomsApi,
  moveSeat as moveSeatApi,
  requestLeave,
  revealFoldWinHand,
  setReady,
  startHand,
} from '../api/roomApi';
import { login as loginApi } from '../api/authApi';
import { clearStoredPlayerId, getStoredPlayerId, storePlayerId } from './playerIdStorage';
import { clearStoredRoomCode, getStoredRoomCode, storeRoomCode } from './roomCodeStorage';
import { clearStoredGuestNickname, getStoredGuestNickname, storeGuestNickname } from './guestNicknameStorage';
import { useRoomSocket } from '../ws/useRoomSocket';
import type { ConnectionStatus, ServerMessage } from '../ws/useRoomSocket';
import type { PlayerActionType, RoomStateResponse, RoomSummaryView } from '../types/room';
import { useTableAnimationQueue } from './useTableAnimationQueue';
import type { ActiveVisualEvent } from './tableAnimation';

const ERROR_AUTO_DISMISS_MS = 6000;

type Screen = 'auth' | 'lobby' | 'table';

interface RoomState {
  screen: Screen;
  // 게스트 닉네임(로그인 없이 로비에 들어온 경우)과 로그인 토큰 중 하나만 채워진다.
  guestNickname: string | null;
  authToken: string | null;
  // 로그인 사용자의 표시용 닉네임(로비 상단 식별 표시용). username(로그인 아이디)은 절대 여기 담지
  // 않는다 — 방에 노출하지 않으려고 애초에 username/nickname을 분리한 결정과 일관성을 유지한다.
  loggedInNickname: string | null;
  roomCode: string | null;
  myPlayerId: string | null;
  roomList: RoomSummaryView[];
  // 로비에서 방을 클릭(또는 코드로 조회)했을 때 좌측 정보 패널에 보여줄 상세. 아직 입장 전이다.
  selectedRoomDetail: RoomStateResponse | null;
  roomState: RoomStateResponse | null;
  error: string | null;
  // 저장된 roomCode+playerId를 GET /api/rooms/{roomCode}로 검증하는 동안(true) 화면이 잠깐 보이지 않게 한다.
  isVerifying: boolean;
  // 고정 좌석제 — 빈자리를 클릭해서 "입장"할 때 실제로 써야 하는 비밀번호/경로를 스펙테이팅
  // 중에도 기억해둔다(좌석 클릭 시점에야 실제 입장 API를 호출하므로). 이미 앉은 뒤로는 둘 다 의미 없다.
  pendingJoinPassword: string | null;
  pendingJoinByCode: boolean;
}

type Action =
  | { type: 'GUEST_IDENTITY_SET'; payload: { nickname: string } }
  | { type: 'LOGGED_IN'; payload: { token: string; nickname: string } }
  | { type: 'LOGGED_OUT' }
  | { type: 'ROOM_LIST_RECEIVED'; payload: RoomSummaryView[] }
  | { type: 'ROOM_DETAIL_SELECTED'; payload: RoomStateResponse }
  | { type: 'ROOM_DETAIL_CLEARED' }
  | { type: 'SPECTATING_ROOM'; payload: { roomCode: string; password?: string; byCode?: boolean } }
  | { type: 'JOINED_ROOM'; payload: { roomCode: string; playerId: string } }
  | { type: 'ROOM_STATE_RECEIVED'; payload: RoomStateResponse }
  | { type: 'LEFT_ROOM' }
  | { type: 'VERIFY_DONE_NO_SESSION' }
  | { type: 'ERROR_OCCURRED'; payload: string }
  | { type: 'CLEAR_ERROR' };

function reducer(state: RoomState, action: Action): RoomState {
  switch (action.type) {
    case 'GUEST_IDENTITY_SET':
      return { ...state, guestNickname: action.payload.nickname, screen: 'lobby', error: null };
    case 'LOGGED_IN':
      return {
        ...state,
        authToken: action.payload.token,
        loggedInNickname: action.payload.nickname,
        screen: 'lobby',
        error: null,
      };
    case 'LOGGED_OUT':
      return {
        ...state,
        guestNickname: null,
        authToken: null,
        loggedInNickname: null,
        roomList: [],
        selectedRoomDetail: null,
        screen: 'auth',
        error: null,
      };
    case 'ROOM_LIST_RECEIVED':
      return { ...state, roomList: action.payload };
    case 'ROOM_DETAIL_SELECTED':
      return { ...state, selectedRoomDetail: action.payload, error: null };
    case 'ROOM_DETAIL_CLEARED':
      return { ...state, selectedRoomDetail: null };
    case 'SPECTATING_ROOM':
      return {
        ...state,
        roomCode: action.payload.roomCode,
        myPlayerId: null,
        selectedRoomDetail: null,
        screen: 'table',
        pendingJoinPassword: action.payload.password ?? null,
        pendingJoinByCode: action.payload.byCode ?? false,
        error: null,
      };
    case 'JOINED_ROOM':
      return {
        ...state,
        roomCode: action.payload.roomCode,
        myPlayerId: action.payload.playerId,
        selectedRoomDetail: null,
        screen: 'table',
        pendingJoinPassword: null,
        pendingJoinByCode: false,
        error: null,
      };
    case 'ROOM_STATE_RECEIVED':
      return { ...state, roomState: action.payload, isVerifying: false, error: null };
    case 'LEFT_ROOM':
      return {
        ...state,
        roomCode: null,
        myPlayerId: null,
        roomState: null,
        // 새 탭으로 열어서 roomCode+playerId(localStorage, 탭 간 공유)만으로 테이블 화면을 복원한
        // 경우, 그 탭의 sessionStorage에는 게스트 닉네임이 없을 수 있다(로그인/게스트 화면을 거친
        // 적이 없으므로) — 그 상태로 로비에 보내면 방 만들기 등에서 닉네임이 비어 있는 채로 요청이
        // 나가 실패한다. 신원(게스트 닉네임/로그인)이 없으면 로비 대신 로그인/게스트 화면으로 보낸다.
        screen: state.guestNickname || state.authToken ? 'lobby' : 'auth',
        isVerifying: false,
        pendingJoinPassword: null,
        pendingJoinByCode: false,
      };
    case 'VERIFY_DONE_NO_SESSION':
      return { ...state, isVerifying: false };
    case 'ERROR_OCCURRED':
      return { ...state, error: action.payload, isVerifying: false };
    case 'CLEAR_ERROR':
      return { ...state, error: null };
    default:
      return state;
  }
}

interface RoomContextValue {
  state: RoomState & {
    connectionStatus: ConnectionStatus;
    // 테이블에 실제로 그려지는(그리고 "지금 내 차례인지" 판단에도 쓰이는), 애니메이션 큐로 지연
    // 재생되는 상태. roomState(진짜 최신 상태)와 달리 액션 표시/칩 이동/카드 등장이 끝날 때까지
    // 옛 값에 머물러 있을 수 있다.
    displayState: RoomStateResponse | null;
    activeVisualEvent: ActiveVisualEvent | null;
    dealProgress: Record<string, number> | null;
  };
  enterLobbyAsGuest: (nickname: string) => void;
  loginAndEnterLobby: (username: string, password: string) => Promise<void>;
  logout: () => void;
  refreshRoomList: () => Promise<void>;
  createNewRoom: (
    name: string,
    isPrivate: boolean,
    password: string | undefined,
    startingChips: number,
    bigBlind: number,
  ) => Promise<void>;
  selectRoom: (roomCode: string) => Promise<void>;
  clearSelectedRoom: () => void;
  // 고정 좌석제 — 로비에서 "입장"을 누르면 바로 앉지 않고 먼저 관전자로 테이블을 보여준다.
  // 실제 입장은 claimSeat(빈 좌석 클릭)에서 일어난다.
  spectateSelectedRoom: (password?: string) => void;
  spectateByCode: (roomCode: string) => Promise<void>;
  leaveSpectating: () => void;
  claimSeat: (seatIndex: number) => Promise<void>;
  moveSeat: (seatIndex: number) => Promise<void>;
  startNewHand: () => Promise<void>;
  toggleReady: (ready: boolean) => Promise<void>;
  toggleLeave: (leaving: boolean) => Promise<void>;
  kickPlayer: (targetId: string) => Promise<void>;
  revealHand: () => Promise<void>;
  decideReveal: (reveal: boolean) => Promise<void>;
  sendAction: (action: PlayerActionType, amount: number) => void;
  // 헤즈업 쇼다운 공개/머크 결정 패널이 화면에 뜬 순간(ShowdownDecisionPanel)에만 호출된다 —
  // 자세한 이유는 useRoomSocket.sendHeadsUpRevealReady 참고.
  sendHeadsUpRevealReady: () => void;
  reconnect: () => void;
  refreshState: () => Promise<void>;
  clearError: () => void;
}

const RoomContext = createContext<RoomContextValue | null>(null);

export function RoomProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(reducer, {
    screen: 'auth',
    // 새로고침 직후에도(특히 이미 앉은 좌석을 복원하는 케이스) 나중에 다른 방에 새로 입장할 때
    // 다시 쓸 수 있도록 sessionStorage에서 복원한다 — 비어 있어도(진짜 첫 방문) null이라 문제 없다.
    guestNickname: getStoredGuestNickname(),
    authToken: null,
    loggedInNickname: null,
    roomCode: null,
    myPlayerId: null,
    roomList: [],
    selectedRoomDetail: null,
    roomState: null,
    error: null,
    isVerifying: Boolean(getStoredRoomCode() && getStoredPlayerId()),
    pendingJoinPassword: null,
    pendingJoinByCode: false,
  });

  // 마운트 시 저장된 roomCode+playerId를 "그대로 신뢰"하지 않고, GET /api/rooms/{roomCode}로 서버에
  // 실제 존재하는지 검증한 뒤에만 테이블 화면으로 복원한다. 없거나(서버 재시작 등) 이미 빠진 좌석이면
  // 조용히 지우고 로그인/게스트 화면부터 다시 시작한다.
  useEffect(() => {
    const roomCode = getStoredRoomCode();
    const playerId = getStoredPlayerId();
    if (!roomCode || !playerId) {
      dispatch({ type: 'VERIFY_DONE_NO_SESSION' });
      return;
    }

    getRoomState(roomCode, playerId)
      .then((roomState) => {
        const stillExists = roomState.players.some((p) => p.id === playerId);
        if (stillExists) {
          dispatch({ type: 'JOINED_ROOM', payload: { roomCode, playerId } });
          dispatch({ type: 'ROOM_STATE_RECEIVED', payload: roomState });
        } else {
          clearStoredPlayerId();
          clearStoredRoomCode();
          dispatch({ type: 'VERIFY_DONE_NO_SESSION' });
        }
      })
      .catch(() => {
        clearStoredPlayerId();
        clearStoredRoomCode();
        dispatch({ type: 'VERIFY_DONE_NO_SESSION' });
      });
  }, []);

  // ERROR 배너는 6초 후 자동으로 사라진다(수동 닫기는 clearError로 별도 제공).
  // 이전 에러가 사라지기 전에 새 에러가 오면 타이머를 새로 시작한다.
  useEffect(() => {
    if (!state.error) {
      return;
    }
    const timer = window.setTimeout(() => dispatch({ type: 'CLEAR_ERROR' }), ERROR_AUTO_DISMISS_MS);
    return () => window.clearTimeout(timer);
  }, [state.error]);

  // handleWsMessage는 useCallback(deps:[])로 한 번만 만들어져서 항상 마운트 시점의 값을 보므로,
  // 최신 myPlayerId는 ref로 읽는다 — 그래야 "나가기 예약된 핸드가 끝나서 서버가 나를 방에서
  // 제거했다"는 소식이 (내가 직접 요청한 게 아니라) 다른 사람의 액션으로 온 브로드캐스트에도
  // 똑같이 반영되어 자동으로 로비로 돌아간다.
  const myPlayerIdRef = useRef<string | null>(null);
  useEffect(() => {
    myPlayerIdRef.current = state.myPlayerId;
  }, [state.myPlayerId]);

  // WebSocket이 STATE/ERROR를 수신할 때마다 반영한다.
  const handleWsMessage = useCallback((msg: ServerMessage) => {
    if (msg.type === 'STATE') {
      const myId = myPlayerIdRef.current;
      const stillSeated = myId === null || msg.state.players.some((p) => p.id === myId);
      if (!stillSeated) {
        clearStoredPlayerId();
        clearStoredRoomCode();
        dispatch({ type: 'LEFT_ROOM' });
        return;
      }
      dispatch({ type: 'ROOM_STATE_RECEIVED', payload: msg.state });
    } else {
      dispatch({ type: 'ERROR_OCCURRED', payload: msg.message });
    }
  }, []);

  // roomCode가 null이면(로비에 있는 동안) WebSocket 자체를 연결하지 않는다 — useRoomSocket 참고.
  const {
    connectionStatus,
    sendAction: rawSendAction,
    sendHeadsUpRevealReady,
    reconnect,
  } = useRoomSocket(state.roomCode, state.myPlayerId, handleWsMessage);
  const { displayState, activeVisualEvent, dealProgress } = useTableAnimationQueue(state.roomState);

  // 소켓이 아직 준비되지 않은 상태에서 액션을 보내려 하면(재연결 중 등) 예전엔 조용히
  // 무시됐다 — 버튼이 눌리는 것처럼 보이는데 아무 반응이 없는 버그로 이어졌다. 실패 시
  // 에러를 보여주고 재연결을 바로 시도해, 최소한 원인이 보이고 스스로 복구를 시도하게 한다.
  const sendAction = useCallback(
    (action: PlayerActionType, amount: number) => {
      const sent = rawSendAction(action, amount);
      if (!sent) {
        dispatch({ type: 'ERROR_OCCURRED', payload: '연결이 불안정합니다. 재연결을 시도합니다...' });
        reconnect();
      }
    },
    [rawSendAction, reconnect],
  );

  const enterLobbyAsGuest = (nickname: string) => {
    storeGuestNickname(nickname);
    dispatch({ type: 'GUEST_IDENTITY_SET', payload: { nickname } });
  };

  const loginAndEnterLobby = async (username: string, password: string) => {
    try {
      const { token, nickname } = await loginApi(username, password);
      dispatch({ type: 'LOGGED_IN', payload: { token, nickname } });
    } catch (e) {
      dispatch({ type: 'ERROR_OCCURRED', payload: (e as Error).message });
    }
  };

  // 로비에서만 호출된다(테이블에 들어가 있으면 먼저 나가기부터 해야 함) — 서버에 토큰을 무효화하는
  // 별도 API는 없다(AuthService가 메모리 토큰만 관리하며 서버 재시작 시 어차피 전부 로그아웃됨).
  // 클라이언트 쪽 식별 정보만 지우고 로그인/게스트 화면으로 돌아간다.
  const logout = () => {
    clearStoredGuestNickname();
    dispatch({ type: 'LOGGED_OUT' });
  };

  const refreshRoomList = async () => {
    try {
      const rooms = await listRoomsApi();
      dispatch({ type: 'ROOM_LIST_RECEIVED', payload: rooms });
    } catch (e) {
      dispatch({ type: 'ERROR_OCCURRED', payload: (e as Error).message });
    }
  };

  // 입장 API가 playerId를 내려준 뒤 공통으로 해야 할 일(저장 + 화면 전환 + 최신 상태 조회).
  const completeJoin = async (roomCode: string, playerId: string) => {
    storePlayerId(playerId);
    storeRoomCode(roomCode);
    dispatch({ type: 'JOINED_ROOM', payload: { roomCode, playerId } });
    const roomState = await getRoomState(roomCode, playerId);
    dispatch({ type: 'ROOM_STATE_RECEIVED', payload: roomState });
  };

  // 방 만들기 직후 전용 — 방금 만든(아무도 없는) 방이라 굳이 좌석을 고를 필요가 없으므로, 빈 좌석
  // 중 가장 낮은 번호(항상 0번)에 바로 앉는다. 로비에서 고른 방은 대신 spectateSelectedRoom으로
  // 먼저 관전하다가 claimSeat로 실제 좌석을 고른다.
  const joinAndEnter = async (roomCode: string, password?: string) => {
    const { playerId } = await joinRoomApi(roomCode, state.guestNickname ?? undefined, state.authToken ?? undefined, password);
    await completeJoin(roomCode, playerId);
  };

  const createNewRoom = async (
    name: string,
    isPrivate: boolean,
    password: string | undefined,
    startingChips: number,
    bigBlind: number,
  ) => {
    try {
      const { roomCode } = await createRoomApi(name, isPrivate, password, startingChips, bigBlind);
      // 만들자마자 바로 입장한다 — 로비로 돌아가서 다시 클릭할 필요가 없다.
      await joinAndEnter(roomCode, isPrivate ? password : undefined);
    } catch (e) {
      dispatch({ type: 'ERROR_OCCURRED', payload: (e as Error).message });
    }
  };

  const selectRoom = async (roomCode: string) => {
    try {
      const detail = await getRoomState(roomCode, null);
      dispatch({ type: 'ROOM_DETAIL_SELECTED', payload: detail });
    } catch (e) {
      dispatch({ type: 'ERROR_OCCURRED', payload: (e as Error).message });
    }
  };

  const clearSelectedRoom = () => dispatch({ type: 'ROOM_DETAIL_CLEARED' });

  // 로비에서 고른 방에 "입장"을 누르면 바로 앉지 않고, 먼저 관전자로 테이블을 연다(좌석 클릭으로
  // 실제로 앉기 전까지). 비밀번호는 나중에 claimSeat가 호출될 때 쓰도록 기억해둔다.
  const spectateSelectedRoom = (password?: string) => {
    const detail = state.selectedRoomDetail;
    if (!detail) {
      return;
    }
    dispatch({ type: 'SPECTATING_ROOM', payload: { roomCode: detail.roomCode, password } });
  };

  // "코드로 입장" — roomCode를 직접 입력해서 관전을 시작한다(비공개방이라도 비밀번호를 묻지 않는
  // 경로이므로 claimSeat 시점에도 비밀번호가 필요 없다). 존재하지 않는 코드는 여기서 바로 에러로 안내한다.
  const spectateByCode = async (roomCode: string) => {
    try {
      await getRoomState(roomCode, null);
      dispatch({ type: 'SPECTATING_ROOM', payload: { roomCode, byCode: true } });
    } catch (e) {
      dispatch({ type: 'ERROR_OCCURRED', payload: (e as Error).message });
    }
  };

  // 아직 좌석을 고르지 않은 관전 상태에서 로비로 돌아간다.
  const leaveSpectating = () => {
    dispatch({ type: 'LEFT_ROOM' });
  };

  // 빈 좌석을 클릭해서 실제로 입장한다. spectateSelectedRoom/spectateByCode가 기억해둔
  // 비밀번호/경로에 따라 비밀번호 검사 있는 API와 없는 API("코드로 입장")를 갈라 호출한다.
  const claimSeat = async (seatIndex: number) => {
    if (!state.roomCode) {
      return;
    }
    try {
      const { playerId } = state.pendingJoinByCode
        ? await joinRoomByCodeApi(state.roomCode, state.guestNickname ?? undefined, state.authToken ?? undefined, seatIndex)
        : await joinRoomApi(
            state.roomCode,
            state.guestNickname ?? undefined,
            state.authToken ?? undefined,
            state.pendingJoinPassword ?? undefined,
            seatIndex,
          );
      await completeJoin(state.roomCode, playerId);
    } catch (e) {
      dispatch({ type: 'ERROR_OCCURRED', payload: (e as Error).message });
    }
  };

  // 이미 앉아있는 플레이어가 다른 빈 좌석으로 옮긴다. 핸드 진행 중이거나 너무 빠르게 연속으로
  // 옮기려 하면 서버가 거부한다(SeatLayout이 UI에서도 미리 막아주지만, 최종 검증은 서버 책임).
  const moveSeat = async (seatIndex: number) => {
    if (!state.myPlayerId || !state.roomCode) {
      return;
    }
    try {
      const roomState = await moveSeatApi(state.roomCode, state.myPlayerId, seatIndex);
      dispatch({ type: 'ROOM_STATE_RECEIVED', payload: roomState });
    } catch (e) {
      dispatch({ type: 'ERROR_OCCURRED', payload: (e as Error).message });
    }
  };

  const startNewHand = async () => {
    if (!state.roomCode) {
      return;
    }
    try {
      const roomState = await startHand(state.roomCode, state.myPlayerId);
      dispatch({ type: 'ROOM_STATE_RECEIVED', payload: roomState });
    } catch (e) {
      dispatch({ type: 'ERROR_OCCURRED', payload: (e as Error).message });
    }
  };

  const toggleReady = async (ready: boolean) => {
    if (!state.myPlayerId || !state.roomCode) {
      return;
    }
    try {
      const roomState = await setReady(state.roomCode, state.myPlayerId, ready);
      dispatch({ type: 'ROOM_STATE_RECEIVED', payload: roomState });
    } catch (e) {
      dispatch({ type: 'ERROR_OCCURRED', payload: (e as Error).message });
    }
  };

  const toggleLeave = async (leaving: boolean) => {
    if (!state.myPlayerId || !state.roomCode) {
      return;
    }
    try {
      const roomState = await requestLeave(state.roomCode, state.myPlayerId, leaving);
      // 핸드 진행 중이 아니었다면 이 응답 자체에서 이미 제거됐을 수 있다 — WS 브로드캐스트를
      // 기다리지 않고 바로 로비로 돌아간다.
      const stillSeated = roomState.players.some((p) => p.id === state.myPlayerId);
      if (!stillSeated) {
        clearStoredPlayerId();
        clearStoredRoomCode();
        dispatch({ type: 'LEFT_ROOM' });
        return;
      }
      dispatch({ type: 'ROOM_STATE_RECEIVED', payload: roomState });
    } catch (e) {
      dispatch({ type: 'ERROR_OCCURRED', payload: (e as Error).message });
    }
  };

  // 방장이 레디 안 한 플레이어를 강퇴한다. 실패(핸드 진행 중/레디함/유예 시간 안 지남)해도 남은
  // 시간 같은 세부 사항은 안내하지 않고, 서버가 보낸 에러 메시지만 그대로 보여준다.
  const kickPlayer = async (targetId: string) => {
    if (!state.myPlayerId || !state.roomCode) {
      return;
    }
    try {
      const roomState = await kickPlayerApi(state.roomCode, state.myPlayerId, targetId);
      dispatch({ type: 'ROOM_STATE_RECEIVED', payload: roomState });
    } catch (e) {
      dispatch({ type: 'ERROR_OCCURRED', payload: (e as Error).message });
    }
  };

  const revealHand = async () => {
    if (!state.myPlayerId || !state.roomCode) {
      return;
    }
    try {
      const roomState = await revealFoldWinHand(state.roomCode, state.myPlayerId);
      dispatch({ type: 'ROOM_STATE_RECEIVED', payload: roomState });
    } catch (e) {
      dispatch({ type: 'ERROR_OCCURRED', payload: (e as Error).message });
    }
  };

  const decideReveal = async (reveal: boolean) => {
    if (!state.myPlayerId || !state.roomCode) {
      return;
    }
    try {
      const roomState = await decideShowdownReveal(state.roomCode, state.myPlayerId, reveal);
      dispatch({ type: 'ROOM_STATE_RECEIVED', payload: roomState });
    } catch (e) {
      dispatch({ type: 'ERROR_OCCURRED', payload: (e as Error).message });
    }
  };

  const clearError = () => dispatch({ type: 'CLEAR_ERROR' });

  // 화면의 "상태 새로고침" 버튼 — WS가 끊겼거나 최신 상태가 의심스러울 때 수동으로 다시 받아온다.
  const refreshState = async () => {
    if (!state.roomCode) {
      return;
    }
    try {
      const roomState = await getRoomState(state.roomCode, state.myPlayerId);
      dispatch({ type: 'ROOM_STATE_RECEIVED', payload: roomState });
    } catch (e) {
      dispatch({ type: 'ERROR_OCCURRED', payload: (e as Error).message });
    }
  };

  return (
    <RoomContext.Provider
      value={{
        state: { ...state, connectionStatus, displayState, activeVisualEvent, dealProgress },
        enterLobbyAsGuest,
        loginAndEnterLobby,
        logout,
        refreshRoomList,
        createNewRoom,
        selectRoom,
        clearSelectedRoom,
        spectateSelectedRoom,
        spectateByCode,
        leaveSpectating,
        claimSeat,
        moveSeat,
        startNewHand,
        toggleReady,
        toggleLeave,
        kickPlayer,
        revealHand,
        decideReveal,
        sendAction,
        sendHeadsUpRevealReady,
        reconnect,
        refreshState,
        clearError,
      }}
    >
      {children}
    </RoomContext.Provider>
  );
}

export function useRoom(): RoomContextValue {
  const ctx = useContext(RoomContext);
  if (!ctx) {
    throw new Error('useRoom은 RoomProvider 내부에서만 사용할 수 있습니다.');
  }
  return ctx;
}
