import { useCallback, useEffect, useRef, useState } from 'react';
import type { PlayerActionType, RoomStateResponse } from '../types/room';

export type ServerMessage =
  | { type: 'STATE'; state: RoomStateResponse }
  | { type: 'ERROR'; message: string };

export type ConnectionStatus = 'idle' | 'connecting' | 'open' | 'closed' | 'reconnecting';

const RECONNECT_DELAY_MS = 2000;

function buildWsUrl(playerId: string): string {
  const base = (import.meta.env.VITE_API_BASE_URL as string).replace(/^http/, 'ws');
  return `${base}/ws?playerId=${encodeURIComponent(playerId)}`;
}

// myPlayerId가 확정된 시점에만 연결하고, 끊기면 2초 후 자동 재연결을 1회만 시도한다.
// 그마저 실패하면 자동 재시도를 멈추고 reconnect()를 수동으로 호출해야 한다.
export function useRoomSocket(playerId: string | null, onMessage: (msg: ServerMessage) => void) {
  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>('idle');
  const socketRef = useRef<WebSocket | null>(null);
  const reconnectTimerRef = useRef<number | null>(null);
  const connectRef = useRef<(() => void) | null>(null);

  // onMessage identity가 매 렌더 바뀌어도 연결을 만드는 effect가 재실행되지 않도록,
  // 항상 최신 콜백만 ref에 담아두고 effect의 의존성 배열에는 playerId만 둔다.
  const onMessageRef = useRef(onMessage);
  useEffect(() => {
    onMessageRef.current = onMessage;
  }, [onMessage]);

  useEffect(() => {
    if (!playerId) {
      connectRef.current = null;
      return;
    }
    // 아래 중첩 함수(connect)에서는 TypeScript가 위 가드로 인한 narrowing을 유지하지 못하므로,
    // 이미 string으로 좁혀진 값을 별도 const에 담아 사용한다.
    const currentPlayerId = playerId;

    let cancelled = false;
    let hasRetried = false;

    const clearReconnectTimer = () => {
      if (reconnectTimerRef.current !== null) {
        window.clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }
    };

    function connect(isManualRetry: boolean) {
      if (cancelled) {
        return;
      }
      clearReconnectTimer();
      setConnectionStatus((prev) => (prev === 'idle' || prev === 'connecting' ? 'connecting' : 'reconnecting'));

      const socket = new WebSocket(buildWsUrl(currentPlayerId));
      socketRef.current = socket;

      socket.onopen = () => {
        if (cancelled) return;
        hasRetried = false;
        setConnectionStatus('open');
      };

      socket.onmessage = (event) => {
        if (cancelled) return;
        onMessageRef.current(JSON.parse(event.data) as ServerMessage);
      };

      socket.onclose = () => {
        socketRef.current = null;
        if (cancelled) return;
        setConnectionStatus('closed');
        // 자동 재연결은 끊긴 뒤 첫 시도에서만 1회 수행한다. 수동 재연결(reconnect())이
        // 실패한 경우까지 자동으로 다시 재시도하지는 않는다(무한 루프 방지).
        if (!hasRetried && !isManualRetry) {
          hasRetried = true;
          reconnectTimerRef.current = window.setTimeout(() => connect(false), RECONNECT_DELAY_MS);
        }
      };
    }

    connectRef.current = () => connect(true);
    connect(false);

    return () => {
      cancelled = true;
      clearReconnectTimer();
      socketRef.current?.close();
      socketRef.current = null;
      connectRef.current = null;
    };
  }, [playerId]);

  const reconnect = useCallback(() => {
    connectRef.current?.();
  }, []);

  const sendAction = useCallback((action: PlayerActionType, amount: number) => {
    const socket = socketRef.current;
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ type: 'ACTION', action, amount }));
    }
  }, []);

  return { connectionStatus, sendAction, reconnect };
}
