package io.github.r0319.luckycasino.paper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MySQL・MariaDB に記録する（複数のサーバーで 1 つのランキングにする）。
 * DB とのやり取りはすべて専用のスレッド 1 本で行い、メインスレッドは待たせない。
 * 読み直しは refresh 秒ごと（ほかのサーバーの記録を取り込む）。自分のサーバーの記録はすぐ順位に入れる。
 *
 * <p>表（prefix は設定の table-prefix）:
 * <pre>
 * &lt;prefix&gt;meta     (k, v)                         schema = 表の形の版
 * &lt;prefix&gt;players  (player, name, updated)        名前は最後に遊んだときのもの
 * &lt;prefix&gt;stats    (player, game, net, plays)     主キー (player, game)。足し算は DB の中で行う
 * </pre>
 * MySQL と MariaDB のどちらでも動く SQL だけを使う。
 */
final class SqlStats implements Stats {

    /** 表の形の版。形を変えるときは上げ、migrate に古い版からの直し方を足す。 */
    static final int SCHEMA = 1;
    private static final int MAX_PENDING = 10_000;

    private record Pending(UUID player, String name, GameType type, long net) {
    }

    private final String url;
    private final Properties properties;
    private final String prefix;
    private final Logger logger;
    private final ScheduledExecutorService worker;
    private final Deque<Pending> pending = new ArrayDeque<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, Map<GameType, Ranking.Totals>> games = new HashMap<>();
    private Connection connection;
    private volatile Ranking ranking = Ranking.EMPTY;

