package io.github.r0319.luckycasino.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** テーブルの保存・読み込み、チャンクに合わせた表示の出し入れ、設置と片付け、クリックの受付。 */
final class TableManager implements Listener {

    static final String ADMIN = "luckycasino.admin";

    private final LuckyCasinoPaper plugin;
    private final File file;
    private final Map<String, BlackjackTable> tables = new LinkedHashMap<>();

    TableManager(LuckyCasinoPaper plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "tables.yml");
    }

    Collection<BlackjackTable> tables() {
        return tables.values();
    }

    void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String id : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(id);
            World world = section == null ? null : plugin.getServer().getWorld(section.getString("world", ""));
            if (world == null) {
                plugin.getLogger().warning("テーブル " + id + " のワールドが見つからないため読み込みません");
                continue;
            }
            TableLayout layout = new TableLayout(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                    (float) section.getDouble("yaw"));
            UUID owner = parseUuid(section.getString("owner"));
            tables.put(id, new BlackjackTable(plugin, id, layout, owner, plugin.bank(), plugin.tableSettings()));
        }
        for (BlackjackTable table : tables.values()) {
            Location origin = table.layout().origin();
            if (origin.getWorld().isChunkLoaded(origin.getBlockX() >> 4, origin.getBlockZ() >> 4)) {
                table.spawn();
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (BlackjackTable table : tables.values()) {
            Location origin = table.layout().origin();
            ConfigurationSection section = yaml.createSection(table.id());
            section.set("world", origin.getWorld().getName());
            section.set("x", origin.getX());
            section.set("y", origin.getY());
            section.set("z", origin.getZ());
            section.set("yaw", table.layout().yaw());
            section.set("owner", table.owner() == null ? null : table.owner().toString());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "tables.yml を保存できません", e);
        }
    }

    void despawnAll() {
        tables.values().forEach(BlackjackTable::despawn);
    }

    BlackjackTable tableOf(UUID player) {
        for (BlackjackTable table : tables.values()) {
            if (table.seatOf(player) >= 0) {
                return table;
            }
        }
        return null;
    }

    private BlackjackTable tableAt(Block block) {
        for (BlackjackTable table : tables.values()) {
            if (table.layout().world().equals(block.getWorld()) && table.layout().contains(block)) {
                return table;
            }
        }
        return null;
    }

    BlackjackTable nearest(Location location, double radius) {
        BlackjackTable best = null;
        double bestDistance = radius * radius;
        for (BlackjackTable table : tables.values()) {
            Location origin = table.layout().origin();
            if (origin.getWorld().equals(location.getWorld()) && origin.distanceSquared(location) <= bestDistance) {
                best = table;
                bestDistance = origin.distanceSquared(location);
            }
        }
        return best;
    }

    /** 両替メニューで選んだあと席に着かせる。ほかの卓に座っていたら断る。 */
    void sit(Player player, BlackjackTable table, int seat, long chips) {
        if (tableOf(player.getUniqueId()) != null) {
            player.sendMessage(Component.text("先に今の席を立ってください", NamedTextColor.RED));
            return;
        }
        if (table.seat(seat) != null) {
            player.sendMessage(Component.text("その席はもう埋まっています", NamedTextColor.RED));
            return;
        }
        table.sit(player, seat, chips);
    }

    // ---- 設置と片付け ----

    private void place(Player player, Block clicked, ItemStack item) {
        if (!player.hasPermission("luckycasino.place")) {
            player.sendMessage(Component.text("テーブルを置く権限がありません", NamedTextColor.RED));
            return;
        }
        float snapped = Math.round(player.getYaw() / 90f) * 90f;
        float yaw = ((snapped + 180f) % 360f + 360f) % 360f;
        TableLayout layout = new TableLayout(clicked.getWorld(), clicked.getX(), clicked.getY() + 1, clicked.getZ(), yaw);

        List<Block> footprint = layout.footprint();
        for (Block block : footprint) {
            if (!block.isEmpty() || !block.getRelative(BlockFace.UP).isEmpty() || tableAt(block) != null) {
                player.sendMessage(Component.text("4 x 2 ブロック、高さ 2 ブロックの空きが必要です", NamedTextColor.RED));
                return;
            }
        }
        for (int i = 0; i < TableLayout.SEATS; i++) {
            if (!layout.at(layout.seat(i)).getBlock().isPassable()) {
                player.sendMessage(Component.text("椅子を置く場所（手前）が塞がっています", NamedTextColor.RED));
                return;
            }
        }
        if (!layout.at(layout.dealer()).getBlock().isPassable()) {
            player.sendMessage(Component.text("ディーラーが立つ場所（奥）が塞がっています", NamedTextColor.RED));
            return;
        }

        // 当たり判定のバリアを置き、保護プラグイン（GriefPrevention など）に設置してよいか確かめる
        List<BlockState> replaced = new ArrayList<>();
        boolean allowed = true;
        for (Block block : footprint) {
            replaced.add(block.getState());
            block.setType(Material.BARRIER, false);
            BlockPlaceEvent check = new BlockPlaceEvent(block, replaced.getLast(), clicked, item, player, true, EquipmentSlot.HAND);
            plugin.getServer().getPluginManager().callEvent(check);
            if (check.isCancelled() || !check.canBuild()) {
                allowed = false;
                break;
            }
        }
        if (!allowed) {
            replaced.forEach(state -> state.update(true, false));
            player.sendMessage(Component.text("ここにはテーブルを置けません", NamedTextColor.RED));
            return;
        }

        String id = UUID.randomUUID().toString().substring(0, 8);
        BlackjackTable table = new BlackjackTable(plugin, id, layout, player.getUniqueId(), plugin.bank(), plugin.tableSettings());
        tables.put(id, table);
        save();
        table.spawn();
        if (player.getGameMode() != GameMode.CREATIVE) {
            item.subtract();
        }
        player.sendMessage(Component.text("ブラックジャックテーブルを置きました" + (plugin.bank().practice() ? "（このサーバーには経済プラグインが無いので練習用です）" : ""), NamedTextColor.GREEN));
    }

    /** 片付ける。座っている人がいれば精算してから。 */
    void remove(BlackjackTable table, boolean dropItem) {
        table.despawn();
        for (Block block : table.layout().footprint()) {
            if (block.getType() == Material.BARRIER) {
                block.setType(Material.AIR, false);
            }
        }
        tables.remove(table.id());
        save();
        if (dropItem) {
            Location at = table.layout().at(0, 1, 0);
            at.getWorld().dropItemNaturally(at, plugin.tableItem().create());
        }
    }

    // ---- イベント ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof Interaction) || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        for (BlackjackTable table : tables.values()) {
            int seat = table.seatOfHitbox(clicked);
            if (seat < 0) {
                continue;
            }
            event.setCancelled(true);
            Player player = event.getPlayer();
            if (table.seatOf(player.getUniqueId()) >= 0) {
                plugin.menus().openBet(player, table);
            } else if (table.seat(seat) != null) {
                player.sendMessage(Component.text("その席は " + table.seat(seat).name + " が使っています", NamedTextColor.GRAY));
            } else if (tableOf(player.getUniqueId()) != null) {
                player.sendMessage(Component.text("先に今の席を立ってください", NamedTextColor.RED));
            } else {
                plugin.menus().openBuyIn(player, table, seat);
            }
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        BlackjackTable table = block.getType() == Material.BARRIER ? tableAt(block) : null;
        if (table != null) {
            event.setCancelled(true);
            if (player.isSneaking() && (player.getUniqueId().equals(table.owner()) || player.hasPermission(ADMIN))) {
                if (!table.empty()) {
                    player.sendMessage(Component.text("座っている人がいるので片付けられません", NamedTextColor.RED));
                } else {
                    remove(table, player.getGameMode() != GameMode.CREATIVE);
                    player.sendMessage(Component.text("テーブルを片付けました", NamedTextColor.GREEN));
                }
            } else if (table.seatOf(player.getUniqueId()) >= 0) {
                plugin.menus().openBet(player, table);
            }
            return;
        }
        ItemStack item = event.getItem();
        if (plugin.tableItem().isTable(item)) {
            event.setCancelled(true);
            if (event.getBlockFace() == BlockFace.UP) {
                place(player, block, item);
            } else {
                player.sendMessage(Component.text("ブロックの上面をクリックして置いてください", NamedTextColor.GRAY));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.BARRIER && tableAt(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        for (BlackjackTable table : tables.values()) {
            if (table.isMount(event.getDismounted())) {
                table.leave(player.getUniqueId());
                return;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        BlackjackTable table = tableOf(event.getPlayer().getUniqueId());
        if (table != null) {
            table.leave(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (BlackjackTable table : tables.values()) {
            if (!table.spawned() && inChunk(table, event.getChunk())) {
                table.spawn();
            }
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (BlackjackTable table : tables.values()) {
            if (table.spawned() && inChunk(table, event.getChunk())) {
                table.despawn();
            }
        }
    }

    private static boolean inChunk(BlackjackTable table, Chunk chunk) {
        Location origin = table.layout().origin();
        return origin.getWorld().equals(chunk.getWorld())
                && origin.getBlockX() >> 4 == chunk.getX() && origin.getBlockZ() >> 4 == chunk.getZ();
    }

    private static UUID parseUuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
