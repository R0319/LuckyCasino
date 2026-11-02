package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.bridge.BridgeProtocol;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** LuckyCasino（ブラックジャック・ルーレット・ポーカー）。 */
public final class LuckyCasinoPaper extends JavaPlugin {

    private BridgeClient bridge;
    private Bank bank;
    private ChipLedger ledger;
    private Stats stats;
    private BlackjackSettings blackjackSettings;
    private RouletteSettings rouletteSettings;
    private SlotSettings slotSettings;
    private PokerSettings pokerSettings;
    private TableItem tableItem;
    private Menus menus;
    private TableManager tables;
    private Achievements achievements;
    private DealerMenu dealerMenu;
    private SpotMenu spotMenu;
    private final java.util.Map<GameType, SpotSettings> spotSettings = new java.util.EnumMap<>(GameType.class);

    @Override
    public void onEnable() {
        saveDefaultConfig();
        L10n.serverLanguage(getConfig().getString("language", "ja"));
        startBridge();
        startResourcePack();

        ledger = new ChipLedger(new File(getDataFolder(), "chips.yml"), getLogger());
        bank = createBank();
        refundLedger();
        stats = createStats();

        blackjackSettings = BlackjackSettings.load(section("blackjack"));
        rouletteSettings = RouletteSettings.load(section("roulette"));
        pokerSettings = PokerSettings.load(section("poker"));
        slotSettings = SlotSettings.load(section("slot"));
        for (GameType type : GameType.values()) {
            if (type.spotBets()) {
                spotSettings.put(type, SpotSettings.load(section(type.id())));
            }
        }
        getLogger().info(L10n.plain("スロットの還元率 %s%%、何か戻る確率 %s%%（5 ライン）",
                String.format("%.1f", slotSettings.paytable().returnRate() * 100), String.format("%.1f", slotSettings.paytable().hitRate() * 100)));
        if (slotSettings.paytable().returnRate() >= 1) {
            getLogger().warning(L10n.plain("スロットの還元率が 100%% 以上です。回し続けるほどチップが増えるので、config.yml の slot の配当を下げてください"));
        }
        tableItem = new TableItem(this);
        tableItem.registerRecipes(getConfig().getConfigurationSection("recipes"), getLogger());
        achievements = new Achievements(getLogger(), new File(getDataFolder(), "achievements.yml"));
        achievements.register();
        getServer().getPluginManager().registerEvents(achievements, this);
        menus = new Menus(this);
        tables = new TableManager(this);
        dealerMenu = new DealerMenu(this);
        spotMenu = new SpotMenu(this);
        getServer().getPluginManager().registerEvents(spotMenu, this);
        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getPluginManager().registerEvents(dealerMenu, this);
        getServer().getPluginManager().registerEvents(tables, this);
        tables.load();

        registerCommand("casino", L10n.plain("LuckyCasino のテーブル操作"), new CasinoCommand(this));
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new RankingPlaceholders(this).register();
            getLogger().info(L10n.plain("PlaceholderAPI に収支ランキングのプレースホルダー（%%luckycasino_…%%）を登録しました"));
        }
    }

    private ConfigurationSection section(String path) {
        ConfigurationSection section = getConfig().getConfigurationSection(path);
        return section == null ? new MemoryConfiguration() : section;
    }

    @Override
    public void onDisable() {
        SelfTest.stopAll();
        if (tables != null) {
            tables.despawnAll();
        }
        if (stats != null) {
            stats.close();
        }
        if (bridge != null) {
            bridge.stop();
        }
    }

    private void startBridge() {
        String host = getConfig().getString("bridge.host", "127.0.0.1");
        int port = getConfig().getInt("bridge.port", BridgeProtocol.DEFAULT_PORT);
        String key = getConfig().getString("bridge.key", "");
        bridge = new BridgeClient(getLogger(), host, port, key, this::stateLines);
        if (!getConfig().getBoolean("bridge.enabled", true)) {
            getLogger().info(L10n.plain("bridge.enabled が false のため Geyser 拡張とは通信しません"));
        } else if (key.length() < BridgeProtocol.MIN_KEY_LENGTH) {
            getLogger().warning(L10n.plain("bridge.key が未設定です。Geyser 拡張の config.properties の key を config.yml に設定してください"));
        } else {
            bridge.start();
        }
    }

    private void startResourcePack() {
        String mode = getConfig().getString("resource-pack.mode", "");
        String packUrl = getConfig().getString("resource-pack.url", "");
        String packSha1 = getConfig().getString("resource-pack.sha1", "");
        Glyphs.external("external".equals(mode));
        if ("external".equals(mode) || "off".equals(mode)) {
            return;
        }
        if (!packUrl.isEmpty() && packSha1.matches("[0-9a-f]{40}")) {
            getServer().getPluginManager().registerEvents(new ResourcePackListener(packUrl, packSha1), this);
        } else if (!packUrl.isEmpty()) {
            getLogger().warning(L10n.plain("resource-pack.sha1 が 40 文字の 16 進ではないため、リソースパックを送りません"));
        }
    }

    /** 収支の記録の置き場所。ranking.storage が mysql なら DB（つながらなければファイル）、それ以外はファイル。 */
    private Stats createStats() {
        File file = new File(getDataFolder(), "stats.yml");
        String storage = getConfig().getString("ranking.storage", "file");
        if ("mysql".equalsIgnoreCase(storage)) {
            ConfigurationSection db = section("ranking.mysql");
            String host = db.getString("host", "127.0.0.1");
            int port = db.getInt("port", 3306);
            String database = db.getString("database", "luckycasino");
            String prefix = db.getString("table-prefix", "luckycasino_");
            try {
                Stats sql = new SqlStats(SqlStats.mysqlUrl(host, port, database), db.getString("user", ""), db.getString("password", ""),
                        prefix, db.getLong("refresh-seconds", 30), getLogger());
                getLogger().info(L10n.plain("収支のランキング: DB（%s:%s/%s、表 %sstats）", host, port, database, prefix));
                return sql;
            } catch (java.sql.SQLException | RuntimeException e) {
                getLogger().log(java.util.logging.Level.SEVERE,
                        L10n.plain("収支のランキングの DB（%s:%s/%s）につなげないため、このサーバーの stats.yml に記録します", host, port, database), e);
            }
        }
        return new Stats.Yaml(file, getLogger());
    }

    /** Vault の経済プラグインがあればそのお金で、無ければ練習用チップで遊ぶ。 */
    private Bank createBank() {
        String mode = getConfig().getString("economy.mode", "auto");
        long practiceChips = Math.max(1, getConfig().getLong("economy.practice-chips", 1000));
        if (!"practice".equals(mode) && getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> provider = getServer().getServicesManager().getRegistration(Economy.class);
            if (provider != null) {
                double rate = getConfig().getDouble("economy.money-per-chip", 1.0);
                getLogger().info(L10n.plain("通貨: %s（チップ 1 枚 = %s）", provider.getProvider().getName(), rate));
                return new Bank.Vault(provider.getProvider(), rate > 0 ? rate : 1.0);
            }
        }
        if ("vault".equals(mode)) {
            getLogger().warning(L10n.plain("economy.mode が vault ですが、Vault の経済プラグインが見つかりません。練習用で動かします"));
        }
        getLogger().info(L10n.plain("通貨: 練習用チップ（お金は使いません）"));
        return new Bank.Practice(practiceChips);
    }

    /** 前回、精算できないまま止まった分をお金に戻す。 */
    private void refundLedger() {
        if (bank.practice()) {
            return;
        }
        Map<UUID, Long> owed = ledger.drain();
        owed.forEach((uuid, chips) -> {
            bank.deposit(getServer().getOfflinePlayer(uuid), chips);
            getLogger().info(L10n.plain("前回精算できなかったチップ %s 枚を %s に戻しました", chips, uuid));
        });
    }

    Stats stats() {
        return stats;
    }

    BridgeClient bridge() {
        return bridge;
    }

    Bank bank() {
        return bank;
    }

    ChipLedger ledger() {
        return ledger;
    }

    BlackjackSettings blackjackSettings() {
        return blackjackSettings;
    }

    RouletteSettings rouletteSettings() {
        return rouletteSettings;
    }

    SlotSettings slotSettings() {
        return slotSettings;
    }

    SpotSettings spotSettings(GameType type) {
        return spotSettings.get(type);
    }

    SpotMenu spotMenu() {
        return spotMenu;
    }

    PokerSettings pokerSettings() {
        return pokerSettings;
    }

    TableItem tableItem() {
        return tableItem;
    }

    Achievements achievements() {
        return achievements;
    }

    DealerMenu dealerMenu() {
        return dealerMenu;
    }

    Menus menus() {
        return menus;
    }

    TableManager tables() {
        return tables;
    }

    /** 通信スレッドから呼ばれるので、メインスレッドで今のカード一覧を作って返す。 */
    private List<String> stateLines() {
        CompletableFuture<List<String>> future = new CompletableFuture<>();
        getServer().getScheduler().runTask(this, () -> {
            List<String> lines = new ArrayList<>();
            if (tables != null) {
                tables.tables().forEach(table -> table.stateLines(lines));
            }
            future.complete(lines);
        });
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            return List.of();
        }
    }
}
