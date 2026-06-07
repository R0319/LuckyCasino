package io.github.r0319.luckyCasino.command;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SpawnBlockDisplayCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはプレイヤーのみ使用できます。");
            return true;
        }

        if (args.length != 4) {
            sender.sendMessage("使い方: /spawnblockdisplay <x> <y> <z> <ブロック>");
            return true;
        }

        double x, y, z;
        try {
            x = parseCoord(args[0], player.getLocation().getX());
            y = parseCoord(args[1], player.getLocation().getY());
            z = parseCoord(args[2], player.getLocation().getZ());
        } catch (NumberFormatException e) {
            sender.sendMessage("座標の値が不正です: " + e.getMessage());
            return true;
        }

        Material material = Material.matchMaterial(args[3].toUpperCase());
        if (material == null || !material.isBlock()) {
            sender.sendMessage("不明なブロック: " + args[3]);
            return true;
        }

        World world = player.getWorld();
        Location loc = new Location(world, x, y, z);
        BlockData blockData = material.createBlockData();

        world.spawn(loc, BlockDisplay.class, display -> display.setBlock(blockData));

        player.sendMessage(String.format(
            "BlockDisplay (%s) を %.2f, %.2f, %.2f に召喚しました。",
            material.name(), x, y, z
        ));
        return true;
    }

    private double parseCoord(String input, double origin) {
        if (input.startsWith("~")) {
            String rest = input.substring(1);
            return origin + (rest.isEmpty() ? 0 : Double.parseDouble(rest));
        }
        return Double.parseDouble(input);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return List.of();

        return switch (args.length) {
            case 1 -> List.of(String.valueOf((int) player.getLocation().getX()));
            case 2 -> List.of(String.valueOf((int) player.getLocation().getY()));
            case 3 -> List.of(String.valueOf((int) player.getLocation().getZ()));
            case 4 -> Arrays.stream(Material.values())
                    .filter(m -> m.isBlock() && !m.isAir()
                            && m.name().toLowerCase().startsWith(args[3].toLowerCase()))
                    .limit(20)
                    .map(m -> m.name().toLowerCase())
                    .collect(Collectors.toList());
            default -> List.of();
        };
    }
}
