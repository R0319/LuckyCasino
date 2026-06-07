package io.github.r0319.luckyCasino.blackjack.economy;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Central economy facade used throughout the blackjack module.
 *
 * <h3>Provider detection order</h3>
 * <ol>
 *   <li><b>Vault</b> — covers any Vault-compatible backend:
 *       ExcellentEconomy, EssentialsX Economy, etc.
 *       {@code getName()} will reflect the actual provider (e.g. "ExcellentEconomy (Vault経由)").</li>
 *   <li><b>ExcellentEconomy 直接 API</b> — reflection-based fallback
 *       for servers that have ExcellentEconomy without Vault.</li>
 *   <li><b>内蔵ウォレット</b> ({@link InternalEconomyProvider}) — fully self-contained,
 *       no external plugins required.  Ideal for development environments.
 *       Balances persisted to {@code plugins/LuckyCasino/wallets.json}.
 *       Exposes {@code /wallet} and {@code /pay} commands.</li>
 * </ol>
 *
 * <h3>DonutAuction</h3>
 * DonutAuction is an auction-house plugin that uses Vault-registered economy
 * plugins for monetary transactions.  It is covered by provider 1 when both
 * are installed.  The {@code softdepend} entry in plugin.yml guarantees
 * correct load ordering.
 */
public class EconomyManager implements EconomyProvider {

    /** Active provider delegate (never null after {@link #setup}). */
    private EconomyProvider delegate = NoopEconomyProvider.INSTANCE;

    /**
     * Keeps a direct reference to the internal provider when it is active so
     * that admin operations ({@link #adminSet}, {@link #adminDeposit},
     * {@link #adminWithdraw}) can access the extra methods.
     * Null when an external provider was chosen.
     */
    private InternalEconomyProvider internalProvider;

    /**
     * Detects and initialises the best available economy provider.
     * Call from {@code onEnable} after all soft-depend plugins have loaded.
     *
     * @param plugin the host plugin
     * @return true if a functional provider was found (always true — internal is the fallback)
     */
    public boolean setup(JavaPlugin plugin) {

        // ── 1. Vault ─────────────────────────────────────────────────────────
        VaultEconomyProvider vault = new VaultEconomyProvider();
        if (vault.init(plugin)) {
            delegate = vault;
            plugin.getLogger().info("[LuckyCasino] 経済プロバイダ接続完了: " + delegate.getName());
            logDetectedPlugins(plugin);
            return true;
        }

        // ── 2. ExcellentEconomy 直接 API ──────────────────────────────────────
        ExcellentEconomyProvider ee = new ExcellentEconomyProvider(plugin);
        if (ee.init()) {
            delegate = ee;
            plugin.getLogger().info("[LuckyCasino] 経済プロバイダ接続完了: " + delegate.getName());
            return true;
        }

        // ── 3. 内蔵ウォレット（フォールバック）───────────────────────────────
        InternalEconomyProvider internal = new InternalEconomyProvider(plugin);
        internal.init(); // always succeeds
        delegate         = internal;
        internalProvider = internal;
        logDetectedPlugins(plugin);
        return true; // internal provider is always available
    }

    // ── Admin helpers (work with any provider) ────────────────────────────────

    /**
     * Forcibly sets a player's balance, adjusting via deposit/withdraw.
     * If the internal provider is active, uses its direct set method.
     */
    public void adminSet(Player player, double amount) {
        if (internalProvider != null) {
            internalProvider.adminSet(player.getUniqueId(), amount);
            return;
        }
        // Vault path: adjust current balance to reach the target
        double current = getBalance(player);
        double diff    = amount - current;
        if (diff > 0) deposit(player, diff);
        else if (diff < 0) withdraw(player, -diff);
    }

    /** Deposits {@code amount} unconditionally (admin use; bypasses balance check). */
    public void adminDeposit(Player player, double amount) {
        deposit(player, amount);
    }

    /**
     * Withdraws {@code amount} even if the player is at the default balance.
     *
     * @return false if the resulting balance would go negative
     */
    public boolean adminWithdraw(Player player, double amount) {
        if (internalProvider != null) {
            return internalProvider.adminRemove(player.getUniqueId(), amount);
        }
        return withdraw(player, amount);
    }

    /** Returns true if the internal (built-in) wallet is the active provider. */
    public boolean isUsingInternalWallet() {
        return internalProvider != null;
    }

    // ── EconomyProvider delegation ────────────────────────────────────────────

    @Override public String  getName()                             { return delegate.getName(); }
    @Override public boolean isAvailable()                         { return delegate.isAvailable(); }
    @Override public boolean hasBalance(Player p, double amount)   { return delegate.hasBalance(p, amount); }
    @Override public boolean withdraw(Player p, double amount)     { return delegate.withdraw(p, amount); }
    @Override public boolean deposit(Player p, double amount)      { return delegate.deposit(p, amount); }
    @Override public double  getBalance(Player p)                  { return delegate.getBalance(p); }
    @Override public String  format(double amount)                 { return delegate.format(amount); }

    // ── Diagnostics ───────────────────────────────────────────────────────────

    private void logDetectedPlugins(JavaPlugin plugin) {
        String[] relevant = {"ExcellentEconomy", "Vault", "donutauction"};
        StringBuilder sb = new StringBuilder("[LuckyCasino] 検出済み経済関連プラグイン: ");
        boolean any = false;
        for (String name : relevant) {
            Plugin p = plugin.getServer().getPluginManager().getPlugin(name);
            if (p != null && p.isEnabled()) {
                sb.append(name).append("(").append(p.getPluginMeta().getVersion()).append(") ");
                any = true;
            }
        }
        if (!any) sb.append("なし (内蔵ウォレット使用中)");
        plugin.getLogger().info(sb.toString().trim());
    }

    // ── No-op implementation (before setup() is called) ───────────────────────

    private enum NoopEconomyProvider implements EconomyProvider {
        INSTANCE;
        @Override public String  getName()                             { return "未設定"; }
        @Override public boolean isAvailable()                         { return false; }
        @Override public boolean hasBalance(Player p, double amount)   { return false; }
        @Override public boolean withdraw(Player p, double amount)     { return false; }
        @Override public boolean deposit(Player p, double amount)      { return false; }
        @Override public double  getBalance(Player p)                  { return 0.0; }
        @Override public String  format(double amount)                 { return String.format("%,.2f", amount); }
    }
}
