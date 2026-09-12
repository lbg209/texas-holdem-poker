// 효과음 재생 — 라이브러리 없이 매번 새 Audio 인스턴스를 만들어서 재생한다. 카드 딜링처럼 200ms
// 간격으로 빠르게 겹쳐 재생되는 경우도, 효과음 자체가 1초 안팎으로 짧아서 풀링 없이 그냥 겹쳐
// 재생해도 자연스럽게 들린다(실제 카드를 연속으로 딜링할 때 소리가 겹치는 것과 비슷).
export type SoundKey =
  | 'cardDeal'
  | 'cardFlip'
  | 'fold'
  | 'chipCall'
  | 'chipBlind'
  | 'chipBet'
  | 'chipRaise'
  | 'chipAllIn'
  | 'potWin'
  | 'timerTick'
  | 'myTurn'
  | 'gameWin';

const SOUND_FILES: Record<SoundKey, string> = {
  cardDeal: '/sounds/card-deal.mp3',
  cardFlip: '/sounds/card-flip.mp3',
  fold: '/sounds/fold.mp3',
  chipCall: '/sounds/chip-call.mp3',
  chipBlind: '/sounds/chip-blind.mp3',
  chipBet: '/sounds/chip-bet.mp3',
  chipRaise: '/sounds/chip-raise.mp3',
  chipAllIn: '/sounds/chip-allin.mp3',
  potWin: '/sounds/pot-win.mp3',
  timerTick: '/sounds/timer-tick.mp3',
  myTurn: '/sounds/my-turn.mp3',
  gameWin: '/sounds/game-win.mp3',
};

// 원본 파일이 용도보다 긴 경우(예: 26초짜리 "동전이 바닥에 쏟아지는" 원본, 7초짜리 타이머 틱 원본)
// 이 시간이 지나면 강제로 멈춘다. 없으면 원본 길이 그대로 끝까지 재생한다.
const MAX_DURATION_MS: Partial<Record<SoundKey, number>> = {
  fold: 900,
  potWin: 2200,
  timerTick: 5000,
  // 원본은 6초짜리 작은 손종(handbell) 한 번 울리는 소리 — 친 직후 짧은 여운까지만 쓰고
  // 나머지 긴 울림 꼬리는 자른다.
  chipAllIn: 1300,
  // 3초짜리 박수 소리 원본 그대로 — 안전망으로만 남겨둠.
  gameWin: 3000,
};

const VOLUME: Partial<Record<SoundKey, number>> = {
  timerTick: 0.6,
  myTurn: 0.8,
  // "살짝" 울리는 느낌을 원해서, 다른 효과음보다 작게.
  chipAllIn: 0.5,
  // 게임 최종 승리 축하음(박수) — 너무 큰 소리는 원치 않는다는 요청으로 적당히 낮춰서.
  gameWin: 0.55,
};

// 족보 콜아웃(영어 음성) 볼륨 — 브라우저 음성합성(SpeechSynthesisUtterance)은 위 VOLUME 맵과
// 별개 API라 여기서 따로 곱한다.
const HAND_RANK_ANNOUNCE_VOLUME = 0.8;

const MUTED_STORAGE_KEY = 'poker.soundMuted';
const VOLUME_STORAGE_KEY = 'poker.soundVolume';

export function isSoundMuted(): boolean {
  try {
    return localStorage.getItem(MUTED_STORAGE_KEY) === 'true';
  } catch {
    return false;
  }
}

export function setSoundMuted(muted: boolean): void {
  try {
    localStorage.setItem(MUTED_STORAGE_KEY, String(muted));
  } catch {
    // 저장 실패해도(프라이빗 모드 등) 이번 세션 동작 자체는 계속돼야 하므로 무시한다.
  }
}

// 0~1 사이의 마스터 볼륨. 음소거(isSoundMuted)와는 별개 개념이다 — 음소거는 "켜져 있냐"를,
// 볼륨은 "켜져 있을 때 얼마나 크냐"를 결정한다. 기본값 1(최대).
export function getSoundVolume(): number {
  try {
    const raw = localStorage.getItem(VOLUME_STORAGE_KEY);
    if (raw === null) {
      return 1;
    }
    const parsed = Number(raw);
    return Number.isFinite(parsed) ? Math.min(1, Math.max(0, parsed)) : 1;
  } catch {
    return 1;
  }
}

export function setSoundVolume(volume: number): void {
  try {
    localStorage.setItem(VOLUME_STORAGE_KEY, String(Math.min(1, Math.max(0, volume))));
  } catch {
    // 저장 실패해도 이번 세션 동작 자체는 계속돼야 하므로 무시한다.
  }
}

// iOS/Safari 등은 사용자 제스처(클릭) 없이 첫 오디오 재생을 막는다 — 로비 진입/레디 같은 첫 클릭
// 시점에 한 번 호출해서, 무음+즉시정지로 "오디오를 재생해도 되는 상태"를 미리 깨워둔다.
let unlocked = false;
export function unlockAudio(): void {
  if (unlocked) {
    return;
  }
  unlocked = true;
  try {
    const audio = new Audio(SOUND_FILES.cardDeal);
    audio.volume = 0;
    audio.play()?.then(
      () => audio.pause(),
      () => {
        // 재생이 막혀도(권한 정책 등) 조용히 무시 — 다음 실제 효과음 재생 때 다시 시도된다.
      },
    );
  } catch {
    // Audio 생성 자체가 실패하는 환경도 무시하고 넘어간다.
  }
}

export function playSound(key: SoundKey): void {
  if (isSoundMuted()) {
    return;
  }
  try {
    const audio = new Audio(SOUND_FILES[key]);
    audio.volume = (VOLUME[key] ?? 1) * getSoundVolume();
    const cutoffMs = MAX_DURATION_MS[key];
    if (cutoffMs !== undefined) {
      const timer = window.setTimeout(() => audio.pause(), cutoffMs);
      audio.addEventListener('ended', () => window.clearTimeout(timer));
    }
    audio.play()?.catch(() => {
      // 자동재생 차단 등으로 실패해도 게임 진행에는 영향 없으니 조용히 무시한다.
    });
  } catch {
    // Audio 생성 자체가 실패하는 환경도 무시하고 넘어간다.
  }
}

// 쇼다운에서 이긴 족보(스트레이트 이상 — 그 아래는 너무 자주 나와서 굳이 안 부름)를 영어로 읽어준다.
// 미리 녹음된 음성 파일 대신 브라우저 내장 음성합성(Web Speech API)을 쓴다 — 별도 파일/라이선스가
// 필요 없고, 어차피 "Full House" 같은 정해진 짧은 단어라 합성 품질 문제도 거의 없다. 지원 안 하는
// 브라우저(구형 등)에서는 조용히 무시된다.
export function announceHandRank(text: string): void {
  if (isSoundMuted()) {
    return;
  }
  if (typeof window === 'undefined' || !('speechSynthesis' in window)) {
    return;
  }
  try {
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = 'en-US';
    utterance.volume = HAND_RANK_ANNOUNCE_VOLUME * getSoundVolume();
    window.speechSynthesis.speak(utterance);
  } catch {
    // 음성합성 자체가 실패하는 환경도 무시하고 넘어간다.
  }
}
