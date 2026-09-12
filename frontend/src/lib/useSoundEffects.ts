import { useEffect, useRef } from 'react';
import type { PlayerActionType } from '../types/room';
import type { ActiveVisualEvent } from '../state/tableAnimation';
import { useCountdownSeconds } from './useCountdownSeconds';
import { announceHandRank, playSound } from './sounds';
import type { SoundKey } from './sounds';
import { getHandRankAnnouncement } from './handRank';

// 족보 콜아웃은 팟 이동 소리(potWin)와 겹치지 않도록 살짝 늦게 부른다.
const HAND_RANK_ANNOUNCE_DELAY_MS = 400;

const ACTION_SOUND: Partial<Record<PlayerActionType, SoundKey>> = {
  CALL: 'chipCall',
  BET: 'chipBet',
  RAISE: 'chipRaise',
  ALL_IN: 'chipAllIn',
  FOLD: 'fold',
  // CHECK는 일부러 소리를 안 넣는다 — 너무 자주 나와서 오히려 거슬린다는 판단.
};

// 내 차례가 급해질 때(URGENT_SECONDS_THRESHOLD 이하로 남았을 때) 틱 소리를 재생하는 기준 — ActionBar의
// 같은 상수와 맞춘 값이다.
const URGENT_SECONDS_THRESHOLD = 10;

// 커뮤니티 카드는 데이터(카드 값)가 스텝 시작 즉시 바뀌지만, 실제 화면(CommunityCards의 RevealCard)은
// 뒷면으로 잠깐 머물렀다가 flipDelayMs 뒤에야 뒤집히기 시작한다 — 소리도 그 타이밍에 맞춰 지연시켜야
// "카드가 뒤집히는 순간"과 소리가 맞아떨어진다. 플랍(빠름)과 턴/리버(긴장감 있게 느림)의 지연이 달라서
// isSlow로 구분한다. 이 두 값은 CommunityCards.tsx의 NORMAL_FLIP_DELAY_MS/SLOW_FLIP_DELAY_MS와
// 반드시 같이 맞춰야 한다 — 거기서 값을 바꾸면 여기도 같이 바꿔야 함.
const COMMUNITY_FLIP_SOUND_DELAY_MS = { normal: 150, slow: 500 };

// PokerTable에서 한 번만 호출한다 — activeVisualEvent(애니메이션 큐가 재생 중인 단일 이벤트)를
// 구독해서 카드 딜링/액션/칩 이동/팟 지급/쇼다운 공개 소리를 재생하고, 별도로 "내 차례가 됨" /
// "내 차례가 급해짐" 알림음도 관리한다.
export function useSoundEffects(
  activeVisualEvent: ActiveVisualEvent | null,
  myPlayerId: string | null,
  currentActorId: string | null,
  turnDeadlineAtMillis: number | null,
  winnerId: string | null,
): void {
  useEffect(() => {
    if (!activeVisualEvent) {
      return;
    }
    let delayedTimer: number | undefined;
    switch (activeVisualEvent.type) {
      case 'DEAL_CARD':
        playSound('cardDeal');
        break;
      case 'BLIND_FLOURISH':
        playSound('chipBlind');
        break;
      case 'POT_TO_WINNERS': {
        playSound('potWin');
        const { winningHandRank, winningHandBestFive } = activeVisualEvent;
        const announcement =
          winningHandRank && winningHandBestFive
            ? getHandRankAnnouncement(winningHandRank, winningHandBestFive)
            : null;
        if (announcement) {
          delayedTimer = window.setTimeout(() => announceHandRank(announcement), HAND_RANK_ANNOUNCE_DELAY_MS);
        }
        break;
      }
      case 'HOLE_CARD_REVEALED':
        playSound('cardFlip');
        break;
      case 'COMMUNITY_CARD_REVEALED': {
        const delay = activeVisualEvent.isSlow
          ? COMMUNITY_FLIP_SOUND_DELAY_MS.slow
          : COMMUNITY_FLIP_SOUND_DELAY_MS.normal;
        delayedTimer = window.setTimeout(() => playSound('cardFlip'), delay);
        break;
      }
      case 'ACTION_TAKEN': {
        const key = ACTION_SOUND[activeVisualEvent.action];
        if (key) {
          playSound(key);
        }
        break;
      }
      case 'CHIPS_TO_POT':
        // 베팅 시점(ACTION_TAKEN)에 이미 칩 소리를 재생했으므로, 팟으로 쓸려 들어가는 시점엔
        // 중복 재생하지 않는다.
        break;
    }
    return () => {
      if (delayedTimer !== undefined) {
        window.clearTimeout(delayedTimer);
      }
    };
  }, [activeVisualEvent]);

  // 내 차례가 된 순간(다른 사람 -> 나로 바뀐 순간) 딱 한 번 알림음을 재생한다.
  const prevActorRef = useRef<string | null>(null);
  useEffect(() => {
    if (myPlayerId && currentActorId === myPlayerId && prevActorRef.current !== currentActorId) {
      playSound('myTurn');
    }
    prevActorRef.current = currentActorId;
  }, [currentActorId, myPlayerId]);

  // 내 차례이고 시간이 얼마 안 남았을 때(URGENT_SECONDS_THRESHOLD 이하) 딱 한 번 틱 소리를 재생한다
  // — 매초 반복하면 시끄러우니, "급해졌다"는 걸 알리는 용도로 한 번만.
  const isMyTurn = myPlayerId !== null && currentActorId === myPlayerId;
  const secondsLeft = useCountdownSeconds(isMyTurn ? turnDeadlineAtMillis : null);
  const urgentFiredRef = useRef(false);
  useEffect(() => {
    if (secondsLeft === null) {
      urgentFiredRef.current = false;
      return;
    }
    if (secondsLeft <= URGENT_SECONDS_THRESHOLD && !urgentFiredRef.current) {
      urgentFiredRef.current = true;
      playSound('timerTick');
    }
  }, [secondsLeft]);

  // GAME OVER(상대 전원 파산, 생존자 1명) 확정 순간 딱 한 번 — winnerId가 null에서 값이 생기는
  // 전환에서만 재생한다(그 이후 계속 non-null로 유지되는 동안 반복 재생되지 않도록).
  const prevWinnerIdRef = useRef<string | null>(null);
  useEffect(() => {
    if (winnerId && !prevWinnerIdRef.current) {
      playSound('gameWin');
    }
    prevWinnerIdRef.current = winnerId;
  }, [winnerId]);
}
