package io.github.r0319.luckyCasino.blackjack.economy;

import org.bukkit.entity.Player;

/**
 * Abstraction over any underlying economy backend (Vault, ExcellentEconomy, etc.).
 * <p>
 * All implementations must be safe to call even when the underlying plugin is
 * not available — methods should return {@code false}/{@code 0.0} rather than
 * throwing exceptions.
 */
public interface EconomyProvider {

    /** Human-readable name shown in logs (e.g. "ExcellentEconomy via Vault"). */
    String getName();

    /** Returns true if the provider was successfully initialised. */
    boolean isAvailable();

    /** Returns true if the player's current balance is at least {@code amount}. */
    boolean hasBalance(Player player, double amount);

    /**
     * Withdraws {@code amount} from the player.
     *
     * @return true if the transaction succeeded
     */
    boolean withdraw(Player player, double amount);

    /**
     * Deposits {@code amount} into the player's account.
     *
     * @return true if the transaction succeeded
     */
    boolean deposit(Player player, double amount);

    double getBalance(Player player);

    /** Returns a locale-aware currency string (e.g. "1,200 Coins"). */
    String format(double amount);
}
