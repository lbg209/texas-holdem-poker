import { createContext, useCallback, useContext, useEffect, useReducer, useRef } from 'react';
import type { ReactNode } from 'react';
import {
  createRoom as createRoomApi,
  decideShowdownReveal,
  getRoomState,
  joinRoom as joinRoomApi,
  joinRoomByCode as joinRoomByCodeApi,
  listRooms as listRoomsApi,
  requestLeave,
  revealFoldWinHand,
  setReady,
  startHand,
} from '../api/roomApi';
import { login as loginApi } from '../api/authApi';
import { clearStoredPlayerId, getStoredPlayerId, storePlayerId } from './playerIdStorage';
import { clearStoredRoomCode, getStoredRoomCode, storeRoomCode } from './roomCodeStorage';
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
}

type Action =
  | { type: 'GUEST_IDENTITY_SET'; payload: { nickname: string } }
  | { type: 'LOGGED_IN'; payload: { token: string; nickname: string } }
  | { type: 'LOGGED_OUT' }
  | { type: 'ROOM_LIST_RECEIVED'; payload: RoomSummaryView[] }
  | { type: 'ROOM_DETAIL_SELECTED'; payload: RoomStateResponse }
  | { type: 'ROOM_DETAIL_CLEARED' }
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
    case 'JOINED_ROOM':
      return {
        ...state,
        roomCode: action.payload.roomCode,
        myPlayerId: action.payload.playerId,
        selectedRoomDetail: null,
        screen: 'table',
        error: null,
      };
    case 'ROOM_STATE_RECEIVED':
      return { ...state, roomState: action.payload, isVerifying: false, error: null };
    case 'LEFT_ROOM':
      return { ...state, roomCode: null, myPlayerId: null, roomState: null, screen: 'lobby', isVerifying: false };
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
    maxPlayers: number,
  ) => Promise<void>;
  selectRoom: (roomCode: string) => Promise<void>;
  clearSelectedRoom: () => void;
  joinSelectedRoom: (password?: string) => Promise<void>;
  joinByCode: (roomCode: string) => Promise<void>;
  startNewHand: () => Promise<void>;
  toggleReady: (ready: boolean) => Promise<void>;
  toggleLeave: (leaving: boolean) => Promise<void>;
  revealHand: () => Promise<void>;
  decideReveal: (reveal: boolean) => Promise<void>;
  sendAction: (action: PlayerActionType, amount: number) => void;
  reconnect: () => void;
  clearError: () => void;
}

const RoomContext = createContext<RoomContextValue | null>(null);

export function RoomProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(reducer, {
    screen: 'auth',
    guestNickname: null,
    authToken: null,
    loggedInNickname: null,
    roomCode: null,
    myPlayerId: null,
    roomList: [],
    selectedRoomDetail: null,
    roomState: null,
    error: null,
    isVerifying: Boolean(getStoredRoomCode() && getStoredPlayerId()),
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
  const { connectionStatus, sendAction, reconnect } = useRoomSocket(state.roomCode, state.myPlayerId, handleWsMessage);
  const { displayState, activeVisualEvent, dealProgress } = useTableAnimationQueue(state.roomState);

  const enterLobbyAsGuest = (nickname: string) => {
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

  // 비밀번호를 확인하는 일반 입장 — createNewRoom(방 만들면 바로 입장)과
  // joinSelectedRoom(로비에서 골라서 입장)이 공유한다.
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
    maxPlayers: number,
  ) => {
    try {
      const { roomCode } = await createRoomApi(name, isPrivate, password, startingChips, bigBlind, maxPlayers);
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

  const joinSelectedRoom = async (password?: string) => {
    const detail = state.selectedRoomDetail;
    if (!detail) {
      return;
    }
    try {
      await joinAndEnter(detail.roomCode, password);
    } catch (e) {
      dispatch({ type: 'ERROR_OCCURRED', payload: (e as Error).message });
    }
  };

  // "코드로 입장" — roomCode를 직접 입력해서 들어온다. 비공개방이라도 비밀번호를 묻지 않는다.
  const joinByCode = async (roomCode: string) => {
    try {
      const { playerId } = await joinRoomByCodeApi(roomCode, state.guestNickname ?? undefined, state.authToken ?? undefined);
      await completeJoin(roomCode, playerId);
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
        joinSelectedRoom,
        joinByCode,
        startNewHand,
        toggleReady,
        toggleLeave,
        revealHand,
        decideReveal,
        sendAction,
        reconnect,
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
