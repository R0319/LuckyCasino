package io.github.r0319.luckyCasino.blackjack.command;

import io.github.r0319.luckyCasino.blackjack.BlackjackModule;
import io.github.r0319.luckyCasino.blackjack.table.SerializableLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Handles all {@code /bj} subcommands.
 *
 * <pre>
 *  Admin:
 *    /bj setdealer [x y z [world]]  — set dealer position (current pos or coordinates)
 *    /bj cleardealer                — remove dealer position and NPC
 *    /bj setplayer <1-4> [x y z [world]] — set a player slot position
 *    /bj clearplayer <1-4>          — remove a player slot position
 *    /bj start                      — force-start the betting phase
 *    /bj testcard                   — run a debug card animation at your location
 *    /bj info                       — show table status and slot information
 *
 *  Player:
 *    /bj join               — join the nearest table (normal slot)
 *    /bj joindealer         — join the dealer slot (spectator / human dealer)
 *    /bj leave              — leave the table (works for both normal and dealer slot)
 *    /bj bet <amount>       — place a bet (BETTING phase) or reserve for next round (WAITING)
 *    /bj hit                — draw a card (PLAYER_TURN)
 *    /bj stand              — stand (PLAYER_TURN)
 *    /bj doubledown | dd    — double down (PLAYER_TURN, 2 cards only)
 * </pre>
 */
public class BlackjackCommand implements CommandExecutor, TabCompleter {

    private static final String PERM_ADMIN  = "luckycasino.blackjack.admin";
    private static final String PERM_PLAYER = "luckycasino.blackjack.player";

    private final BlackjackModule module;

    public BlackjackCommand(BlackjackModule module) {
        this.module = module;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Dispatch
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            // Admin
            case "setdealer"              -> cmdSetDealer(sender, args);
            case "cleardealer"            -> cmdClearDealer(sender);
            case "setplayer"              -> cmdSetPlayer(sender, args);
            case "clearplayer"            -> cmdClearPlayer(sender, args);
            case "start"                  -> cmdStart(sender);
            case "testcard"               -> cmdTestCard(sender);
            case "info", "status"         -> cmdInfo(sender);
            // Player
            case "join"                   -> cmdJoin(sender);
            case "joindealer"             -> cmdJoinDealer(sender);
            case "leave"                  -> cmdLeave(sender);
            case "bet"                    -> cmdBet(sender, args);
            case "hit"                    -> cmdHit(sender);
            case "stand"                  -> cmdStand(sender);
            case "doubledown", "dd"       -> cmdDoubleDown(sender);
            default                       -> sender.sendMessage("§c不明なサブコマンド: " + args[0]
                                                + "  §7/bj で使い方を確認できます。");
        }
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Admin subcommands
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * /bj setdealer [x y z [world]]
     * Uses the player's current position if no coordinates given.
     */
    private void cmdSetDealer(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (!player.hasPermission(PERM_ADMIN)) { noPermission(sender); return; }

        if (args.length >= 4) {
            // Coordinate input: /bj setdealer x y z [world]
            Location loc = parseCoords(sender, args, 1, player);
            if (loc == null) return;
            module.setDealerLocation(loc);
            sender.sendMessage(String.format("§aディーラー位置を §2%.1f, %.1f, %.1f (%s) §aに設定しました。",
                    loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getName()));
        } else {
            // Current position
            module.setDealerLocation(player);
        }
    }

    /** /bj cleardealer */
    private void cmdClearDealer(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (!player.hasPermission(PERM_ADMIN)) { noPermission(sender); return; }
        module.clearDealerLocation(player);
    }

