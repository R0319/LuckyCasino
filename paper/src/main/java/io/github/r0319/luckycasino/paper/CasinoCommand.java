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
 * /casino roll … チンチロで自分の番にサイコロを振る（チャットのボタンから呼ばれる）
 * /casino call|fold … カジノホールデムでコールかフォールドを選ぶ（チャットのボタンから呼ばれる）
 * /casino bet … 賭けのメニューを開き直す
 * /casino leave … 席を立つ
 * /casino rules [種類] … ルールブック
 * /casino top [種類] … 収支のランキング（上位 10 人と自分の順位。コンソールからも可）
 * /casino dealer skin &lt;プレイヤー名&gt;|name &lt;名前&gt;|reset … 近くのテーブルのディーラーの見た目（置いた人と管理者）
 * /casino give &lt;種類&gt; [プレイヤー] … テーブルアイテムを渡す（管理者）
 * /casino remove [force] … 近くのテーブルを片付ける（管理者。座っている人がいるときは force を付けたときだけ、精算してから）
 * /casino status … 通貨と Geyser 拡張との通信状態（管理者）
 * /casino selftest &lt;種類&gt; [回数] [leave] … ボットだけで卓を回して確かめる（leave で勝負の途中に席を立つ場面も）（管理者、コンソールからも可）
 */
final class CasinoCommand implements BasicCommand {

