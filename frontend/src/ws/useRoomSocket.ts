import { useCallback, useEffect, useRef, useState } from 'react';
import type { PlayerActionType, RoomStateResponse } from '../types/room';

export type ServerMessage =
  | { type: 'STATE'; state: RoomStateResponse }
  | { type: 'ERROR'; message: string };

export type ConnectionStatus = 'idle' | 'connecting' | 'open' | 'closed' | 'reconnecting';

const RECONNECT_DELAY_MS = 2000;

function buildWsUrl(roomCode: string, playerId: string | null): string {
  const base = (import.meta.env.VITE_API_BASE_URL as string).replace(/^http/, 'ws');
  const query = playerId ? `?roomCode=${encodeURIComponent(roomCode)}&playerId=${encodeURIComponent(playerId)}` : `?roomCode=${encodeURIComponent(roomCode)}`;
  return `${base}/ws${query}`;
}

// roomCode가 없으면(로비에 있는 동안) 아예 연결하지 않는다 — 로비 방 목록은 실시간 갱신 대상이
// 아니라 REST로만 조회하므로, 방에 실제로 들어가 있을 때만 WebSocket이 필요하다. roomCode/playerId가
// 바뀔 때마다(참가/나가기 포함) 다시 연결하고, 끊기면 2초 후 자동 재연결을 1회만 시도한다.
export function useRoomSocket(
  roomCode: string | null,
  playerId: string | null,
  onMessage: (msg: ServerMessage) => void,
) {
  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>('idle');
  const socketRef = useRef<WebSocket | null>(null);
  const reconnectTimerRef = useRef<number | null>(null);
  const connectRef = useRef<(() => void) | null>(null);

  // onMessage identity가 매 렌더 바뀌어도 연결을 만드는 effect가 재실행되지 않도록,
  // 항상 최신 콜백만 ref에 담아두고 effect의 의존성 배열에는 roomCode/playerId만 둔다.
  const onMessageRef = useRef(onMessage);
  useEffect(() => {
    onMessageRef.current = onMessage;
  }, [onMessage]);

  useEffect(() => {
    if (!roomCode) {
      setConnectionStatus('idle');
      return;
    }

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

      const socket = new WebSocket(buildWsUrl(roomCode as string, playerId));
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
        // socketRef가 "지금 닫히는 이 소켓"을 가리킬 때만 비운다. roomCode는 그대로 두고 playerId만
        // null -> 실제값으로 바뀌는 경우(관전하다가 좌석을 골라 입장하는 흐름) 새 소켓이 이미
        // socketRef에 들어간 뒤에 옛 소켓의 onclose가 늦게 발동할 수 있는데, 조건 없이 비우면
        // 방금 연결된 정상 소켓 참조까지 지워버려서 sendAction이 이후 계속 조용히 실패하게 된다.
        if (socketRef.current === socket) {
          socketRef.current = null;
        }
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
  }, [roomCode, playerId]);

  const reconnect = useCallback(() => {
    connectRef.current?.();
  }, []);

  // 소켓이 준비되지 않은 상태(재연결 중 등)에서 호출되면 이전엔 조용히 무시됐는데, 그러면
  // 사용자 입장에서는 "버튼이 안 눌리는" 것처럼만 보이고 원인을 알 방법이 없었다. 대신 전송
  // 성공 여부를 반환해서, 호출하는 쪽(RoomContext)이 실패 시 에러를 보여주고 재연결을 시도할 수
  // 있게 한다.
  const sendRaw = useCallback((payload: object): boolean => {
    const socket = socketRef.current;
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify(payload));
      return true;
    }
    return false;
  }, []);

  const sendAction = useCallback(
    (action: PlayerActionType, amount: number): boolean => sendRaw({ type: 'ACTION', action, amount }),
    [sendRaw],
  );

  // 헤즈업 쇼다운 공개/머크 결정 패널이 실제로 화면에 뜬 순간(카드 공개 연출이 다 끝난 뒤)에만
  // 호출된다 — 서버가 이 신호를 받은 시점부터 진짜 결정 제한 시간을 센다. 백엔드가 모든 클라이언트
  // 메시지를 같은 봉투(ActionMessage: type/action/amount)로 파싱하므로, action/amount를 생략하면
  // 파싱 자체가 실패해서 "메시지 형식이 올바르지 않습니다" 에러로 튕겨나간다 — 명시적으로 채워 보낸다.
  const sendHeadsUpRevealReady = useCallback(
    (): boolean => sendRaw({ type: 'HEADS_UP_REVEAL_READY', action: null, amount: 0 }),
    [sendRaw],
  );

  return { connectionStatus, sendAction, sendHeadsUpRevealReady, reconnect };
}
