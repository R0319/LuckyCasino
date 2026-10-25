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
import org.bukkit.event.entity.EntityDamageEvent;
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
    private final Map<String, CasinoTable> tables = new LinkedHashMap<>();

    TableManager(LuckyCasinoPaper plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "tables.yml");
    }

    Collection<CasinoTable> tables() {
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
            Frame frame = new Frame(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                    (float) section.getDouble("yaw"));
            GameType type = GameType.byId(section.getString("type", GameType.BLACKJACK.id()));
            if (type == null) {
                plugin.getLogger().warning("テーブル " + id + " の種類 " + section.getString("type") + " が分からないため読み込みません");
                continue;
            }
            UUID owner = parseUuid(section.getString("owner"));
            tables.put(id, createTable(type, id, frame, owner, false));
        }
        for (CasinoTable table : tables.values()) {
            Location origin = table.frame().origin();
            if (origin.getWorld().isChunkLoaded(origin.getBlockX() >> 4, origin.getBlockZ() >> 4)) {
                table.spawn();
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (CasinoTable table : tables.values()) {
            Location origin = table.frame().origin();
            ConfigurationSection section = yaml.createSection(table.id());
            section.set("type", table.type().id());
            section.set("world", origin.getWorld().getName());
            section.set("x", origin.getX());
            section.set("y", origin.getY());
            section.set("z", origin.getZ());
            section.set("yaw", table.frame().yaw());
            section.set("owner", table.owner() == null ? null : table.owner().toString());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "tables.yml を保存できません", e);
        }
    }

    /**
     * 種類に合わせてテーブルを作る（表示はまだ出さない）。selfTest なら練習用チップで、待ち時間を短くする。
     */
    CasinoTable createTable(GameType type, String id, Frame frame, UUID owner, boolean selfTest) {
        Bank bank = selfTest ? new Bank.Practice(1000) : plugin.bank();
        return switch (type) {
            case BLACKJACK -> {
                BlackjackSettings s = plugin.blackjackSettings();
                yield new BlackjackTable(plugin, id, frame, owner, bank,
                        selfTest ? new BlackjackSettings(s.rules(), 3, 5, s.buyIns()) : s);
            }
            case ROULETTE -> {
                RouletteSettings s = plugin.rouletteSettings();
                yield new RouletteTable(plugin, id, frame, owner, bank,
                        selfTest ? new RouletteSettings(s.minBet(), s.maxBet(), s.maxTotal(), 5, s.buyIns(), s.standingRange()) : s);
            }
            case POKER -> {
                PokerSettings s = plugin.pokerSettings();
                yield new PokerTable(plugin, id, frame, owner, bank,
                        selfTest ? new PokerSettings(s.smallBlind(), s.bigBlind(), 5, 2, s.buyIns()) : s);
            }
            case SLOT -> new SlotMachine(plugin, id, frame, owner, bank, plugin.slotSettings());
        };
    }

    void despawnAll() {
        tables.values().forEach(CasinoTable::despawn);
    }

    CasinoTable tableOf(UUID player) {
        for (CasinoTable table : tables.values()) {
            if (table.seatOf(player) >= 0) {
                return table;
            }
        }
        return null;
    }

    private CasinoTable tableAt(Block block) {
        for (CasinoTable table : tables.values()) {
            if (table.contains(block)) {
                return table;
            }
        }
        return null;
    }

    CasinoTable nearest(Location location, double radius) {
        CasinoTable best = null;
        double bestDistance = radius * radius;
        for (CasinoTable table : tables.values()) {
            Location origin = table.frame().origin();
            if (origin.getWorld().equals(location.getWorld()) && origin.distanceSquared(location) <= bestDistance) {
                best = table;
                bestDistance = origin.distanceSquared(location);
            }
        }
        return best;
    }

    /** 両替メニューで選んだあと席に着かせる。ほかの卓に座っていたら断る。 */
    void sit(Player player, CasinoTable table, int seat, long chips) {
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

    private void place(Player player, Block clicked, ItemStack item, GameType type) {
        if (!player.hasPermission("luckycasino.place")) {
            player.sendMessage(Component.text("テーブルを置く権限がありません", NamedTextColor.RED));
            return;
        }
        float snapped = Math.round(player.getYaw() / 90f) * 90f;
        float yaw = ((snapped + 180f) % 360f + 360f) % 360f;
        // 1 ブロックの台（スロット）はブロックの中心、テーブルはブロックの角を中心にする
        double center = type.singleBlock() ? 0.5 : 0;
        Frame frame = new Frame(clicked.getWorld(), clicked.getX() + center, clicked.getY() + 1, clicked.getZ() + center, yaw);
        String id = UUID.randomUUID().toString().substring(0, 8);
        CasinoTable table = createTable(type, id, frame, player.getUniqueId(), false);
        TableShape shape = table.shape();

        List<Block> footprint = table.footprint();
        for (Block block : footprint) {
            if (!block.isEmpty() || (shape.needsHeadroom() && !block.getRelative(BlockFace.UP).isEmpty()) || tableAt(block) != null) {
                player.sendMessage(Component.text(shape.spaceText(), NamedTextColor.RED));
                return;
            }
        }
        for (int i = 0; i < shape.seats(); i++) {
            Block seat = frame.at(shape.seat(i)).getBlock();
            if (!seat.isPassable() || !seat.getRelative(BlockFace.UP).isPassable() || tableAt(seat) != null) {
                player.sendMessage(Component.text("椅子を置く場所が塞がっています", NamedTextColor.RED));
                return;
            }
        }
        if (shape.dealer() != null && !frame.at(shape.dealer()).getBlock().isPassable()) {
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

        tables.put(id, table);
        save();
        table.spawn();
        if (player.getGameMode() != GameMode.CREATIVE) {
            item.subtract();
        }
        player.sendMessage(Component.text(type.itemName() + "を置きました" + (plugin.bank().practice() ? "（このサーバーには経済プラグインが無いので練習用です）" : ""), NamedTextColor.GREEN));
    }

    /** 片付ける。座っている人がいれば精算してから。 */
    void remove(CasinoTable table, boolean dropItem) {
        table.despawn();
        for (Block block : table.footprint()) {
            if (block.getType() == Material.BARRIER) {
                block.setType(Material.AIR, false);
            }
        }
        tables.remove(table.id());
        save();
        if (dropItem) {
            Location at = table.frame().at(0, 1, 0);
            at.getWorld().dropItemNaturally(at, plugin.tableItem().create(table.type()));
        }
    }

    // ---- イベント ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof Interaction) || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        for (CasinoTable table : tables.values()) {
            if (table.clickFixture(event.getPlayer(), clicked)) {
                event.setCancelled(true);
                return;
            }
            int seat = table.seatOfHitbox(clicked);
            if (seat < 0) {
                continue;
            }
            event.setCancelled(true);
            Player player = event.getPlayer();
            if (table.seatOf(player.getUniqueId()) >= 0) {
                table.interact(player, true);
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
        CasinoTable table = block.getType() == Material.BARRIER ? tableAt(block) : null;
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
                table.interact(player, false);
            }
            return;
        }
        ItemStack item = event.getItem();
        GameType type = plugin.tableItem().typeOf(item);
        if (type != null) {
            event.setCancelled(true);
            if (event.getBlockFace() == BlockFace.UP) {
                place(player, block, item, type);
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

    /** ディーラーは無敵だが、クリエイティブの人は無敵でも倒せるので、ダメージそのものを止める。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        for (CasinoTable table : tables.values()) {
            if (table.isDealer(event.getEntity())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        for (CasinoTable table : tables.values()) {
            if (table.isMount(event.getDismounted())) {
                table.dismounted(player.getUniqueId());
                return;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        CasinoTable table = tableOf(event.getPlayer().getUniqueId());
        if (table != null) {
            table.leave(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (CasinoTable table : tables.values()) {
            if (!table.spawned() && inChunk(table, event.getChunk())) {
                table.spawn();
            }
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (CasinoTable table : tables.values()) {
            if (table.spawned() && inChunk(table, event.getChunk())) {
                table.despawn();
            }
        }
    }

    private static boolean inChunk(CasinoTable table, Chunk chunk) {
        Location origin = table.frame().origin();
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
