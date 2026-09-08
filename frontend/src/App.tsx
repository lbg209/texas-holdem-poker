import { RoomProvider, useRoom } from './state/RoomContext';
import { JoinForm } from './components/room/JoinForm';
import { TableHeader } from './components/table/TableHeader';
import { PokerTable } from './components/table/PokerTable';
import { ActionBar } from './components/table/ActionBar';

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
