package io.github.r0319.luckycasino.paper;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * config.yml の poker 節。
 *
 * @param smallBlind   スモールブラインド
 * @param bigBlind     ビッグブラインド（席に着くのに最低限要るチップでもある）
 * @param turnSeconds  1 回の行動の制限時間
 * @param startSeconds 2 人そろってから（前の勝負が終わってから）次を配るまで
 * @param buyIns       席に着くときに選べるチップの枚数
 */
record PokerSettings(long smallBlind, long bigBlind, int turnSeconds, int startSeconds, List<Long> buyIns) {

    static PokerSettings load(ConfigurationSection section) {
        long sb = Math.max(1, section.getLong("small-blind", 5));
        long bb = Math.max(sb, section.getLong("big-blind", 10));
        List<Long> buyIns = section.getLongList("buy-ins").stream().filter(n -> n > 0).sorted().toList();
        return new PokerSettings(sb, bb,
                Math.max(5, section.getInt("turn-seconds", 25)),
                Math.max(2, section.getInt("start-seconds", 5)),
                buyIns.isEmpty() ? List.of(200L, 500L, 1000L, 2000L) : buyIns);
    }
}
