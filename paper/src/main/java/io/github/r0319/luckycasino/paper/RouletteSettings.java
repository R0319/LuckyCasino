package io.github.r0319.luckycasino.paper;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * config.yml の roulette 節。
 *
 * @param minBet         1 か所に置く最低額
 * @param maxBet         1 か所に置ける最高額
 * @param maxTotal       1 回に 1 人が賭けられる合計
 * @param bettingSeconds 最初の人が賭けてから締め切るまで
 */
record RouletteSettings(long minBet, long maxBet, long maxTotal, int bettingSeconds, List<Long> buyIns) {

    static RouletteSettings load(ConfigurationSection section) {
        long min = Math.max(1, section.getLong("min-bet", 10));
        long max = Math.max(min, section.getLong("max-bet", 500));
        List<Long> buyIns = section.getLongList("buy-ins").stream().filter(n -> n > 0).sorted().toList();
        return new RouletteSettings(min, max, Math.max(max, section.getLong("max-total", 2000)),
                Math.max(5, section.getInt("betting-seconds", 20)),
                buyIns.isEmpty() ? List.of(100L, 500L, 1000L, 5000L) : buyIns);
    }
}
