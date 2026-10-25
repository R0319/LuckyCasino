package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.slot.Paytable;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * config.yml の slot 節。
 *
 * @param bets     1 回に賭けられる額の選択肢（小さい順、最大 5 つ）
 * @param buyIns   席に着くときに選べるチップの枚数
 * @param paytable 配当表
 */
record SlotSettings(List<Long> bets, List<Long> buyIns, Paytable paytable) {

    static SlotSettings load(ConfigurationSection section) {
        List<Long> bets = section.getLongList("bets").stream().filter(n -> n > 0).distinct().sorted().limit(5).toList();
        List<Long> buyIns = section.getLongList("buy-ins").stream().filter(n -> n > 0).sorted().toList();
        Paytable d = Paytable.DEFAULT;
        ConfigurationSection p = section.getConfigurationSection("paytable");
        Paytable paytable = p == null ? d : new Paytable(
                Math.max(0, p.getLong("sevens", d.sevens())), Math.max(0, p.getLong("bars", d.bars())),
                Math.max(0, p.getLong("bells", d.bells())), Math.max(0, p.getLong("melons", d.melons())),
                Math.max(0, p.getLong("lemons", d.lemons())), Math.max(0, p.getLong("cherry1", d.cherry1())),
                Math.max(0, p.getLong("cherry2", d.cherry2())), Math.max(0, p.getLong("cherry3", d.cherry3())));
        return new SlotSettings(bets.isEmpty() ? List.of(10L, 50L, 100L) : bets,
                buyIns.isEmpty() ? List.of(100L, 500L, 1000L, 5000L) : buyIns, paytable);
    }

    long minBet() {
        return bets.getFirst();
    }
}
