package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.slot.Line;
import io.github.r0319.luckycasino.slot.Paytable;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * config.yml の slot 節。
 *
 * @param lineBets 1 ラインに賭けられる額の選択肢（小さい順、最大 5 つ）。1 回の賭け金はこれ × 選んだラインの本数
 * @param buyIns   席に着くときに選べるチップの枚数
 * @param paytable 配当表
 */
record SlotSettings(List<Long> lineBets, List<Long> buyIns, Paytable paytable) {

    /** 1 ラインの賭け金の上限（5 ライン × 300 倍でも桁があふれないように）。 */
    static final long MAX_LINE_BET = 1_000_000;

    static SlotSettings load(ConfigurationSection section) {
        List<Long> lineBets;
        if (section.contains("line-bets")) {
            lineBets = section.getLongList("line-bets").stream().filter(n -> n > 0 && n <= MAX_LINE_BET).distinct().sorted().limit(5).toList();
        } else {
            // 0.3.4 までの bets（1 回の賭け金を 5 ラインに等分）は 1 ラインの額に直して読む
            lineBets = section.getLongList("bets").stream().filter(n -> n > 0 && n % Line.COUNT == 0)
                    .map(n -> n / Line.COUNT).filter(n -> n <= MAX_LINE_BET).distinct().sorted().limit(5).toList();
        }
        List<Long> buyIns = section.getLongList("buy-ins").stream().filter(n -> n > 0).sorted().toList();
        Paytable d = Paytable.DEFAULT;
        ConfigurationSection p = section.getConfigurationSection("paytable");
        Paytable paytable = p == null ? d : new Paytable(
                Math.max(0, p.getLong("sevens", d.sevens())), Math.max(0, p.getLong("bars", d.bars())),
                Math.max(0, p.getLong("bells", d.bells())), Math.max(0, p.getLong("melons", d.melons())),
                Math.max(0, p.getLong("lemons", d.lemons())), Math.max(0, p.getLong("cherry1", d.cherry1())),
                Math.max(0, p.getLong("cherry2", d.cherry2())), Math.max(0, p.getLong("cherry3", d.cherry3())));
        return new SlotSettings(lineBets.isEmpty() ? List.of(2L, 10L, 20L) : lineBets,
                buyIns.isEmpty() ? List.of(100L, 500L, 1000L, 5000L) : buyIns, paytable);
    }

    /** 一番小さい 1 ラインの賭け金（1 本だけ賭けるときの 1 回の最低額でもある）。 */
    long minLineBet() {
        return lineBets.getFirst();
    }
}
