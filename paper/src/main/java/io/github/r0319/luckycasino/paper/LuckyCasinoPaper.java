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
    private BlackjackSettings blackjackSettings;
    private RouletteSettings rouletteSettings;
    private SlotSettings slotSettings;
    private PokerSettings pokerSettings;
    private TableItem tableItem;
    private Menus menus;
    private TableManager tables;
    private Achievements achievements;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        startBridge();
        startResourcePack();

        ledger = new ChipLedger(new File(getDataFolder(), "chips.yml"), getLogger());
        bank = createBank();
        refundLedger();

        blackjackSettings = BlackjackSettings.load(section("blackjack"));
        rouletteSettings = RouletteSettings.load(section("roulette"));
        pokerSettings = PokerSettings.load(section("poker"));
        slotSettings = SlotSettings.load(section("slot"));
        getLogger().info(String.format("スロットの還元率 %.1f%%、何か戻る確率 %.1f%%（5 ライン）",
                slotSettings.paytable().returnRate() * 100, slotSettings.paytable().hitRate() * 100));
        tableItem = new TableItem(this);
        tableItem.registerRecipes(getConfig().getConfigurationSection("recipes"), getLogger());
        achievements = new Achievements(getLogger());
        achievements.register();
        getServer().getPluginManager().registerEvents(achievements, this);
        menus = new Menus(this);
        tables = new TableManager(this);
        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getPluginManager().registerEvents(tables, this);
        tables.load();

        registerCommand("casino", "LuckyCasino のテーブル操作", new CasinoCommand(this));
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
            getLogger().info("bridge.enabled が false のため Geyser 拡張とは通信しません");
        } else if (key.length() < BridgeProtocol.MIN_KEY_LENGTH) {
            getLogger().warning("bridge.key が未設定です。Geyser 拡張の config.properties の key を config.yml に設定してください");
        } else {
            bridge.start();
        }
    }

    private void startResourcePack() {
        String packUrl = getConfig().getString("resource-pack.url", "");
        String packSha1 = getConfig().getString("resource-pack.sha1", "");
        if (!packUrl.isEmpty() && packSha1.matches("[0-9a-f]{40}")) {
            getServer().getPluginManager().registerEvents(new ResourcePackListener(packUrl, packSha1), this);
        } else if (!packUrl.isEmpty()) {
            getLogger().warning("resource-pack.sha1 が 40 文字の 16 進ではないため、リソースパックを送りません");
        }
    }

    /** Vault の経済プラグインがあればそのお金で、無ければ練習用チップで遊ぶ。 */
    private Bank createBank() {
        String mode = getConfig().getString("economy.mode", "auto");
        long practiceChips = Math.max(1, getConfig().getLong("economy.practice-chips", 1000));
        if (!"practice".equals(mode) && getServer().getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> provider = getServer().getServicesManager().getRegistration(Economy.class);
            if (provider != null) {
                double rate = getConfig().getDouble("economy.money-per-chip", 1.0);
                getLogger().info("通貨: " + provider.getProvider().getName() + "（チップ 1 枚 = " + rate + "）");
                return new Bank.Vault(provider.getProvider(), rate > 0 ? rate : 1.0);
            }
        }
        if ("vault".equals(mode)) {
            getLogger().warning("economy.mode が vault ですが、Vault の経済プラグインが見つかりません。練習用で動かします");
        }
        getLogger().info("通貨: 練習用チップ（お金は使いません）");
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
            getLogger().info("前回精算できなかったチップ " + chips + " 枚を " + uuid + " に戻しました");
        });
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

    PokerSettings pokerSettings() {
        return pokerSettings;
    }

    TableItem tableItem() {
        return tableItem;
    }

    Achievements achievements() {
        return achievements;
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
