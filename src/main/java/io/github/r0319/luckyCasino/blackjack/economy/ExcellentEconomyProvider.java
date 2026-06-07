package io.github.r0319.luckyCasino.blackjack.economy;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

/**
 * {@link EconomyProvider} that uses <b>ExcellentEconomy's own API directly</b>,
 * without going through Vault.
 *
 * <p>This is the fallback for servers that have ExcellentEconomy installed
 * but have <em>not</em> installed Vault (unusual but possible).
 *
 * <p><b>Integration strategy (reflection-based):</b><br>
 * ExcellentEconomy does not publish a standalone API jar to a public Maven
 * repository, so the calls are made via reflection to keep the LuckyCasino jar
 * free of hard compile-time dependencies.  If ExcellentEconomy's internal API
 * changes, only this class needs updating.
 *
 * <p>Reflected call path (ExcellentEconomy 1.x / NightEx API):
 * <pre>
 *   ExcellentEconomy plugin = ...
 *   EconomyManager   ecoMgr = plugin.getEconomyManager()          // or getCurrencyManager()
 *   UserManager      userMgr = ecoMgr.getUserManager()
 *   EconomyUser      user   = userMgr.getOrCreate(uuid)
 *   double balance           = user.getBalance()
 *   user.removeBalance(amount, true)   // true = force
 *   user.addBalance(amount, true)
 * </pre>
 *
 * <p>If reflection fails (API incompatibility), every operation returns a safe
 * default and a warning is logged so the server owner can investigate.
 */
public class ExcellentEconomyProvider implements EconomyProvider {

    /** Bukkit plugin name as registered in its own plugin.yml. */
    private static final String PLUGIN_NAME = "ExcellentEconomy";

    private final JavaPlugin host;
    private Plugin excellentEconomy;

    // Cached reflected method handles (null = not resolved yet)
    private Method getEconomyManager;
    private Method getUserManager;
    private Method getOrCreate;
    private Method getBalance;
    private Method removeBalance;
    private Method addBalance;

    private boolean available = false;

    public ExcellentEconomyProvider(JavaPlugin host) {
        this.host = host;
    }

    /**
     * Attempts to locate the ExcellentEconomy plugin and resolve its API via
     * reflection.
     *
     * @return true if the plugin was found and the API was successfully resolved
     */
    public boolean init() {
        Plugin plugin = host.getServer().getPluginManager().getPlugin(PLUGIN_NAME);
        if (plugin == null || !plugin.isEnabled()) return false;

        try {
            // Resolve the chain: plugin → EconomyManager → UserManager → EconomyUser
            Class<?> pluginClass      = plugin.getClass();
            getEconomyManager         = pluginClass.getMethod("getEconomyManager");
            Object ecoManager         = getEconomyManager.invoke(plugin);

            Class<?> ecoManagerClass  = ecoManager.getClass();
            getUserManager            = ecoManagerClass.getMethod("getUserManager");
            Object userManager        = getUserManager.invoke(ecoManager);

            Class<?> userManagerClass = userManager.getClass();
            getOrCreate               = findMethod(userManagerClass, "getOrCreate", UUID.class);

            if (getOrCreate == null) {
                host.getLogger().warning(
                        "[LuckyCasino] ExcellentEconomy の UserManager#getOrCreate メソッドが見つかりません。"
                                + " プラグインバージョンを確認してください。");
                return false;
            }

            // Probe one user to get the EconomyUser class for balance methods
            Object probeUser = getOrCreate.invoke(userManager, UUID.randomUUID());
            Class<?> userClass = probeUser.getClass();

            getBalance    = findMethod(userClass, "getBalance");
            removeBalance = findMethod(userClass, "removeBalance", double.class, boolean.class);
            addBalance    = findMethod(userClass, "addBalance",    double.class, boolean.class);

            if (getBalance == null || removeBalance == null || addBalance == null) {
                host.getLogger().warning(
                        "[LuckyCasino] ExcellentEconomy の残高メソッドが見つかりません。");
                return false;
            }

            excellentEconomy = plugin;
            available = true;
            return true;

        } catch (Exception e) {
            host.getLogger().log(Level.WARNING,
                    "[LuckyCasino] ExcellentEconomy の直接 API 解決に失敗しました。"
                            + " Vault 経由の接続を推奨します。", e);
            return false;
        }
    }

    // ── EconomyProvider ───────────────────────────────────────────────────────

    @Override public String  getName()     { return PLUGIN_NAME + " (直接API)"; }
    @Override public boolean isAvailable() { return available; }

    @Override
    public boolean hasBalance(Player player, double amount) {
        return available && getBalance(player) >= amount;
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        if (!available) return false;
        try {
            Object user = resolveUser(player.getUniqueId());
            removeBalance.invoke(user, amount, true);
            return true;
        } catch (Exception e) {
            log(e, "withdraw");
            return false;
        }
    }

    @Override
    public boolean deposit(Player player, double amount) {
        if (!available) return false;
        try {
            Object user = resolveUser(player.getUniqueId());
            addBalance.invoke(user, amount, true);
            return true;
        } catch (Exception e) {
            log(e, "deposit");
            return false;
        }
    }

    @Override
    public double getBalance(Player player) {
        if (!available) return 0.0;
        try {
            Object user = resolveUser(player.getUniqueId());
            Object bal  = getBalance.invoke(user);
            return bal instanceof Number n ? n.doubleValue() : 0.0;
        } catch (Exception e) {
            log(e, "getBalance");
            return 0.0;
        }
    }

    @Override
    public String format(double amount) {
        return String.format("%,.2f", amount);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Object resolveUser(UUID uuid) throws Exception {
        Object ecoManager  = getEconomyManager.invoke(excellentEconomy);
        Object userManager = getUserManager.invoke(ecoManager);
        return getOrCreate.invoke(userManager, uuid);
    }

    /** Finds a method by name and exact parameter types, returns null if not found. */
    private static Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        try {
            return clazz.getMethod(name, params);
        } catch (NoSuchMethodException ignored) {
            // Try superclass / interfaces
            for (Method m : clazz.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == params.length) {
                    return m;
                }
            }
        }
        return null;
    }

    private void log(Exception e, String operation) {
        host.getLogger().log(Level.WARNING,
                "[LuckyCasino] ExcellentEconomy " + operation + " 操作中にエラーが発生しました。", e);
    }
}
