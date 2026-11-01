package io.github.r0319.luckycasino.paper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 収支のランキング。1 回座ってから立つまでの「戻したチップ − 両替したチップ」をプレイヤーごと・ゲームごとに足したもの。
 * 作ったあとは変えない（表示やプレースホルダーから何度読んでもよい）。
 */
final class Ranking {

    /** プレイヤー 1 人の 1 ゲーム分（または全ゲームの合計）。 */
    record Totals(long net, long plays) {

        static final Totals ZERO = new Totals(0, 0);

        Totals plus(long net, long plays) {
            return new Totals(this.net + net, this.plays + plays);
        }
    }

    /** 順位表の 1 行。rank は 1 から。 */
    record Row(int rank, UUID player, String name, long net, long plays) {
    }

    /** 記録そのもの（保存先から読んだ形）。 */
    record Entry(UUID player, String name, Map<GameType, Totals> games) {
    }

    static final Ranking EMPTY = new Ranking(List.of());

    private final Map<GameType, List<Row>> byGame = new EnumMap<>(GameType.class);
    private final List<Row> overall;
    private final Map<UUID, Row> overallOf = new HashMap<>();
    private final Map<GameType, Map<UUID, Row>> byGameOf = new EnumMap<>(GameType.class);

    Ranking(List<Entry> entries) {
        overall = rank(entries, null);
        overall.forEach(row -> overallOf.put(row.player(), row));
        for (GameType type : GameType.values()) {
            List<Row> rows = rank(entries, type);
            byGame.put(type, rows);
            Map<UUID, Row> of = new HashMap<>();
            rows.forEach(row -> of.put(row.player(), row));
            byGameOf.put(type, of);
        }
    }

    private static List<Row> rank(List<Entry> entries, GameType type) {
        record Item(Entry entry, Totals totals) {
        }
        List<Item> items = new ArrayList<>();
        for (Entry entry : entries) {
            Totals totals;
            if (type == null) {
                totals = Totals.ZERO;
                for (Totals t : entry.games().values()) {
                    totals = totals.plus(t.net(), t.plays());
                }
            } else {
                totals = entry.games().get(type);
            }
            if (totals != null && totals.plays() > 0) {
                items.add(new Item(entry, totals));
            }
        }
        // 収支の多い順。同じなら遊んだ回数の少ない順、それも同じなら名前順（毎回同じ並びにする）
        items.sort(Comparator.<Item>comparingLong(i -> -i.totals().net())
                .thenComparingLong(i -> i.totals().plays())
                .thenComparing(i -> i.entry().name(), String.CASE_INSENSITIVE_ORDER));
        List<Row> rows = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            // 収支が同じ人は同じ順位（1, 2, 2, 4 …）
            int rank = i > 0 && items.get(i - 1).totals().net() == item.totals().net() ? rows.get(i - 1).rank() : i + 1;
            rows.add(new Row(rank, item.entry().player(), item.entry().name(), item.totals().net(), item.totals().plays()));
        }
        return List.copyOf(rows);
    }

    /** 上から limit 人。type が null なら全ゲームの合計。 */
    List<Row> top(GameType type, int limit) {
        List<Row> rows = type == null ? overall : byGame.get(type);
        return rows.subList(0, Math.min(limit, rows.size()));
    }

    /** n 位（1 から）の行。いなければ null。 */
    Row at(GameType type, int n) {
        List<Row> rows = type == null ? overall : byGame.get(type);
        return n >= 1 && n <= rows.size() ? rows.get(n - 1) : null;
    }

    /** その人の行。まだ遊んでいなければ null。 */
    Row of(UUID player, GameType type) {
        return type == null ? overallOf.get(player) : byGameOf.get(type).get(player);
    }

    /** +1,200 / -300 / 0 の形。 */
    static String signed(long net) {
        return net == 0 ? "0" : String.format(Locale.ROOT, "%+,d", net);
    }

    /** 載っている人数。 */
    int size(GameType type) {
        return (type == null ? overall : byGame.get(type)).size();
    }

    /** PlaceholderAPI の params（%luckycasino_ と % の間）を値にする。viewer は見ている人（いなければ null）。知らない形なら null（PlaceholderAPI はそのまま残す）。 */
    String placeholder(UUID viewer, String params) {
        String rest = params.toLowerCase(Locale.ROOT);
        GameType type = null;
        int underscore = rest.indexOf('_');
        if (underscore > 0) {
            GameType byId = GameType.byId(rest.substring(0, underscore));
            if (byId != null) {
                type = byId;
                rest = rest.substring(underscore + 1);
            }
        } else if (GameType.byId(rest) != null) {
            return null;
        }
        if (rest.startsWith("top_")) {
            String[] parts = rest.split("_", 3);
            if (parts.length < 3) {
                return null;
            }
            int n;
            try {
                n = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return null;
            }
            return field(at(type, n), parts[2], "---");
        }
        if (viewer == null) {
            return "";
        }
        Ranking.Row own = of(viewer, type);
        if (rest.equals("rank")) {
            return own == null ? "-" : Integer.toString(own.rank());
        }
        if (own == null) {
            return switch (rest) {
                case "net", "net_raw", "plays" -> "0";
                default -> null;
            };
        }
        return field(own, rest, "");
    }

    private static String field(Ranking.Row row, String field, String none) {
        return switch (field) {
            case "name" -> row == null ? none : row.name();
            case "net" -> row == null ? "" : Ranking.signed(row.net());
            case "net_raw" -> row == null ? "" : Long.toString(row.net());
            case "plays" -> row == null ? "" : Long.toString(row.plays());
            case "rank" -> row == null ? "" : Integer.toString(row.rank());
            default -> null;
        };
    }
}
