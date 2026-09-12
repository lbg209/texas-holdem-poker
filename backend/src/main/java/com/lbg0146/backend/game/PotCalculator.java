package com.lbg0146.backend.game;

import com.lbg0146.backend.player.Player;
import com.lbg0146.backend.player.PlayerStatus;
import com.lbg0146.backend.room.Pot;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PotCalculator {

    private PotCalculator() {
    }

    public static List<Pot> calculate(List<Player> players) {
        return calculate(players, 0);
    }

    // 핸드 종료 시점에 각 플레이어의 totalHandContribution을 기준으로 메인팟/사이드팟을 계산한다.
    // 폴드한 플레이어의 기여금도 팟 금액에는 포함되지만, 그 플레이어는 어떤 팟도 가져갈 자격이 없다.
    // 올인한 플레이어는 자신이 기여한 금액 수준까지의 팟에서만 승리할 수 있다.
    // deadMoney(앤티 등, 특정 플레이어 소유가 아닌 돈)는 항상 폴드 안 한 전원이 경쟁하는 메인팟
    // (eligiblePlayerIds가 가장 넓은, 즉 목록의 첫 번째 팟)에 통째로 더한다 — 개별 플레이어의
    // totalHandContribution 수준과 무관하게 전원이 경쟁하는 돈이라, 다른 사이드팟 계산에 섞이면
    // 안 된다(섞이면 그 돈을 낸 사람만 가져가는 가짜 사이드팟이 생긴다).
    public static List<Pot> calculate(List<Player> players, int deadMoney) {
        List<Player> contributors = players.stream()
                .filter(p -> p.getTotalHandContribution() > 0)
                .toList();
        if (contributors.isEmpty()) {
            return List.of();
        }

        List<Integer> levels = contributors.stream()
                .map(Player::getTotalHandContribution)
                .distinct()
                .sorted()
                .toList();

        List<Pot> pots = new ArrayList<>();
        int previousLevel = 0;
        for (int level : levels) {
            int increment = level - previousLevel;
            List<Player> reachedThisLevel = contributors.stream()
                    .filter(p -> p.getTotalHandContribution() >= level)
                    .toList();

            Set<String> eligiblePlayerIds = reachedThisLevel.stream()
                    .filter(p -> p.getStatus() != PlayerStatus.FOLDED)
                    .map(Player::getId)
                    .collect(Collectors.toSet());

            if (!eligiblePlayerIds.isEmpty()) {
                pots.add(new Pot(increment * reachedThisLevel.size(), eligiblePlayerIds));
            }
            previousLevel = level;
        }

        if (deadMoney > 0 && !pots.isEmpty()) {
            Pot mainPot = pots.get(0);
            pots.set(0, new Pot(mainPot.amount() + deadMoney, mainPot.eligiblePlayerIds()));
        }
        return pots;
    }
}