    /**
     * 表を用意して最初の読み込みまで済ませる（起動時に 1 回だけ呼ぶので、ここは待つ）。
     * つながらない・表の形が新しすぎるときは SQLException。
     */
    SqlStats(String url, String user, String password, String prefix, long refreshSeconds, Logger logger) throws SQLException {
        if (!prefix.matches("[A-Za-z0-9_]{0,32}")) {
            throw new SQLException(L10n.plain("table-prefix は英数字と _ の 32 文字までです: %s", prefix));
        }
        this.url = url;
        this.properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", password);
        this.prefix = prefix;
        this.logger = logger;
        connection();
        migrate();
        reload();
        this.worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "LuckyCasino-Stats");
            thread.setDaemon(true);
            return thread;
        });
        long period = Math.max(5, refreshSeconds);
        worker.scheduleWithFixedDelay(this::refresh, period, period, TimeUnit.SECONDS);
    }

    /** JDBC の URL（MySQL Connector/J。Paper に入っている）。 */
    static String mysqlUrl(String host, int port, String database) {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&connectTimeout=5000&socketTimeout=10000&tcpKeepAlive=true";
    }

    private Connection connection() throws SQLException {
        if (connection == null || !connection.isValid(2)) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                    // 切れた接続を閉じられなくても続ける
                }
            }
            connection = DriverManager.getConnection(url, properties);
        }
        return connection;
    }

    private void migrate() throws SQLException {
        try (Statement st = connection().createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "meta (k VARCHAR(32) NOT NULL PRIMARY KEY, v VARCHAR(64) NOT NULL)");
            int version = 0;
            try (ResultSet rs = st.executeQuery("SELECT v FROM " + prefix + "meta WHERE k = 'schema'")) {
                if (rs.next()) {
                    version = Integer.parseInt(rs.getString(1));
                }
            }
            if (version > SCHEMA) {
                throw new SQLException(L10n.plain("収支の表（%smeta）の版 %s はこのプラグインより新しいため使えません。プラグインを新しくしてください", prefix, version));
            }
            if (version < 1) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "players (player CHAR(36) NOT NULL PRIMARY KEY,"
                        + " name VARCHAR(32) NOT NULL, updated BIGINT NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + prefix + "stats (player CHAR(36) NOT NULL, game VARCHAR(32) NOT NULL,"
                        + " net BIGINT NOT NULL DEFAULT 0, plays BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (player, game))");
                st.executeUpdate("DELETE FROM " + prefix + "meta WHERE k = 'schema'");
                st.executeUpdate("INSERT INTO " + prefix + "meta (k, v) VALUES ('schema', '" + SCHEMA + "')");
            }
            // 版 2 以降を足すときは: if (version < 2) { ALTER TABLE …; UPDATE meta … }
        }
    }

    @Override
    public void add(UUID player, String name, GameType type, long net) {
        worker.execute(() -> {
            // すぐ順位に入れる（DB に書けなかったときも、書き直すまでの間は表示に入れておく）
            names.put(player, name);
            games.computeIfAbsent(player, k -> new EnumMap<>(GameType.class))
                    .merge(type, new Ranking.Totals(net, 1), (a, b) -> a.plus(b.net(), b.plays()));
            rebuild();
            if (pending.size() >= MAX_PENDING) {
                Pending dropped = pending.removeFirst();
                logger.warning(L10n.plain("DB に書けない収支がたまりすぎたため、古いものを捨てました: %s %s %s", dropped.name(), dropped.type().id(), dropped.net()));
            }
            pending.addLast(new Pending(player, name, type, net));
            flush();
        });
    }

    /** たまっている収支を DB に書く（専用のスレッドから）。書けなければ残して次の読み直しのときにまた試す。 */
    private void flush() {
        while (!pending.isEmpty()) {
            Pending p = pending.peekFirst();
            try {
                write(p);
                pending.removeFirst();
            } catch (SQLException e) {
                logger.log(Level.WARNING, L10n.plain("収支を DB に書けません（あとで書き直します。残り %s 件）", pending.size()), e);
                return;
            }
        }
    }

    private void write(Pending p) throws SQLException {
        Connection c = connection();
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try (PreparedStatement stats = c.prepareStatement("INSERT INTO " + prefix + "stats (player, game, net, plays) VALUES (?, ?, ?, 1)"
                + " ON DUPLICATE KEY UPDATE net = net + ?, plays = plays + 1");
             PreparedStatement players = c.prepareStatement("INSERT INTO " + prefix + "players (player, name, updated) VALUES (?, ?, ?)"
                     + " ON DUPLICATE KEY UPDATE name = ?, updated = ?")) {
            long now = System.currentTimeMillis();
            stats.setString(1, p.player().toString());
            stats.setString(2, p.type().id());
            stats.setLong(3, p.net());
            stats.setLong(4, p.net());
            stats.executeUpdate();
            players.setString(1, p.player().toString());
            players.setString(2, p.name());
            players.setLong(3, now);
            players.setString(4, p.name());
            players.setLong(5, now);
            players.executeUpdate();
            c.commit();
        } catch (SQLException e) {
            try {
                c.rollback();
            } catch (SQLException ignored) {
                // 接続が切れていれば戻せない（DB 側で取り消される）
            }
            throw e;
        } finally {
            try {
                c.setAutoCommit(auto);
            } catch (SQLException ignored) {
                // 次に使うときにつなぎ直す
            }
        }
    }

    /** 書けていない分を書いてから、DB の全部を読み直す（専用のスレッドから）。 */
    private void refresh() {
        flush();
        if (!pending.isEmpty()) {
            return;
        }
        try {
            reload();
        } catch (SQLException e) {
            logger.log(Level.WARNING, L10n.plain("収支のランキングを DB から読み直せません"), e);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, L10n.plain("収支のランキングを DB から読み直せません"), e);
        }
    }

    private void reload() throws SQLException {
        Map<UUID, String> newNames = new HashMap<>();
        Map<UUID, Map<GameType, Ranking.Totals>> newGames = new HashMap<>();
        try (Statement st = connection().createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT player, name FROM " + prefix + "players")) {
                while (rs.next()) {
                    UUID uuid = parse(rs.getString(1));
                    if (uuid != null) {
                        newNames.put(uuid, rs.getString(2));
                    }
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT player, game, net, plays FROM " + prefix + "stats")) {
                while (rs.next()) {
                    UUID uuid = parse(rs.getString(1));
                    GameType type = GameType.byId(rs.getString(2));
                    if (uuid != null && type != null) {
                        newGames.computeIfAbsent(uuid, k -> new EnumMap<>(GameType.class))
                                .put(type, new Ranking.Totals(rs.getLong(3), rs.getLong(4)));
                    }
                }
            }
        }
        names.clear();
        names.putAll(newNames);
        games.clear();
        games.putAll(newGames);
        rebuild();
    }

    private static UUID parse(String text) {
        try {
            return UUID.fromString(text.trim());
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    private void rebuild() {
        List<Ranking.Entry> entries = new ArrayList<>(games.size());
        games.forEach((uuid, totals) -> entries.add(new Ranking.Entry(uuid, names.getOrDefault(uuid, uuid.toString()), Map.copyOf(totals))));
        ranking = new Ranking(entries);
    }

    @Override
    public Ranking ranking() {
        return ranking;
    }

    /** 書けていない分を書いてから閉じる（最大 10 秒待つ）。 */
    @Override
    public void close() {
        worker.execute(() -> {
            flush();
            if (!pending.isEmpty()) {
                for (Pending p : pending) {
                    logger.warning(L10n.plain("DB に書けなかった収支: %s（%s）%s %s 枚", p.name(), p.player(), p.type().id(), p.net()));
                }
            }
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException ignored) {
                // 止めるときなので気にしない
            }
        });
        worker.shutdown();
        try {
            if (!worker.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning(L10n.plain("収支の DB への書き込みが 10 秒で終わりませんでした"));
                worker.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
