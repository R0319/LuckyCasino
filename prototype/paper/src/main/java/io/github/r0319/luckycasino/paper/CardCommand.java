package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.bridge.CardCode;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /lccard deal [カード名|random] … 見ているブロックの上に裏向きで配る（例: spade_a, heart_10）
 * /lccard flip / unflip … 表に返す / 裏に戻す
 * /lccard remove … 片付ける
 * /lccard status … 通信状態
 */
final class CardCommand implements BasicCommand {

    private static final List<String> SUBCOMMANDS = List.of("deal", "flip", "unflip", "remove", "status");

    private final LuckyCasinoPaper plugin;

    CardCommand(LuckyCasinoPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public String permission() {
        return "luckycasino.prototype";
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        if (!(source.getExecutor() instanceof Player player)) {
            source.getSender().sendMessage(Component.text("プレイヤーとして実行してください", NamedTextColor.RED));
            return;
        }
        String sub = args.length == 0 ? "deal" : args[0].toLowerCase();
        PrototypeCard card = plugin.cards().get(player.getUniqueId());
        switch (sub) {
            case "deal" -> deal(player, card, args.length > 1 ? args[1].toLowerCase() : "random");
            case "flip" -> {
                if (card == null) {
                    player.sendMessage(Component.text("先に /lccard deal で配ってください", NamedTextColor.RED));
                } else {
                    card.flip();
                }
            }
            case "unflip" -> {
                if (card != null) {
                    card.unflip();
                }
            }
            case "remove" -> {
                if (card != null) {
                    card.remove();
                    plugin.cards().remove(player.getUniqueId());
                }
            }
            case "status" -> player.sendMessage(Component.text("Geyser 拡張との通信: "
                    + (plugin.bridge().isConnected() ? "接続中" : "未接続"), NamedTextColor.YELLOW));
            default -> player.sendMessage(Component.text("使い方: /lccard <deal|flip|unflip|remove|status>", NamedTextColor.RED));
        }
    }

    private void deal(Player player, PrototypeCard previous, String name) {
        int code = name.equals("random") ? ThreadLocalRandom.current().nextInt(1, CardCode.MAX + 1) : CardCode.fromModelName(name);
        if (code < 1) {
            player.sendMessage(Component.text("カード名が違います（例: spade_a, heart_10, club_k）", NamedTextColor.RED));
            return;
        }
        Block target = player.getTargetBlockExact(6);
        if (target == null) {
            player.sendMessage(Component.text("6 ブロック以内のブロックを見てください", NamedTextColor.RED));
            return;
        }
        if (previous != null) {
            previous.remove();
        }
        Location at = target.getRelative(BlockFace.UP).getLocation().add(0.5, 0.01, 0.5);
        // 絵柄の上側がプレイヤーから見て奥になる向き
        at.setYaw(player.getLocation().getYaw() + 180f);
        plugin.cards().put(player.getUniqueId(), PrototypeCard.deal(plugin, plugin.bridge(), at, code));
        player.sendMessage(Component.text(CardCode.modelName(code) + " を配りました。/lccard flip で表に返せます", NamedTextColor.GREEN));
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("deal")) {
            List<String> names = new ArrayList<>();
            names.add("random");
            for (int code = 1; code <= CardCode.MAX; code++) {
                names.add(CardCode.modelName(code));
            }
            return names.stream().filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        }
        return List.of();
    }
}
