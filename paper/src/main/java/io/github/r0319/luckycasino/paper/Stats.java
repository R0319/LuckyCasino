package io.github.r0319.luckycasino.paper;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 収支の記録の置き場所。 */
interface Stats {

    /** 席を立ったときに 1 回分の収支を足す（メインスレッドから呼ぶ）。 */
    void add(UUID player, String name, GameType type, long net);

    /** 今のランキング（すぐ返る。メインスレッドからもプレースホルダーからも呼んでよい）。 */
    Ranking ranking();

    /** プラグインを止めるときに呼ぶ。 */
    default void close() {
    }

    /**
     * サーバーごとのファイル（plugins/LuckyCasino/stats.yml）。
     *
     * <pre>
     * players:
     *   &lt;uuid&gt;:
     *     name: R0319
     *     games:
     *       blackjack: { net: 120, plays: 3 }
     * </pre>
     */
    final class Yaml implements Stats {

        private final File file;
        private final Logger logger;
        private final Map<UUID, String> names = new HashMap<>();
        private final Map<UUID, Map<GameType, Ranking.Totals>> games = new HashMap<>();
        private Ranking ranking = Ranking.EMPTY;

        Yaml(File file, Logger logger) {
            this.file = file;
            this.logger = logger;
            load();
            rebuild();
        }

        private void load() {
            if (!file.exists()) {
                return;
            }
            ConfigurationSection players = YamlConfiguration.loadConfiguration(file).getConfigurationSection("players");
            if (players == null) {
                return;
            }
            for (String key : players.getKeys(false)) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(key);
                } catch (IllegalArgumentException e) {
                    logger.warning(L10n.plain("stats.yml の不正な行を読み飛ばしました: %s", key));
                    continue;
                }
                ConfigurationSection player = players.getConfigurationSection(key);
                if (player == null) {
                    continue;
                }
                names.put(uuid, player.getString("name", key));
                Map<GameType, Ranking.Totals> totals = new EnumMap<>(GameType.class);
                ConfigurationSection section = player.getConfigurationSection("games");
                if (section != null) {
                    for (String id : section.getKeys(false)) {
                        GameType type = GameType.byId(id);
                        if (type != null) {
                            totals.put(type, new Ranking.Totals(section.getLong(id + ".net"), section.getLong(id + ".plays")));
                        }
                    }
                }
                games.put(uuid, totals);
            }
        }

        @Override
        public void add(UUID player, String name, GameType type, long net) {
            names.put(player, name);
            games.computeIfAbsent(player, k -> new EnumMap<>(GameType.class))
                    .merge(type, new Ranking.Totals(net, 1), (a, b) -> a.plus(b.net(), b.plays()));
            rebuild();
            save();
        }

        @Override
        public Ranking ranking() {
            return ranking;
        }

        private void rebuild() {
            List<Ranking.Entry> entries = new ArrayList<>(games.size());
            games.forEach((uuid, totals) -> entries.add(new Ranking.Entry(uuid, names.getOrDefault(uuid, uuid.toString()), totals)));
            ranking = new Ranking(entries);
        }

        private void save() {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.options().setHeader(List.of(
                    L10n.plain("LuckyCasino の収支（席を立ったときの「戻したチップ - 両替したチップ」の合計）。"),
                    L10n.plain("サーバーを止めてから編集してください。プレイヤーの行を消すとその人の記録が消えます。")));
            games.forEach((uuid, totals) -> {
                String base = "players." + uuid;
                yaml.set(base + ".name", names.get(uuid));
                totals.forEach((type, t) -> {
                    yaml.set(base + ".games." + type.id() + ".net", t.net());
                    yaml.set(base + ".games." + type.id() + ".plays", t.plays());
                });
            });
            try {
                yaml.save(file);
            } catch (IOException e) {
                logger.log(Level.SEVERE, L10n.plain("stats.yml を保存できません"), e);
            }
        }
    }
}
