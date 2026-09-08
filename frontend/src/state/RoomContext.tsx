import { createContext, useCallback, useContext, useEffect, useReducer } from 'react';
import type { ReactNode } from 'react';
import { getRoomState, joinRoom, startHand } from '../api/roomApi';
import { clearStoredPlayerId, getStoredPlayerId, storePlayerId } from './playerIdStorage';
import { useRoomSocket } from '../ws/useRoomSocket';
import type { ConnectionStatus, ServerMessage } from '../ws/useRoomSocket';
import type { PlayerActionType, RoomStateResponse } from '../types/room';

const ERROR_AUTO_DISMISS_MS = 6000;

interface RoomState {
  roomState: RoomStateResponse | null;
  myPlayerId: string | null;
  error: string | null;
  // 저장된 playerId를 GET /api/room으로 검증하는 동안(true) JoinForm이 잠깐 보이지 않게 한다.
  isVerifying: boolean;
}

type Action =
  | { type: 'PLAYER_JOINED'; payload: { playerId: string } }
  | { type: 'ROOM_STATE_RECEIVED'; payload: RoomStateResponse }
  | { type: 'ERROR_OCCURRED'; payload: string }
  | { type: 'CLEAR_ERROR' }
  | { type: 'RESET_PLAYER' };

function reducer(state: RoomState, action: Action): RoomState {
  switch (action.type) {
    case 'PLAYER_JOINED':
      return { ...state, myPlayerId: action.payload.playerId, error: null };
    case 'ROOM_STATE_RECEIVED':
      return { ...state, roomState: action.payload, isVerifying: false, error: null };
    case 'ERROR_OCCURRED':
      return { ...state, error: action.payload, isVerifying: false };
    case 'CLEAR_ERROR':
      return { ...state, error: null };
    case 'RESET_PLAYER':
      return { ...state, myPlayerId: null, isVerifying: false };
    default:
      return state;
  }
}

interface RoomContextValue {
  state: RoomState & { connectionStatus: ConnectionStatus };
  join: (nickname: string) => Promise<void>;
  refreshState: () => Promise<void>;
  startNewHand: () => Promise<void>;
  sendAction: (action: PlayerActionType, amount: number) => void;
  reconnect: () => void;
  clearError: () => void;
}

const RoomContext = createContext<RoomContextValue | null>(null);

export function RoomProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(reducer, {
    roomState: null,
    myPlayerId: null,
    error: null,
    isVerifying: Boolean(getStoredPlayerId()),
  });

  // 마운트 시 저장된 playerId를 "그대로 신뢰"하지 않고, GET /api/room으로 서버에 실제
  // 존재하는지 검증한 뒤에만 PLAYER_JOINED를 반영한다. 없으면 저장값을 지우고 참가 화면으로 돌린다.
  useEffect(() => {
    const stored = getStoredPlayerId();
    if (!stored) {
      return;
    }

    getRoomState(stored)
      .then((roomState) => {
        const stillExists = roomState.players.some((p) => p.id === stored);
        if (stillExists) {
          dispatch({ type: 'PLAYER_JOINED', payload: { playerId: stored } });
          dispatch({ type: 'ROOM_STATE_RECEIVED', payload: roomState });
        } else {
          clearStoredPlayerId();
          dispatch({ type: 'RESET_PLAYER' });
        }
      })
      .catch((e: Error) => dispatch({ type: 'ERROR_OCCURRED', payload: e.message }));
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

  // WebSocket이 STATE/ERROR를 수신할 때마다 3단계에서 만든 액션을 그대로 재사용한다.
  const handleWsMessage = useCallback((msg: ServerMessage) => {
    if (msg.type === 'STATE') {
      dispatch({ type: 'ROOM_STATE_RECEIVED', payload: msg.state });
    } else {
      dispatch({ type: 'ERROR_OCCURRED', payload: msg.message });
    }
  }, []);

  const { connectionStatus, sendAction, reconnect } = useRoomSocket(state.myPlayerId, handleWsMessage);

  const join = async (nickname: string) => {
    try {
      const { playerId } = await joinRoom(nickname);
      storePlayerId(playerId);
      dispatch({ type: 'PLAYER_JOINED', payload: { playerId } });
      const roomState = await getRoomState(playerId);
      dispatch({ type: 'ROOM_STATE_RECEIVED', payload: roomState });
    } catch (e) {
      dispatch({ type: 'ERROR_OCCURRED', payload: (e as Error).message });
    }
  };

  const refreshState = async () => {
    try {
      const roomState = await getRoomState(state.myPlayerId);
      dispatch({ type: 'ROOM_STATE_RECEIVED', payload: roomState });
    } catch (e) {
      dispatch({ type: 'ERROR_OCCURRED', payload: (e as Error).message });
    }
  };

  const startNewHand = async () => {
    try {
      const roomState = await startHand(state.myPlayerId);
      dispatch({ type: 'ROOM_STATE_RECEIVED', payload: roomState });
    } catch (e) {
      dispatch({ type: 'ERROR_OCCURRED', payload: (e as Error).message });
    }
  };

  const clearError = () => dispatch({ type: 'CLEAR_ERROR' });

  return (
    <RoomContext.Provider
      value={{ state: { ...state, connectionStatus }, join, refreshState, startNewHand, sendAction, reconnect, clearError }}
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
