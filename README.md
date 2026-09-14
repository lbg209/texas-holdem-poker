# Texas Hold'em Poker

Spring Boot(REST + WebSocket) 백엔드와 React 프론트엔드로 만든 실시간 멀티플레이어 텍사스 홀덤 포커 게임입니다. 서버가 카드 셔플부터 베팅, 사이드팟 계산, 쇼다운까지 모든 게임 규칙을 판단하는 서버 권위(server-authoritative) 구조로 만들었습니다.

## 🔗 배포 링크

**[https://texas-holdem-poker-nine.vercel.app](https://texas-holdem-poker-nine.vercel.app)**

게스트 닉네임만으로 바로 입장해서 플레이해볼 수 있습니다. 무료 서버라 잠깐 사용이 없으면 슬립되는데, 처음 접속 시 서버가 깨어나느라 최대 1분 정도 걸릴 수 있습니다.

## 기술 스택

![Java](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?logo=springboot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?logo=mysql&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-6-3178C6?logo=typescript&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-8-646CFF?logo=vite&logoColor=white)
![TailwindCSS](https://img.shields.io/badge/Tailwind%20CSS-4-06B6D4?logo=tailwindcss&logoColor=white)
![WebSocket](https://img.shields.io/badge/WebSocket-native-010101?logo=websocket&logoColor=white)

## 주요 기능

- 로그인/게스트 인증, 로비(방 목록/생성/검색/코드로 입장), 멀티룸
- 고정 6인 좌석제(빈 좌석 클릭으로 입장/이동), 방장 배지 + 강퇴
- 카드 셔플/딜, 베팅 라운드, 사이드팟 계산, 족보 판정까지 서버가 전담하는 서버 권위 구조
- 턴 타이머 + 자동 폴드, 레디 시스템 + 자동 시작, 헤즈업 쇼다운 머크 결정
- 블라인드 상승 + 빅블라인드 앤티, 핸드 히스토리, GAME OVER 자동 리매치
- 카드/칩 애니메이션, 족보 등급별 쇼다운 하이라이트, 사운드 효과

REST는 참가/조회처럼 매번 요청하는 동작을, WebSocket은 베팅 액션·커뮤니티 카드 공개 같은 실시간 상태 동기화를 맡는 식으로 역할을 나눴고, 방(room)마다 독립된 게임 인스턴스가 떠서 여러 방이 동시에 운영됩니다.

## 더 자세한 내용

- [backend/README.md](backend/README.md) — 게임 로직, 동시성 처리, REST/WebSocket 설계, 테스트 현황 등
- [frontend/README.md](frontend/README.md) — 화면 구성, 상태 관리, 애니메이션, 기술적으로 흥미로웠던 버그들 등

## Claude Code와 함께 개발

이 프로젝트는 [Claude Code](https://claude.com/claude-code)와 페어 프로그래밍 방식으로 개발했습니다. 기능마다 설계안을 먼저 검토·승인받은 뒤 구현하고, 구현 후에는 테스트와 실제 플레이로 검증하는 과정을 반복했습니다.