    private static final List<String> PLAYER = List.of("hit", "stand", "double", "split", "fold", "check", "call", "raise", "allin",
            "spin", "roll", "bet", "leave", "rules", "dealer", "top");
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
                sender.sendMessage(L10n.tr("権限がありません").color(NamedTextColor.RED));
                return;
            }
            admin(source, sub, args);
            return;
        }
        if (sub.equals("top")) {
            top(source, args);
            return;
        }
        if (!(source.getExecutor() instanceof Player player)) {
            sender.sendMessage(L10n.tr("プレイヤーとして実行してください").color(NamedTextColor.RED));
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
            player.sendMessage(L10n.tr("テーブルの席に着いていません。椅子をクリックすると座れます").color(NamedTextColor.GRAY));
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
                    case ChinchiroTable chinchiro when sub.equals("roll") -> chinchiro.roll(player);
                    case HoldemTable holdem when sub.equals("call") || sub.equals("fold") -> holdem.choose(player, sub.equals("call"));
                    default -> usage(player, table);
                }
            }
        }
    }

    private void dealer(Player player, String[] args) {
        CasinoTable table = plugin.tables().nearestWithDealer(player.getLocation(), 6);
        if (table == null) {
            player.sendMessage(L10n.tr("6 ブロック以内にディーラーのいるテーブルがありません").color(NamedTextColor.RED));
            return;
        }
        if (!table.canEdit(player)) {
            player.sendMessage(L10n.tr("ディーラーの見た目を変えられるのは、テーブルを置いた人と管理者だけです").color(NamedTextColor.RED));
            return;
        }
        String what = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        String value = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)).strip() : "";
        DealerLook look = table.dealerLook();
        switch (what) {
            case "skin" -> {
                if (!DealerLook.validSkin(value)) {
                    player.sendMessage(L10n.tr("プレイヤー名は英数字と _ の 3〜16 文字です").color(NamedTextColor.RED));
                    return;
                }
                plugin.tables().changeDealer(table, look.withSkin(value));
                player.sendMessage(L10n.tr("ディーラーのスキンを %s にしました", value).color(NamedTextColor.GREEN));
            }
            case "name" -> {
                if (value.isEmpty() || value.codePointCount(0, value.length()) > DealerLook.MAX_NAME) {
                    player.sendMessage(L10n.tr("名前は 1〜%s 文字です", DealerLook.MAX_NAME).color(NamedTextColor.RED));
                    return;
                }
                plugin.tables().changeDealer(table, look.withName(value));
                player.sendMessage(L10n.tr("ディーラーの名前を「%s」にしました", value).color(NamedTextColor.GREEN));
            }
            case "reset" -> {
                plugin.tables().changeDealer(table, DealerLook.DEFAULT);
                player.sendMessage(L10n.tr("ディーラーの見た目を元に戻しました").color(NamedTextColor.GREEN));
            }
            default -> plugin.dealerMenu().open(player, table);
        }
    }

    private static final int TOP = 10;

    private void top(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        GameType type = null;
        if (args.length > 1) {
            type = GameType.byId(args[1].toLowerCase(Locale.ROOT));
            if (type == null) {
                sender.sendMessage(L10n.tr("使い方: /casino top [%s]", typeIds()).color(NamedTextColor.RED));
                return;
            }
        }
        if (plugin.bank().practice()) {
            sender.sendMessage(L10n.tr("練習用チップで遊んでいるため、収支は記録していません").color(NamedTextColor.GRAY));
            return;
        }
        Ranking ranking = plugin.stats().ranking();
        Component title = type == null ? L10n.tr("全ゲーム") : L10n.tr(type.displayName());
        sender.sendMessage(L10n.tr("―― 収支ランキング（%s） ――", title).color(NamedTextColor.GOLD));
        List<Ranking.Row> rows = ranking.top(type, TOP);
        if (rows.isEmpty()) {
            sender.sendMessage(L10n.tr("まだ記録がありません。席を立つと、その回の収支が記録されます").color(NamedTextColor.GRAY));
        }
        for (Ranking.Row row : rows) {
            sender.sendMessage(Component.text(row.rank() + ". ", NamedTextColor.YELLOW)
                    .append(Component.text(row.name() + "  ", NamedTextColor.WHITE))
                    .append(net(row.net()))
                    .append(L10n.tr("（%s 回）", row.plays()).color(NamedTextColor.GRAY)));
        }
        if (source.getExecutor() instanceof Player player) {
            Ranking.Row own = ranking.of(player.getUniqueId(), type);
            sender.sendMessage(own == null
                    ? L10n.tr("あなたはまだ記録がありません").color(NamedTextColor.GRAY)
                    : L10n.tr("あなた: %s 位 / %s 人", own.rank(), ranking.size(type)).color(NamedTextColor.AQUA)
                            .append(Component.text("  ")).append(net(own.net()))
                            .append(L10n.tr("（%s 回）", own.plays()).color(NamedTextColor.GRAY)));
        }
    }

    private static Component net(long net) {
        return L10n.tr("%s 枚", Ranking.signed(net))
                .color(net > 0 ? NamedTextColor.GREEN : net < 0 ? NamedTextColor.RED : NamedTextColor.GRAY);
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
                        player.sendMessage(L10n.tr("額は数字で指定してください").color(NamedTextColor.RED));
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
            case POKER -> "/casino fold|check|call|raise [total]|allin|leave|rules";
            case SLOT -> "/casino spin|bet|leave|rules";
            case BACCARAT, BIGWHEEL, HIGHLOW, HORSERACE -> "/casino bet|leave|rules";
            case CHINCHIRO -> "/casino bet|roll|leave|rules";
            case HOLDEM -> "/casino bet|call|fold|leave|rules";
        };
        player.sendMessage(L10n.tr("使い方: %s", text).color(NamedTextColor.GRAY));
    }

    private void admin(CommandSourceStack source, String sub, String[] args) {
        CommandSender sender = source.getSender();
        switch (sub) {
            case "give" -> {
                GameType type = args.length > 1 ? GameType.byId(args[1].toLowerCase(Locale.ROOT)) : null;
                if (type == null) {
                    sender.sendMessage(L10n.tr("使い方: /casino give <%s> [プレイヤー]", typeIds()).color(NamedTextColor.RED));
                    return;
                }
                Player target = args.length > 2 ? plugin.getServer().getPlayerExact(args[2])
                        : source.getExecutor() instanceof Player p ? p : null;
                if (target == null) {
                    sender.sendMessage(L10n.tr("プレイヤーが見つかりません").color(NamedTextColor.RED));
                    return;
                }
                target.getInventory().addItem(plugin.tableItem().create(type));
                sender.sendMessage(L10n.tr("%s に%sを渡しました", target.getName(), L10n.tr(type.itemName())).color(NamedTextColor.GREEN));
            }
            case "remove" -> {
                if (!(source.getExecutor() instanceof Player player)) {
                    sender.sendMessage(L10n.tr("プレイヤーとして実行してください").color(NamedTextColor.RED));
                    return;
                }
                CasinoTable table = plugin.tables().nearest(player.getLocation(), 6);
                if (table == null) {
                    player.sendMessage(L10n.tr("6 ブロック以内にテーブルがありません").color(NamedTextColor.RED));
                    return;
                }
                if (!table.empty() && !(args.length > 1 && args[1].equalsIgnoreCase("force"))) {
                    player.sendMessage(L10n.tr("座っている人がいるので片付けません。精算して片付けるなら /casino remove force").color(NamedTextColor.RED));
                    return;
                }
                plugin.tables().remove(table, false);
                player.sendMessage(L10n.tr("テーブルを片付けました").color(NamedTextColor.GREEN));
            }
            case "status" -> sender.sendMessage(L10n.tr("Geyser 拡張: %s / 通貨: %s / テーブル: %s 台",
                    L10n.tr(plugin.bridge().isConnected() ? "接続中" : "未接続"),
                    plugin.bank().practice() ? L10n.tr("練習用（経済プラグインなし）") : "Vault",
                    plugin.tables().tables().size()).color(NamedTextColor.GRAY));
            case "selftest" -> {
                GameType type = args.length > 1 ? GameType.byId(args[1].toLowerCase(Locale.ROOT)) : null;
                if (type == null) {
                    sender.sendMessage(L10n.tr("使い方: /casino selftest <%s> [回数] [leave]", typeIds()).color(NamedTextColor.RED));
                    return;
                }
                int rounds = 10;
                if (args.length > 2) {
                    try {
                        rounds = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(L10n.tr("回数は数字で指定してください").color(NamedTextColor.RED));
                        return;
                    }
                }
                boolean leave = args.length > 3 && args[3].equalsIgnoreCase("leave");
                SelfTest.start(plugin, type, rounds, sender, leave);
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
        if (args.length == 2 && (sub.equals("give") || sub.equals("selftest") || sub.equals("rules") || sub.equals("top"))) {
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
