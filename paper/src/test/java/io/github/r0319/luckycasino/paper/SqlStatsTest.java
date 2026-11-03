package io.github.r0319.luckycasino.paper;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** DB 保存（H2 を MySQL の書き方で動かす）。2 台のサーバーが同じ DB に書く場面も。 */
class SqlStatsTest {

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final Logger LOG = Logger.getLogger("test");

    private static String url(String name) {
        return "jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
    }

    /** 専用のスレッドの仕事が終わるまで待つ（close は書き終えてから戻る）。 */
    private static SqlStats open(String name) throws SQLException {
        return new SqlStats(url(name), "sa", "", "lc_", 3600, LOG);
    }

    @Test
    void twoServersShareOneRanking() throws SQLException {
        SqlStats hub = open("shared");
        SqlStats survival = open("shared");
        hub.add(A, "Alice", GameType.BLACKJACK, 100);
        survival.add(A, "Alice", GameType.BLACKJACK, -30);
        survival.add(B, "Bob", GameType.SLOT, 50);
        hub.close();
        survival.close();

        Ranking ranking = open("shared").ranking();
        assertEquals(List.of("Alice", "Bob"), ranking.top(null, 10).stream().map(Ranking.Row::name).toList());
        Ranking.Row alice = ranking.of(A, GameType.BLACKJACK);
        assertEquals(70, alice.net());
        assertEquals(2, alice.plays());
        assertEquals(50, ranking.of(B, null).net());
    }

    @Test
    void ownRecordShowsBeforeReload() throws Exception {
        SqlStats stats = open("own");
        stats.add(A, "Alice", GameType.ROULETTE, 300);
        // 専用のスレッドで足されるので少し待つ
        for (int i = 0; i < 100 && stats.ranking().of(A, null) == null; i++) {
            Thread.sleep(20);
        }
        assertEquals(300, stats.ranking().of(A, null).net());
        stats.close();
    }

    @Test
    void refusesNewerSchema() throws SQLException {
        open("newer").close();
        try (Connection c = DriverManager.getConnection(url("newer"), "sa", ""); Statement st = c.createStatement()) {
            st.executeUpdate("UPDATE lc_meta SET v = '" + (SqlStats.SCHEMA + 1) + "' WHERE k = 'schema'");
        }
        assertThrows(SQLException.class, () -> open("newer"));
    }

    @Test
    void rejectsBadPrefix() {
        assertThrows(SQLException.class, () -> new SqlStats(url("bad"), "sa", "", "x; DROP", 3600, LOG));
    }
}
