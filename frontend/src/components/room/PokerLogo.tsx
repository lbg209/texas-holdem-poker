import { Card } from '../table/Card';

// 로그인/로비 화면 맨 위에 붙이는 간단한 로고. 새 그림을 그리는 대신 이미 있는 Card 컴포넌트를
// 살짝 기울여 재사용해서, 앱 다른 곳의 카드 그림과 시각적으로 통일되게 한다.
export function PokerLogo() {
  return (
    <div className="mb-6 flex items-center justify-center gap-2">
      <div className="h-20 w-16 -rotate-12 overflow-hidden sm:h-24 sm:w-20">
        <div className="origin-top-left scale-75">
          <Card card={{ suit: 'SPADE', rank: 'ACE', display: 'A♠' }} face="up" />
        </div>
      </div>
      <div className="flex flex-col items-center px-1">
        <h1 className="text-2xl font-bold tracking-wide text-slate-100 sm:text-3xl">
          TEXAS <span className="text-emerald-400">HOLD'EM</span>
        </h1>
      </div>
      <div className="h-20 w-16 rotate-12 overflow-hidden sm:h-24 sm:w-20">
        <div className="origin-top-left scale-75">
          <Card card={{ suit: 'HEART', rank: 'KING', display: 'K♥' }} face="up" />
        </div>
      </div>
    </div>
  );
}
