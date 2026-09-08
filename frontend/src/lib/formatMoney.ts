// 절댓값이 1,000 이상이면 K 단위로 줄여서 표시한다 (10000 -> "10K", -1500 -> "-1.5K"). 1,000 미만은 그대로.
export function formatMoney(amount: number): string {
  if (Math.abs(amount) < 1000) {
    return amount.toLocaleString();
  }
  const k = Math.round((amount / 1000) * 10) / 10;
  return `${Number.isInteger(k) ? k.toFixed(0) : k.toFixed(1)}K`;
}
