package io.github.r0319.luckycasino.paper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 収支ランキングの並び・同じ順位・ゲーム別・プレースホルダー・保存。 */
class RankingTest {

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);
    private static final UUID D = new UUID(0, 4);

    private static Ranking.Entry entry(UUID uuid, String name, Object... gameNetPlays) {
        Map<GameType, Ranking.Totals> games = new EnumMap<>(GameType.class);
        for (int i = 0; i < gameNetPlays.length; i += 3) {
            games.put((GameType) gameNetPlays[i], new Ranking.Totals((long) gameNetPlays[i + 1], (long) gameNetPlays[i + 2]));
        }
        return new Ranking.Entry(uuid, name, games);
    }

    private static Ranking sample() {
        return new Ranking(List.of(
                entry(A, "Alice", GameType.BLACKJACK, 500L, 3L, GameType.SLOT, -200L, 10L),
                entry(B, "Bob", GameType.ROULETTE, 300L, 1L),
                entry(C, "Carol", GameType.SLOT, -50L, 2L),
                entry(D, "Dave", GameType.BLACKJACK, 300L, 2L)));
    }

    @Test
    void overallSumsGamesAndSortsByNet() {
        List<Ranking.Row> top = sample().top(null, 10);
        // 300 で並ぶ 3 人は遊んだ回数の少ない順
        assertEquals(List.of("Bob", "Dave", "Alice", "Carol"), top.stream().map(Ranking.Row::name).toList());
        Ranking.Row alice = top.get(2);
        assertEquals(300, alice.net());
        assertEquals(13, alice.plays());
    }

    @Test
    void sameNetSharesRank() {
        // Alice・Dave・Bob が 300。回数の少ない順に並ぶが順位は同じ
        List<Ranking.Row> top = sample().top(null, 10);
        assertEquals(List.of(1, 1, 1, 4), top.stream().map(Ranking.Row::rank).toList());
    }

    @Test
    void perGameRanking() {
        Ranking ranking = sample();
        assertEquals(List.of("Alice", "Dave"), ranking.top(GameType.BLACKJACK, 10).stream().map(Ranking.Row::name).toList());
        assertEquals(List.of("Carol", "Alice"), ranking.top(GameType.SLOT, 10).stream().map(Ranking.Row::name).toList());
        assertEquals(2, ranking.of(A, GameType.SLOT).rank());
        assertNull(ranking.of(B, GameType.SLOT));
        assertEquals(0, ranking.size(GameType.POKER));
        assertEquals(1, ranking.top(null, 1).size());
    }

    @Test
    void placeholders() {
        Ranking ranking = sample();
        assertEquals("Bob", ranking.placeholder(null, "top_1_name"));
        assertEquals("+300", ranking.placeholder(null, "top_1_net"));
        assertEquals("-50", ranking.placeholder(null, "top_4_net_raw"));
        assertEquals("---", ranking.placeholder(null, "top_5_name"));
        assertEquals("", ranking.placeholder(null, "top_5_net"));
        assertEquals("Carol", ranking.placeholder(null, "slot_top_1_name"));
        assertEquals("4", ranking.placeholder(C, "rank"));
        assertEquals("-50", ranking.placeholder(C, "net"));
        assertEquals("2", ranking.placeholder(C, "plays"));
        assertEquals("-", ranking.placeholder(C, "poker_rank"));
        assertEquals("0", ranking.placeholder(C, "poker_net"));
        assertEquals("+1,200", Ranking.signed(1200));
        assertNull(ranking.placeholder(null, "top_x_name"));
        assertNull(ranking.placeholder(C, "unknown"));
    }

    @Test
    void yamlStoreAddsAndReloads(@TempDir File dir) {
        File file = new File(dir, "stats.yml");
        Stats.Yaml stats = new Stats.Yaml(file, Logger.getLogger("test"));
        stats.add(A, "Alice", GameType.BLACKJACK, 100);
        stats.add(A, "Alice2", GameType.BLACKJACK, -30);
        stats.add(B, "Bob", GameType.SLOT, 50);
        Ranking.Row alice = stats.ranking().of(A, null);
        assertEquals(70, alice.net());
        assertEquals(2, alice.plays());
        assertEquals("Alice2", alice.name());

        Ranking reloaded = new Stats.Yaml(file, Logger.getLogger("test")).ranking();
        assertEquals(List.of("Alice2", "Bob"), reloaded.top(null, 10).stream().map(Ranking.Row::name).toList());
        assertEquals(50, reloaded.of(B, GameType.SLOT).net());
    }
}
