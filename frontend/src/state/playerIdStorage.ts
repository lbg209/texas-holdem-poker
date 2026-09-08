// 백엔드에 세션/재로그인 개념이 없어(참가할 때마다 새 Player가 생성됨), 새로고침 후에도
// 같은 사람으로 남아있으려면 playerId를 브라우저에 직접 보관해야 한다.
const STORAGE_KEY = 'poker.playerId';

export function getStoredPlayerId(): string | null {
  return localStorage.getItem(STORAGE_KEY);
}

export function storePlayerId(playerId: string): void {
  localStorage.setItem(STORAGE_KEY, playerId);
}

export function clearStoredPlayerId(): void {
  localStorage.removeItem(STORAGE_KEY);
}