    /**
     * /bj setplayer <1-4> [x y z [world]]
     * Uses the player's current position if no coordinates given.
     */
    private void cmdSetPlayer(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (!player.hasPermission(PERM_ADMIN)) { noPermission(sender); return; }
        if (args.length < 2) { sender.sendMessage("§c使い方: /bj setplayer <1-4> [x y z [world]]"); return; }

        int slot = parseSlot(sender, args[1]);
        if (slot < 0) return;

        if (args.length >= 5) {
            // Coordinate input: /bj setplayer 1 x y z [world]
            // First check dealer prereq
            if (module.getTableManager().getDefaultTable().getDealerLocation() == null) {
                sender.sendMessage("§c先にディーラー位置を §e/bj setdealer §cで設定してください。");
                return;
            }
            Location loc = parseCoords(sender, args, 2, player);
            if (loc == null) return;
            module.setPlayerSlot(slot, loc);
            sender.sendMessage(String.format("§aプレイヤースロット §2%d §aを §2%.1f, %.1f, %.1f (%s) §aに設定しました。",
                    slot + 1, loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getName()));
        } else {
            // Current position
            module.setPlayerSlot(player, slot);
        }
    }

    /** /bj clearplayer <1-4> */
    private void cmdClearPlayer(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (!player.hasPermission(PERM_ADMIN)) { noPermission(sender); return; }
        if (args.length < 2) { sender.sendMessage("§c使い方: /bj clearplayer <1-4>"); return; }

        int slot = parseSlot(sender, args[1]);
        if (slot < 0) return;
        module.clearPlayerSlot(player, slot);
    }

    private void cmdStart(CommandSender sender) {
        if (!sender.hasPermission(PERM_ADMIN)) { noPermission(sender); return; }
        module.forceStart();
        sender.sendMessage("§aベッティングフェーズを開始します。");
    }

    private void cmdTestCard(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (!player.hasPermission(PERM_ADMIN)) { noPermission(sender); return; }
        module.runTestCard(player);
    }

    private void cmdInfo(CommandSender sender) {
        if (!sender.hasPermission(PERM_ADMIN) && !(sender instanceof Player p
                && p.hasPermission(PERM_PLAYER))) {
            noPermission(sender);
            return;
        }
        module.sendTableInfo(sender);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Player subcommands
    // ─────────────────────────────────────────────────────────────────────────

    private void cmdJoin(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (!player.hasPermission(PERM_PLAYER)) { noPermission(sender); return; }
        module.joinTable(player);
    }

    private void cmdJoinDealer(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (!player.hasPermission(PERM_PLAYER)) { noPermission(sender); return; }
        module.joinAsDealer(player);
    }

    private void cmdLeave(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        // leaveAny handles both normal player slot and dealer slot
        module.leaveAny(player);
    }

    private void cmdBet(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (args.length < 2) { sender.sendMessage("§c使い方: /bj bet <金額>"); return; }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c金額は数値で指定してください。");
            return;
        }
        module.placeBet(player, amount);
    }

    private void cmdHit(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        module.hit(player);
    }

    private void cmdStand(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        module.stand(player);
    }

