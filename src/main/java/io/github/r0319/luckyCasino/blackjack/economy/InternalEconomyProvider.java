package io.github.r0319.luckyCasino.blackjack.economy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;

/**
 * File-backed, fully self-contained {@link EconomyProvider}.
 *
 * <p>Designed as the <em>last-resort fallback</em> when no external economy
 * plugin (Vault / ExcellentEconomy) is installed.  This makes the plugin
 * fully functional in a vanilla-only development environment without
 * installing any additional plugins.
 *
 * <p>Balances are persisted to
 * {@code plugins/LuckyCasino/wallets.json} after every write operation.
 *
 * <h3>Commands provided alongside this provider</h3>
 * <ul>
 *   <li>{@code /wallet} — balance check, admin give/take/set</li>
 *   <li>{@code /pay <player> <amount>} — player-to-player transfer</li>
 * </ul>
 */
public class InternalEconomyProvider implements EconomyProvider {

    // ── Configuration ─────────────────────────────────────────────────────────
    /** Starting balance given to players who have never been seen before. */
    public static final double DEFAULT_BALANCE = 1_000.0;
    /** Currency unit label used in formatted strings. */
    public static final String CURRENCY_SYMBOL = "コイン";

    // ── Internals ─────────────────────────────────────────────────────────────
    private final JavaPlugin plugin;
    private final Path walletsFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    /** UUID (as String) → balance. Using LinkedHashMap preserves insertion order in the JSON. */
    private final Map<UUID, Double> balances = new LinkedHashMap<>();

    public InternalEconomyProvider(JavaPlugin plugin) {
        this.plugin = plugin;
        this.walletsFile = plugin.getDataFolder().toPath().resolve("wallets.json");
    }

    /**
     * Loads existing wallet data from disk.
     * Always succeeds — missing file simply starts with empty balances.
     *
     * @return always {@code true}
     */
    public boolean init() {
        load();
        plugin.getLogger().info("[LuckyCasino] 内蔵ウォレットを使用します "
                + "(初期残高: " + format(DEFAULT_BALANCE) + "/プレイヤー)");
        plugin.getLogger().info("[LuckyCasino] 外部経済プラグインを導入すると自動的に切り替わります。");
        return true;
    }

    // ── EconomyProvider ───────────────────────────────────────────────────────

    @Override public String  getName()     { return "内蔵ウォレット"; }
    @Override public boolean isAvailable() { return true; }

    @Override
    public boolean hasBalance(Player player, double amount) {
        return rawBalance(player.getUniqueId()) >= amount;
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        UUID uuid = player.getUniqueId();
        double current = rawBalance(uuid);
        if (current < amount) return false;
        balances.put(uuid, current - amount);
        save();
        return true;
    }

    @Override
    public boolean deposit(Player player, double amount) {
        UUID uuid = player.getUniqueId();
        balances.put(uuid, rawBalance(uuid) + amount);
        save();
        return true;
    }

    @Override
    public double getBalance(Player player) {
        return rawBalance(player.getUniqueId());
    }

    @Override
    public String format(double amount) {
        // e.g. "1,500 コイン"
        return String.format("%,.0f %s", amount, CURRENCY_SYMBOL);
    }

    // ── Admin operations (used by WalletCommand) ──────────────────────────────

    public void adminSet(UUID uuid, double amount) {
        balances.put(uuid, Math.max(0.0, amount));
        save();
    }

    public void adminAdd(UUID uuid, double amount) {
        balances.put(uuid, rawBalance(uuid) + Math.abs(amount));
        save();
    }

    /**
     * @return false if the balance would go negative
     */
    public boolean adminRemove(UUID uuid, double amount) {
        double current = rawBalance(uuid);
        if (current < amount) return false;
        balances.put(uuid, current - amount);
        save();
        return true;
    }

    public double rawBalance(UUID uuid) {
        return balances.getOrDefault(uuid, DEFAULT_BALANCE);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void load() {
        if (!Files.exists(walletsFile)) return;
        try (Reader r = Files.newBufferedReader(walletsFile, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, Double>>() {}.getType();
            Map<String, Double> raw = gson.fromJson(r, type);
            if (raw != null) {
                raw.forEach((k, v) -> {
                    try { balances.put(UUID.fromString(k), v); }
                    catch (IllegalArgumentException ignored) {} // skip malformed entries
                });
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[LuckyCasino] wallets.json の読み込みに失敗しました", e);
        }
    }

    /** Persists the current state to disk. Called after every write. */
    public void save() {
        try {
            Files.createDirectories(walletsFile.getParent());
            Map<String, Double> raw = new LinkedHashMap<>();
            balances.forEach((uuid, bal) -> raw.put(uuid.toString(), bal));
            try (Writer w = Files.newBufferedWriter(walletsFile, StandardCharsets.UTF_8)) {
                gson.toJson(raw, w);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[LuckyCasino] wallets.json の保存に失敗しました", e);
        }
    }
}
