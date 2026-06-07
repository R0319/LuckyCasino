package io.github.r0319.luckyCasino.blackjack.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * {@link EconomyProvider} that delegates to whatever economy plugin is
 * registered with Vault's service manager.
 *
 * <p>This covers all Vault-compatible economy backends, including:
 * <ul>
 *   <li><b>ExcellentEconomy</b> — registers itself as a Vault provider;
 *       {@link #getName()} will return "ExcellentEconomy" when it is active.</li>
 *   <li><b>EssentialsX Economy</b></li>
 *   <li>Any other Vault-compatible plugin</li>
 * </ul>
 *
 * <p>DonutAuction uses Vault-registered economy plugins for monetary transactions,
 * so this provider is also the correct backend when DonutAuction is installed.
 */
public class VaultEconomyProvider implements EconomyProvider {

    private Economy economy;
    private String name = "Unknown (Vault)";

    /**
     * Attempts to bind to the Vault economy service.
     *
     * @return true if a Vault economy provider was found and bound
     */
    public boolean init(JavaPlugin plugin) {
        // Vault plugin itself must be present
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;

        economy = rsp.getProvider();
        if (economy == null) return false;

        // economy.getName() returns the provider plugin name, e.g. "ExcellentEconomy"
        name = economy.getName() + " (Vault経由)";
        return true;
    }

    // ── EconomyProvider ───────────────────────────────────────────────────────

    @Override public String  getName()      { return name; }
    @Override public boolean isAvailable()  { return economy != null; }

    @Override
    public boolean hasBalance(Player player, double amount) {
        if (economy == null) return false;
        return economy.has(player, amount);
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        if (economy == null) return false;
        EconomyResponse r = economy.withdrawPlayer(player, amount);
        return r.transactionSuccess();
    }

    @Override
    public boolean deposit(Player player, double amount) {
        if (economy == null) return false;
        EconomyResponse r = economy.depositPlayer(player, amount);
        return r.transactionSuccess();
    }

    @Override
    public double getBalance(Player player) {
        if (economy == null) return 0.0;
        return economy.getBalance((OfflinePlayer) player);
    }

    @Override
    public String format(double amount) {
        if (economy == null) return String.format("%.2f", amount);
        return economy.format(amount);
    }
}
