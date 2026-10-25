package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.blackjack.Action;
import io.github.r0319.luckycasino.poker.PokerAction;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * /casino hit|stand|double|split … ブラックジャックの行動（チャットのボタンから呼ばれる）
 * /casino fold|check|call|raise [合計]|allin … ポーカーの行動
 * /casino spin … スロットを回す（チャットのボタンから呼ばれる）
 * /casino bet … 賭けのメニューを開き直す
 * /casino leave … 席を立つ
 * /casino rules [種類] … ルールブック
 * /casino dealer skin &lt;プレイヤー名&gt;|name &lt;名前&gt;|reset … 近くのテーブルのディーラーの見た目（置いた人と管理者）
 * /casino give &lt;種類&gt; [プレイヤー] … テーブルアイテムを渡す（管理者）
 * /casino remove [force] … 近くのテーブルを片付ける（管理者。座っている人がいるときは force を付けたときだけ、精算してから）
 * /casino status … 通貨と Geyser 拡張との通信状態（管理者）
 * /casino selftest &lt;種類&gt; [回数] … ボットだけで卓を回して確かめる（管理者、コンソールからも可）
 */
final class CasinoCommand implements BasicCommand {

    private static final List<String> PLAYER = List.of("hit", "stand", "double", "split", "fold", "check", "call", "raise", "allin",
            "spin", "bet", "leave", "rules", "dealer");
    private static final List<String> ADMIN = List.of("give", "remove", "status", "selftest");

    private final LuckyCasinoPaper plugin;

    CasinoCommand(LuckyCasinoPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        CommandSender sender = source.getSender();
        if (ADMIN.contains(sub)) {
            if (!sender.hasPermission(TableManager.ADMIN)) {
                sender.sendMessage(Component.text("権限がありません", NamedTextColor.RED));
                return;
            }
            admin(source, sub, args);
            return;
        }
        if (!(source.getExecutor() instanceof Player player)) {
            sender.sendMessage(Component.text("プレイヤーとして実行してください", NamedTextColor.RED));
            return;
        }
        if (sub.equals("dealer")) {
            dealer(player, args);
            return;
        }
        CasinoTable table = plugin.tables().tableOf(player.getUniqueId());
        if (sub.equals("rules")) {
            GameType type = args.length > 1 ? GameType.byId(args[1].toLowerCase(Locale.ROOT)) : null;
            Rulebook.open(player, type != null ? type : table != null ? table.type() : GameType.BLACKJACK);
            return;
        }
        if (table == null) {
            player.sendMessage(Component.text("テーブルの席に着いていません。椅子をクリックすると座れます", NamedTextColor.GRAY));
            return;
        }
        switch (sub) {
            case "bet" -> table.openMenu(player);
            case "leave" -> table.leave(player.getUniqueId());
            default -> {
                switch (table) {
                    case BlackjackTable blackjack -> blackjack(player, blackjack, sub);
                    case PokerTable poker -> poker(player, poker, sub, args);
                    case SlotMachine slot when sub.equals("spin") -> slot.spin(player);
                    default -> usage(player, table);
                }
            }
        }
    }

