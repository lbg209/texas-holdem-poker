// playerIdStorage/roomCodeStorage와 비슷한 역할이지만 일부러 localStorage가 아니라
// sessionStorage를 쓴다 — roomCode/playerId는 탭이 여러 개여도 "같은 브라우저의 같은 사람"이라고
// 가정해도 되지만, 닉네임은 같은 브라우저에서 탭마다 다른 게스트로 테스트하는 경우(A/B 두 게스트를
// 같은 브라우저로 테스트)가 흔해서 localStorage로 공유하면 서로 덮어써버린다. sessionStorage는
// 탭마다 독립적으로 유지되면서도 새로고침에는 그대로 살아남는다(탭을 닫으면 사라짐 — 게스트
// 정체성은 원래 일회성이라는 성격과도 맞는다).
//
// 게스트 닉네임은 서버에 전혀 저장되지 않고(로그인 계정과 달리 Player.nickname은 그때그때 요청에
// 실어 보내는 값) 오직 이 저장소에만 있다 — 새로고침 후 세션이 복원되면(roomCode+playerId로 이미
// 앉은 좌석을 되찾음) 당장은 닉네임이 필요 없지만, 그 뒤 방을 나가서 다른 방에 새로 입장하려 할
// 때(claimSeat) 다시 필요해진다. 이걸 복원해두지 않으면 새로고침 이후엔 guestNickname이 계속
// null로 남아 "닉네임은 비어 있을 수 없습니다" 에러로 어떤 방에도 새로 입장할 수 없게 된다
// (실사용 중 발견된 버그).
const STORAGE_KEY = 'poker.guestNickname';

export function getStoredGuestNickname(): string | null {
  return sessionStorage.getItem(STORAGE_KEY);
}

export function storeGuestNickname(nickname: string): void {
  sessionStorage.setItem(STORAGE_KEY, nickname);
}

// 명시적 "로그아웃"에서만 호출한다 — 방 나가기/강퇴/GAME OVER 리셋 등 다른 경로에서는 절대 지우면
// 안 된다(그게 바로 이번에 고친 버그의 원인이었다).
export function clearStoredGuestNickname(): void {
  sessionStorage.removeItem(STORAGE_KEY);
}
