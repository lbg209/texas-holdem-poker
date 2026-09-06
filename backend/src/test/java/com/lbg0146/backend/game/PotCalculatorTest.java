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
}
