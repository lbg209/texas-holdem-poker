import { RoomProvider, useRoom } from './state/RoomContext';
import { JoinForm } from './components/room/JoinForm';
import { TableHeader } from './components/table/TableHeader';
import { PokerTable } from './components/table/PokerTable';
import { ActionBar } from './components/table/ActionBar';
import { ShowdownDecisionPanel } from './components/table/ShowdownDecisionPanel';

function RoomGate() {
  const { state } = useRoom();

  if (state.isVerifying) {
    return <p className="mt-24 text-center text-slate-400">확인 중...</p>;
  }

  if (!state.myPlayerId) {
    return <JoinForm />;
  }

  return (
    <>
      <TableHeader />
      {state.displayState && (
        <>
          <PokerTable
            displayState={state.displayState}
            activeVisualEvent={state.activeVisualEvent}
            dealProgress={state.dealProgress}
            myPlayerId={state.myPlayerId}
          />
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
  );
}

function App() {
  return (
    <RoomProvider>
      <div className="min-h-svh bg-slate-900 p-6 text-slate-100">
        <RoomGate />
      </div>
    </RoomProvider>
  );
}

export default App;
