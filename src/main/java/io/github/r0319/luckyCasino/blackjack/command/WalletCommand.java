package io.github.r0319.luckyCasino.blackjack.command;

import io.github.r0319.luckyCasino.blackjack.economy.EconomyManager;
import io.github.r0319.luckyCasino.blackjack.economy.InternalEconomyProvider;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Provides wallet management and player-to-player transfers.
 *
 * <h3>/wallet</h3>
 * <pre>
 *  /wallet                         — 自分の残高を確認
 *  /wallet give &lt;player&gt; &lt;amount&gt;  — [管理者] 残高を付与
 *  /wallet take &lt;player&gt; &lt;amount&gt;  — [管理者] 残高を没収
 *  /wallet set  &lt;player&gt; &lt;amount&gt;  — [管理者] 残高を直接設定
 * </pre>
 *
 * <h3>/pay</h3>
 * <pre>
 *  /pay &lt;player&gt; &lt;amount&gt;           — 他プレイヤーへの送金
 * </pre>
 *
 * <p><b>内蔵ウォレット使用時</b>はこれらのコマンドが実際の残高操作を行います。<br>
 * <b>Vault / ExcellentEconomy 使用時</b>は {@code /wallet give/take/set} が
 * Vault の deposit/withdraw API を経由します。{@code /pay} は Vault 環境では
 * EssentialsX 等の同名コマンドに自然に委譲されるため、
 * 本コマンドは主に開発・内蔵ウォレット環境用です。
 */
public class WalletCommand implements CommandExecutor, TabCompleter {

    private static final String PERM_ADMIN = "luckycasino.wallet.admin";

    private final EconomyManager economy;

    public WalletCommand(EconomyManager economy) {
        this.economy = economy;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  /wallet
    // ─────────────────────────────────────────────────────────────────────────

    public boolean onWalletCommand(@NotNull CommandSender sender, @NotNull Command command,
                                   @NotNull String label, @NotNull String[] args) {
        if (!economy.isAvailable()) {
            sender.sendMessage("§c経済システムが利用できません。");
            return true;
        }

        // /wallet (no args) — show own balance
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§c残高確認はプレイヤーのみ使用できます。");
                return true;
            }
            double bal = economy.getBalance(player);
            player.sendMessage("§6§l── 残高 ──");
            player.sendMessage("§e" + economy.format(bal));
            return true;
        }