    private void cmdDoubleDown(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        module.doubleDown(player);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Tab completion
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> all = new java.util.ArrayList<>(
                    List.of("join", "joindealer", "leave", "bet", "hit", "stand", "doubledown", "info"));
            if (sender.hasPermission(PERM_ADMIN)) {
                all.addAll(List.of("setdealer", "cleardealer", "setplayer", "clearplayer",
                        "start", "testcard"));
            }
            return all.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("setplayer")
                    || args[0].equalsIgnoreCase("clearplayer")) {
                return List.of("1", "2", "3", "4");
            }
            if (args[0].equalsIgnoreCase("bet")) {
                return List.of("100", "500", "1000");
            }
        }
        // For setdealer / setplayer, suggest coordinate hints
        if (args.length >= 2 && args.length <= 5) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("setdealer") || sub.equals("setplayer")) {
                int coordStart = sub.equals("setdealer") ? 1 : 2;
                int coordArg = args.length - coordStart;
                if (coordArg >= 1 && coordArg <= 3) {
                    return List.of("~"); // Tilde hint for relative coordinates
                }
            }
        }
        return List.of();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Parses x y z [world] arguments starting at {@code offset}.
     * Supports {@code ~} for relative coordinates (relative to the player's position).
     * Returns null and sends an error if parsing fails.
     */
    private @Nullable Location parseCoords(CommandSender sender, String[] args,
                                            int offset, @Nullable Player relative) {
        try {
            double baseX = (relative != null) ? relative.getX() : 0;
            double baseY = (relative != null) ? relative.getY() : 0;
            double baseZ = (relative != null) ? relative.getZ() : 0;

            double x = parseCoord(args[offset],     baseX);
            double y = parseCoord(args[offset + 1], baseY);
            double z = parseCoord(args[offset + 2], baseZ);

            World world;
            if (args.length > offset + 3) {
                world = Bukkit.getWorld(args[offset + 3]);
                if (world == null) {
                    sender.sendMessage("§cワールドが見つかりません: " + args[offset + 3]);
                    return null;
                }
            } else if (relative != null) {
                world = relative.getWorld();
            } else {
                sender.sendMessage("§cワールドを指定してください。");
                return null;
            }

            float yaw = (relative != null) ? relative.getYaw() : 0f;
            return new Location(world, x, y, z, yaw, 0f);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c座標は数値または ~ で指定してください。例: /bj setdealer ~ ~ ~");
            return null;
        } catch (ArrayIndexOutOfBoundsException e) {
            sender.sendMessage("§c座標が不足しています。x y z の3つを指定してください。");
            return null;
        }
    }

    /** Parses a single coordinate token.  Supports {@code ~} and {@code ~n} for relative values. */
    private double parseCoord(String token, double base) {
        if (token.equals("~")) return base;
        if (token.startsWith("~")) return base + Double.parseDouble(token.substring(1));
        return Double.parseDouble(token);
    }

    /** Parses "1"-"4" into a 0-based slot index. Returns -1 and sends an error on failure. */
    private int parseSlot(CommandSender sender, String token) {
        int slot;
        try {
            slot = Integer.parseInt(token);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cスロット番号は1〜4の整数で指定してください。");
            return -1;
        }
        if (slot < 1 || slot > 4) {
            sender.sendMessage("§cスロット番号は1〜4で指定してください。");
            return -1;
        }
        return slot - 1;
    }

    private @Nullable Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player p) return p;
        sender.sendMessage("§cこのコマンドはプレイヤーのみ使用できます。");
        return null;
    }

    private void noPermission(CommandSender sender) {
        sender.sendMessage("§c権限がありません。");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l── ブラックジャック コマンド ──");
        sender.sendMessage("§e/bj join §7— テーブルに参加");
        sender.sendMessage("§e/bj joindealer §7— ディーラーポジションに参加");
        sender.sendMessage("§e/bj leave §7— テーブルを離れる");
        sender.sendMessage("§e/bj bet <金額> §7— ベット（待機中は予約ベット）");
        sender.sendMessage("§e/bj hit §7— カードを引く");
        sender.sendMessage("§e/bj stand §7— スタンド");
        sender.sendMessage("§e/bj doubledown §7— ダブルダウン");
        sender.sendMessage("§e/bj info §7— テーブル情報を表示");
        if (sender.hasPermission(PERM_ADMIN)) {
            sender.sendMessage("§e/bj setdealer [x y z] §7— ディーラー位置を設定");
            sender.sendMessage("§e/bj cleardealer §7— ディーラー位置を削除");
            sender.sendMessage("§e/bj setplayer <1-4> [x y z] §7— プレイヤースロット位置を設定");
            sender.sendMessage("§e/bj clearplayer <1-4> §7— プレイヤースロット位置を削除");
            sender.sendMessage("§e/bj start §7— ゲームを強制開始");
            sender.sendMessage("§e/bj testcard §7— カードアニメーションのテスト表示");
        }
    }
}
