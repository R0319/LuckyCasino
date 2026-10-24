package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.blackjack.Action;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * /casino hit|stand|double|split … 自分の番の行動（チャットのボタンから呼ばれる）
 * /casino bet … 賭け金メニューを開き直す
 * /casino leave … 席を立つ
 * /casino give [プレイヤー] … テーブルアイテムを渡す（管理者）
 * /casino remove … 近くのテーブルを片付ける（管理者、座っている人は精算）
 * /casino status … Geyser 拡張との通信状態（管理者）
 */
final class CasinoCommand implements BasicCommand {

    private static final List<String> PLAYER = List.of("hit", "stand", "double", "split", "bet", "leave");
    private static final List<String> ADMIN = List.of("give", "remove", "status");

    private final LuckyCasinoPaper plugin;

    CasinoCommand(LuckyCasinoPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        boolean admin = source.getSender().hasPermission(TableManager.ADMIN);
        if (ADMIN.contains(sub)) {
            if (!admin) {
                source.getSender().sendMessage(Component.text("権限がありません", NamedTextColor.RED));
                return;
            }
            admin(source, sub, args);
            return;
        }
        if (!(source.getExecutor() instanceof Player player)) {
            source.getSender().sendMessage(Component.text("プレイヤーとして実行してください", NamedTextColor.RED));
            return;
        }
        BlackjackTable table = plugin.tables().tableOf(player.getUniqueId());
        if (table == null) {
            player.sendMessage(Component.text("テーブルの席に着いていません。席をクリックすると座れます", NamedTextColor.GRAY));
            return;
        }
        switch (sub) {
            case "hit" -> table.act(player, Action.HIT);
            case "stand" -> table.act(player, Action.STAND);
            case "double" -> table.act(player, Action.DOUBLE);
            case "split" -> table.act(player, Action.SPLIT);
            case "bet" -> plugin.menus().openBet(player, table);
            case "leave" -> table.leave(player.getUniqueId());
            default -> player.sendMessage(Component.text("使い方: /casino hit|stand|double|split|bet|leave", NamedTextColor.GRAY));
        }
    }

    private void admin(CommandSourceStack source, String sub, String[] args) {
        switch (sub) {
            case "give" -> {
                Player target = args.length > 1 ? plugin.getServer().getPlayerExact(args[1])
                        : source.getExecutor() instanceof Player p ? p : null;
                if (target == null) {
                    source.getSender().sendMessage(Component.text("プレイヤーが見つかりません", NamedTextColor.RED));
                    return;
                }
                target.getInventory().addItem(plugin.tableItem().create());
                source.getSender().sendMessage(Component.text(target.getName() + " にテーブルを渡しました", NamedTextColor.GREEN));
            }
            case "remove" -> {
                if (!(source.getExecutor() instanceof Player player)) {
                    source.getSender().sendMessage(Component.text("プレイヤーとして実行してください", NamedTextColor.RED));
                    return;
                }
                BlackjackTable table = plugin.tables().nearest(player.getLocation(), 6);
                if (table == null) {
                    player.sendMessage(Component.text("6 ブロック以内にテーブルがありません", NamedTextColor.RED));
                    return;
                }
                plugin.tables().remove(table, false);
                player.sendMessage(Component.text("テーブルを片付けました", NamedTextColor.GREEN));
            }
            case "status" -> source.getSender().sendMessage(Component.text(
                    "Geyser 拡張: " + (plugin.bridge().isConnected() ? "接続中" : "未接続")
                            + " / 通貨: " + (plugin.bank().practice() ? "練習用（経済プラグインなし）" : "Vault"), NamedTextColor.GRAY));
            default -> {
            }
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length > 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>(PLAYER);
        if (source.getSender().hasPermission(TableManager.ADMIN)) {
            options.addAll(ADMIN);
        }
        String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }
}
