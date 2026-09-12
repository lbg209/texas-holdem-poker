package com.lbg0146.backend.game;

import com.lbg0146.backend.player.Player;
import com.lbg0146.backend.room.Pot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PotCalculatorTest {

    @Test
    void 기여금이_모두_같으면_팟이_하나다() {
        Player a = new Player("A", "A", 10_000);
        Player b = new Player("B", "B", 10_000);
        a.commitChips(500);
        b.commitChips(500);

        List<Pot> pots = PotCalculator.calculate(List.of(a, b));

        assertEquals(1, pots.size());
        assertEquals(1000, pots.get(0).amount());
        assertEquals(Set.of("A", "B"), pots.get(0).eligiblePlayerIds());
    }

    @Test
    void 폴드한_플레이어의_기여금은_팟에_포함되지만_수령_자격은_없다() {
        Player a = new Player("A", "A", 10_000);
        Player b = new Player("B", "B", 10_000);
        Player c = new Player("C", "C", 700);
        Player d = new Player("D", "D", 10_000);

        a.commitChips(1700);
        b.commitChips(500);
        b.fold();
        c.commitChips(700); // 칩 전부 소진 -> 자동 ALL_IN
        d.commitChips(1700);

        List<Pot> pots = PotCalculator.calculate(List.of(a, b, c, d));

        int total = pots.stream().mapToInt(Pot::amount).sum();
        assertEquals(4600, total, "실제 낸 돈 총합과 팟 총액이 같아야 한다");

        boolean anyPotIncludesB = pots.stream()
                .anyMatch(pot -> pot.eligiblePlayerIds().contains("B"));
        assertTrue(!anyPotIncludesB, "폴드한 B는 어떤 팟도 가져갈 자격이 없다");

        // C는 700까지만 기여했으므로 그 이상 구간(사이드팟)에는 자격이 없다
        int amountEligibleForC = pots.stream()
                .filter(pot -> pot.eligiblePlayerIds().contains("C"))
                .mapToInt(Pot::amount)
                .sum();
        assertEquals(2600, amountEligibleForC);

        // A, D만 참가 가능한 700 초과분(사이드팟)은 2000이어야 한다
        int aOnlyWithD = pots.stream()
                .filter(pot -> pot.eligiblePlayerIds().equals(Set.of("A", "D")))
                .mapToInt(Pot::amount)
                .sum();
        assertEquals(2000, aOnlyWithD);
    }

    // 실사용 중 발견된 버그의 회귀 테스트: 앤티(deadMoney)를 Player.totalHandContribution에
    // 섞어서 반영했더니, 실제로는 두 사람이 똑같이 기여했는데도(사이드팟이 나뉠 이유가 없는데도)
    // 앤티를 낸 사람만 가져가는 가짜 사이드팟이 하나 더 생겨버렸다. deadMoney를 별도 파라미터로
    // 분리해서 항상 메인팟(eligiblePlayerIds가 가장 넓은 팟)에만 통째로 더하도록 고쳤다.
    @Test
    void 데드머니는_기여금_차이를_만들지_않고_메인팟에_통째로_더해진다() {
        Player a = new Player("A", "A", 10_000);
        Player b = new Player("B", "B", 10_000);
        a.commitChips(200);
        b.commitChips(200);

        List<Pot> pots = PotCalculator.calculate(List.of(a, b), 200); // 200 = 앤티라고 가정

        assertEquals(1, pots.size(), "기여금이 같으므로 사이드팟 없이 팟이 하나여야 한다");
        assertEquals(600, pots.get(0).amount(), "블라인드(200+200) + 데드머니(200)");
        assertEquals(Set.of("A", "B"), pots.get(0).eligiblePlayerIds());
    }

    @Test
    void 기여자가_없으면_데드머니가_있어도_팟이_생기지_않는다() {
        Player a = new Player("A", "A", 10_000);

        List<Pot> pots = PotCalculator.calculate(List.of(a), 200);

        assertEquals(0, pots.size());
    }
}
