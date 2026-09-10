// playerIdStorage와 짝을 이룬다 — 새로고침 후 "어느 방의 어느 좌석"이었는지 복원하려면 둘 다 필요하다.
const STORAGE_KEY = 'poker.roomCode';

export function getStoredRoomCode(): string | null {
  return localStorage.getItem(STORAGE_KEY);
}

export function storeRoomCode(roomCode: string): void {
  localStorage.setItem(STORAGE_KEY, roomCode);
}

export function clearStoredRoomCode(): void {
  localStorage.removeItem(STORAGE_KEY);
}
