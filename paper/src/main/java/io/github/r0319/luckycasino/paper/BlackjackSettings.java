package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.blackjack.Rules;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * config.yml の blackjack 節。
 *
 * @param bettingSeconds 最初の人が賭けてから配り始めるまで
 * @param turnSeconds    1 回の行動の制限時間
 * @param buyIns         席に着くときに選べるチップの枚数
 */
record BlackjackSettings(Rules rules, int bettingSeconds, int turnSeconds, List<Long> buyIns) {

    static BlackjackSettings load(ConfigurationSection section) {
        Rules rules = new Rules(
                Math.max(1, section.getInt("decks", 6)),
                0.75,
                section.getBoolean("dealer-hits-soft-17", false),
                section.getBoolean("double-after-split", true),
                Math.max(1, section.getLong("min-bet", 10)),
                Math.max(1, section.getLong("max-bet", 1000)));
        List<Long> buyIns = section.getLongList("buy-ins").stream().filter(n -> n > 0).sorted().toList();
        return new BlackjackSettings(rules,
                Math.max(3, section.getInt("betting-seconds", 15)),
                Math.max(5, section.getInt("turn-seconds", 20)),
                buyIns.isEmpty() ? List.of(100L, 500L, 1000L, 5000L) : buyIns);
    }
}
