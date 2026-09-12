import { useEffect } from 'react';
import { RoomProvider, useRoom } from './state/RoomContext';
import { AuthScreen } from './components/room/AuthScreen';
import { LobbyScreen } from './components/room/LobbyScreen';
import { TableHeader } from './components/table/TableHeader';
import { PokerTable } from './components/table/PokerTable';
import { ActionBar } from './components/table/ActionBar';
import { ShowdownDecisionPanel } from './components/table/ShowdownDecisionPanel';
import { RoomInfoToggle } from './components/table/RoomInfoToggle';
import { HandHistoryToggle } from './components/table/HandHistoryToggle';
import { BlindStructureToggle } from './components/table/BlindStructureToggle';
import { HandRankToggle } from './components/table/HandRankToggle';
import { unlockAudio } from './lib/sounds';

function RoomGate() {
  const { state } = useRoom();

  if (state.isVerifying) {
    return <p className="mt-24 text-center text-slate-400">확인 중...</p>;
  }

  if (state.screen === 'auth') {
    return <AuthScreen />;
  }

  if (state.screen === 'lobby') {
    return <LobbyScreen />;
  }

  return (
    <>
      {/* 좌측 상단 토글들을 세로로 쌓되, 각자 fixed로 고정하지 않고 이 공통 컨테이너 안에서
          일반적인 문서 흐름(flex-col)으로 배치한다 — 위 토글의 패널이 열려 높이가 늘어나면
          아래 토글이 자연스럽게 밀려 내려가서, 가로 배치처럼 버튼끼리 멀어지지도 않고
          세로로 겹쳐서 가려지지도 않는다. */}
      <div className="fixed left-4 top-4 z-20 flex flex-col items-start gap-2">
        <RoomInfoToggle />
        <HandRankToggle />
        <HandHistoryToggle />
        <BlindStructureToggle />
      </div>
      <TableHeader />
      {state.displayState && (
        <>
          <PokerTable
            displayState={state.displayState}
            activeVisualEvent={state.activeVisualEvent}
            dealProgress={state.dealProgress}
            myPlayerId={state.myPlayerId}
          />
          {/* 아직 좌석을 고르지 않은 관전자에게는 액션/쇼다운 결정 패널이 의미가 없으니 숨긴다. */}
          {state.myPlayerId && (
            <>
              <div className="fixed bottom-4 right-4 z-20">
                <ActionBar />
              </div>
              {/* 카드 공개/머크 결정은 베팅 버튼(화면 아래쪽)과 접근성이 겹치지 않도록 테이블 오른쪽
                  중간에 별도로 고정한다. */}
              <div className="fixed right-4 top-1/2 z-20 -translate-y-1/2">
                <ShowdownDecisionPanel />
              </div>
            </>
          )}
        </>
      )}
    </>
  );
}

function App() {
  // 브라우저(특히 iOS/Safari)는 사용자 제스처 없이 첫 오디오 재생을 막는다 — 페이지 어디든 처음
  // 클릭하는 순간 오디오를 한 번 "깨워둔다"(무음 재생 후 즉시 정지)  로그인/게스트 화면부터
  // 적용되도록 최상위에서 한 번만 등록한다.
  useEffect(() => {
    const handler = () => unlockAudio();
    window.addEventListener('pointerdown', handler, { once: true });
    return () => window.removeEventListener('pointerdown', handler);
  }, []);

  return (
    <RoomProvider>
      <div className="min-h-svh bg-slate-900 p-6 text-slate-100">
        <RoomGate />
      </div>
    </RoomProvider>
  );
}

export default App;