        // Admin subcommands
        if (!sender.hasPermission(PERM_ADMIN)) {
            sender.sendMessage("§c権限がありません。");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> adminGive(sender, args);
            case "take" -> adminTake(sender, args);
            case "set"  -> adminSet(sender, args);
            default     -> sender.sendMessage("§c使い方: /wallet [give|take|set] <プレイヤー> <金額>");
        }
        return true;
    }

    private void adminGive(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage("§c使い方: /wallet give <プレイヤー> <金額>"); return; }
        Player target = resolvePlayer(sender, args[1]);
        if (target == null) return;
        double amount = parseAmount(sender, args[2]);
        if (amount < 0) return;

        economy.adminDeposit(target, amount);
        sender.sendMessage("§a" + target.getName() + " に " + economy.format(amount) + " を付与しました。"
                + " (残高: " + economy.format(economy.getBalance(target)) + ")");
        target.sendMessage("§a管理者から " + economy.format(amount) + " を受け取りました。");
    }

    private void adminTake(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage("§c使い方: /wallet take <プレイヤー> <金額>"); return; }
        Player target = resolvePlayer(sender, args[1]);
        if (target == null) return;
        double amount = parseAmount(sender, args[2]);
        if (amount < 0) return;

        boolean ok = economy.adminWithdraw(target, amount);
        if (!ok) {
            sender.sendMessage("§c" + target.getName() + " の残高が不足しています "
                    + "(残高: " + economy.format(economy.getBalance(target)) + ")");
            return;
        }
        sender.sendMessage("§a" + target.getName() + " から " + economy.format(amount) + " を没収しました。"
                + " (残高: " + economy.format(economy.getBalance(target)) + ")");
        target.sendMessage("§c管理者に " + economy.format(amount) + " を没収されました。");
    }

    private void adminSet(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage("§c使い方: /wallet set <プレイヤー> <金額>"); return; }
        Player target = resolvePlayer(sender, args[1]);
        if (target == null) return;
        double amount = parseAmount(sender, args[2]);
        if (amount < 0) return;

        economy.adminSet(target, amount);
        sender.sendMessage("§a" + target.getName() + " の残高を " + economy.format(amount) + " に設定しました。");
        target.sendMessage("§e残高が " + economy.format(amount) + " に設定されました。");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  /pay
    // ─────────────────────────────────────────────────────────────────────────

    public boolean onPayCommand(@NotNull CommandSender sender, @NotNull Command command,
                                @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player payer)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ使用できます。");
            return true;
        }
        if (!economy.isAvailable()) {
            payer.sendMessage("§c経済システムが利用できません。");
            return true;
        }
        if (args.length < 2) {
            payer.sendMessage("§c使い方: /pay <プレイヤー> <金額>");
            return true;
        }

        Player target = resolvePlayer(payer, args[0]);
        if (target == null) return true;
        if (target.equals(payer)) {
            payer.sendMessage("§c自分自身には送金できません。");
            return true;
        }

        double amount = parseAmount(payer, args[1]);
        if (amount < 0) return true;
        if (amount == 0) { payer.sendMessage("§c金額は0より大きい値を指定してください。"); return true; }

        if (!economy.hasBalance(payer, amount)) {
            payer.sendMessage("§c残高が不足しています。 (残高: " + economy.format(economy.getBalance(payer)) + ")");
            return true;
        }

        economy.withdraw(payer, amount);
        economy.deposit(target, amount);

        payer.sendMessage("§a" + target.getName() + " に " + economy.format(amount) + " を送金しました。"
                + " §7(残高: " + economy.format(economy.getBalance(payer)) + ")");
        target.sendMessage("§a" + payer.getName() + " から " + economy.format(amount) + " を受け取りました。"
                + " §7(残高: " + economy.format(economy.getBalance(target)) + ")");
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CommandExecutor entry point (routes both /wallet and /pay)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "pay"    -> onPayCommand(sender, command, label, args);
            case "wallet" -> onWalletCommand(sender, command, label, args);
            default       -> false;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Tab completion
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);

        if (name.equals("pay")) {
            if (args.length == 1) return onlinePlayers(args[0]);
            if (args.length == 2) return List.of("100", "500", "1000");
            return List.of();
        }

        if (name.equals("wallet")) {
            if (args.length == 0) return List.of();
            if (args.length == 1) {
                List<String> subs = new java.util.ArrayList<>();
                if (sender.hasPermission(PERM_ADMIN)) subs.addAll(List.of("give", "take", "set"));
                return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
            }
            if (args.length == 2 && sender.hasPermission(PERM_ADMIN)) return onlinePlayers(args[1]);
            if (args.length == 3) return List.of("100", "500", "1000", "10000");
        }
        return List.of();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private @Nullable Player resolvePlayer(CommandSender sender, String name) {
        Player target = Bukkit.getPlayer(name);
        if (target == null) sender.sendMessage("§cプレイヤー「" + name + "」が見つかりません（オンラインのみ対応）。");
        return target;
    }

    /**
     * Parses an amount string. Returns -1 and sends an error message on failure.
     */
    private double parseAmount(CommandSender sender, String raw) {
        try {
            double v = Double.parseDouble(raw);
            if (v < 0) { sender.sendMessage("§c金額は0以上の値を指定してください。"); return -1; }
            return v;
        } catch (NumberFormatException e) {
            sender.sendMessage("§c「" + raw + "」は有効な金額ではありません。");
            return -1;
        }
    }

    private List<String> onlinePlayers(String prefix) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}