    private void dealer(Player player, String[] args) {
        CasinoTable table = plugin.tables().nearestWithDealer(player.getLocation(), 6);
        if (table == null) {
            player.sendMessage(Component.text("6 ブロック以内にディーラーのいるテーブルがありません", NamedTextColor.RED));
            return;
        }
        if (!table.canEdit(player)) {
            player.sendMessage(Component.text("ディーラーの見た目を変えられるのは、テーブルを置いた人と管理者だけです", NamedTextColor.RED));
            return;
        }
        String what = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        String value = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)).strip() : "";
        DealerLook look = table.dealerLook();
        switch (what) {
            case "skin" -> {
                if (!DealerLook.validSkin(value)) {
                    player.sendMessage(Component.text("プレイヤー名は英数字と _ の 3〜16 文字です", NamedTextColor.RED));
                    return;
                }
                plugin.tables().changeDealer(table, look.withSkin(value));
                player.sendMessage(Component.text("ディーラーのスキンを " + value + " にしました", NamedTextColor.GREEN));
            }
            case "name" -> {
                if (value.isEmpty() || value.codePointCount(0, value.length()) > DealerLook.MAX_NAME) {
                    player.sendMessage(Component.text("名前は 1〜" + DealerLook.MAX_NAME + " 文字です", NamedTextColor.RED));
                    return;
                }
                plugin.tables().changeDealer(table, look.withName(value));
                player.sendMessage(Component.text("ディーラーの名前を「" + value + "」にしました", NamedTextColor.GREEN));
            }
            case "reset" -> {
                plugin.tables().changeDealer(table, DealerLook.DEFAULT);
                player.sendMessage(Component.text("ディーラーの見た目を元に戻しました", NamedTextColor.GREEN));
            }
            default -> plugin.dealerMenu().open(player, table);
        }
    }

    private void blackjack(Player player, BlackjackTable table, String sub) {
        switch (sub) {
            case "hit" -> table.act(player, Action.HIT);
            case "stand" -> table.act(player, Action.STAND);
            case "double" -> table.act(player, Action.DOUBLE);
            case "split" -> table.act(player, Action.SPLIT);
            default -> usage(player, table);
        }
    }

    private void poker(Player player, PokerTable table, String sub, String[] args) {
        switch (sub) {
            case "fold" -> table.act(player, PokerAction.FOLD);
            case "check" -> table.act(player, PokerAction.CHECK);
            case "call" -> table.act(player, PokerAction.CALL);
            case "allin" -> table.allIn(player);
            case "raise" -> {
                if (args.length > 1) {
                    try {
                        table.raiseTo(player, Long.parseLong(args[1]));
                    } catch (NumberFormatException e) {
                        player.sendMessage(Component.text("額は数字で指定してください", NamedTextColor.RED));
                    }
                } else {
                    table.openRaise(player);
                }
            }
            default -> usage(player, table);
        }
    }

    private static void usage(Player player, CasinoTable table) {
        String text = switch (table.type()) {
            case BLACKJACK -> "/casino hit|stand|double|split|bet|leave|rules";
            case ROULETTE -> "/casino bet|leave|rules";
            case POKER -> "/casino fold|check|call|raise [合計]|allin|leave|rules";
            case SLOT -> "/casino spin|bet|leave|rules";
            case BACCARAT, BIGWHEEL -> "/casino bet|leave|rules";
        };
        player.sendMessage(Component.text("使い方: " + text, NamedTextColor.GRAY));
    }

    private void admin(CommandSourceStack source, String sub, String[] args) {
        CommandSender sender = source.getSender();
        switch (sub) {
            case "give" -> {
                GameType type = args.length > 1 ? GameType.byId(args[1].toLowerCase(Locale.ROOT)) : null;
                if (type == null) {
                    sender.sendMessage(Component.text("使い方: /casino give <" + typeIds() + "> [プレイヤー]", NamedTextColor.RED));
                    return;
                }
                Player target = args.length > 2 ? plugin.getServer().getPlayerExact(args[2])
                        : source.getExecutor() instanceof Player p ? p : null;
                if (target == null) {
                    sender.sendMessage(Component.text("プレイヤーが見つかりません", NamedTextColor.RED));
                    return;
                }
                target.getInventory().addItem(plugin.tableItem().create(type));
                sender.sendMessage(Component.text(target.getName() + " に" + type.itemName() + "を渡しました", NamedTextColor.GREEN));
            }
            case "remove" -> {
                if (!(source.getExecutor() instanceof Player player)) {
                    sender.sendMessage(Component.text("プレイヤーとして実行してください", NamedTextColor.RED));
                    return;
                }
                CasinoTable table = plugin.tables().nearest(player.getLocation(), 6);
                if (table == null) {
                    player.sendMessage(Component.text("6 ブロック以内にテーブルがありません", NamedTextColor.RED));
                    return;
                }
                if (!table.empty() && !(args.length > 1 && args[1].equalsIgnoreCase("force"))) {
                    player.sendMessage(Component.text("座っている人がいるので片付けません。精算して片付けるなら /casino remove force", NamedTextColor.RED));
                    return;
                }
                plugin.tables().remove(table, false);
                player.sendMessage(Component.text("テーブルを片付けました", NamedTextColor.GREEN));
            }
            case "status" -> sender.sendMessage(Component.text(
                    "Geyser 拡張: " + (plugin.bridge().isConnected() ? "接続中" : "未接続")
                            + " / 通貨: " + (plugin.bank().practice() ? "練習用（経済プラグインなし）" : "Vault")
                            + " / テーブル: " + plugin.tables().tables().size() + " 台", NamedTextColor.GRAY));
            case "selftest" -> {
                GameType type = args.length > 1 ? GameType.byId(args[1].toLowerCase(Locale.ROOT)) : null;
                if (type == null) {
                    sender.sendMessage(Component.text("使い方: /casino selftest <" + typeIds() + "> [回数]", NamedTextColor.RED));
                    return;
                }
                int rounds = 10;
                if (args.length > 2) {
                    try {
                        rounds = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Component.text("回数は数字で指定してください", NamedTextColor.RED));
                        return;
                    }
                }
                SelfTest.start(plugin, type, rounds, sender);
            }
            default -> {
            }
        }
    }

    private static String typeIds() {
        return String.join("|", Arrays.stream(GameType.values()).map(GameType::id).toList());
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("give") || sub.equals("selftest") || sub.equals("rules"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Arrays.stream(GameType.values()).map(GameType::id).filter(id -> id.startsWith(prefix)).toList();
        }
        if (args.length == 2 && sub.equals("dealer")) {
            return Stream.of("skin", "name", "reset").filter(option -> option.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length > 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>(PLAYER);
        if (source.getSender().hasPermission(TableManager.ADMIN)) {
            options.addAll(ADMIN);
        }
        return options.stream().filter(option -> option.startsWith(sub)).toList();
    }
}
