package io.github.r0319.luckyCasino;

import io.github.r0319.luckyCasino.blackjack.BlackjackModule;
import io.github.r0319.luckyCasino.blackjack.listener.BlackjackListener;
import io.github.r0319.luckyCasino.command.SpawnBlockDisplayCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class LuckyCasino extends JavaPlugin {

    private BlackjackModule blackjackModule;

    @Override
    public void onEnable() {
        // ── Debug: BlockDisplay test command ──────────────────────────────
        SpawnBlockDisplayCommand sbdHandler = new SpawnBlockDisplayCommand();
        getCommand("spawnblockdisplay").setExecutor(sbdHandler);
        getCommand("spawnblockdisplay").setTabCompleter(sbdHandler);

        // ── Blackjack module ──────────────────────────────────────────────
        blackjackModule = new BlackjackModule(this);
        blackjackModule.registerCommands();

        // ── Event listeners ───────────────────────────────────────────────
        getServer().getPluginManager().registerEvents(
                new BlackjackListener(blackjackModule), this);

        getLogger().info("LuckyCasino が有効化されました。");
    }

    @Override
    public void onDisable() {
        if (blackjackModule != null) {
            blackjackModule.shutdown();
        }
        getLogger().info("LuckyCasino が無効化されました。");
    }

    public BlackjackModule getBlackjackModule() { return blackjackModule; }
}
