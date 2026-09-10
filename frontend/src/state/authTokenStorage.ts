// 로그인 토큰을 브라우저에 보관한다. playerIdStorage와 별개다 — 이 토큰은 "다음에 방에 들어갈 때
// 로그인 상태로 들어가기" 용도이고, 이미 참가한 좌석으로 새로고침 후 재접속하는 건 playerId가 담당한다.
const STORAGE_KEY = 'poker.authToken';

export function getStoredAuthToken(): string | null {
  return localStorage.getItem(STORAGE_KEY);
}

export function storeAuthToken(token: string): void {
  localStorage.setItem(STORAGE_KEY, token);
}

export function clearStoredAuthToken(): void {
  localStorage.removeItem(STORAGE_KEY);
}
