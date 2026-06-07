package io.github.r0319.luckyCasino.blackjack;

import io.github.r0319.luckyCasino.blackjack.animation.CardAnimationManager;
import io.github.r0319.luckyCasino.blackjack.command.BlackjackCommand;
import io.github.r0319.luckyCasino.blackjack.command.WalletCommand;
import io.github.r0319.luckyCasino.blackjack.economy.EconomyManager;
import io.github.r0319.luckyCasino.blackjack.game.BlackjackGame;
import io.github.r0319.luckyCasino.blackjack.player.BlackjackPlayer;
import io.github.r0319.luckyCasino.blackjack.table.BlackjackTable;
import io.github.r0319.luckyCasino.blackjack.table.SerializableLocation;
import io.github.r0319.luckyCasino.blackjack.table.TableManager;
import io.github.r0319.luckyCasino.blackjack.table.TableState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Entry point for the blackjack sub-system.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Instantiate in {@code onEnable} after Vault is available.</li>
 *   <li>Call {@link #registerCommands()} to bind commands.</li>
 *   <li>Call {@link #shutdown()} in {@code onDisable}.</li>
 * </ol>
 *
 * <h3>Dealer NPC</h3>
 * When a dealer location is set and no human dealer is present, a NoAI
 * {@link Villager} is automatically spawned at the dealer position as a
 * visual stand-in.  Use {@code /bj joindealer} to replace it with a real
 * player, and {@code /bj leavedealer} (or {@code /bj leave}) to restore it.
 * The NPC is tagged with {@link #DEALER_NPC_KEY} so it can be found and
 * removed after a server restart even if the chunk was unloaded at startup.
 *
 * <h3>Slot markers</h3>
 * Quarter-scale wool {@link BlockDisplay} entities are placed at each
 * configured player slot so admins can see where they are in-world.
 * They are NOT persistent (chunk-unload removes them) and are re-spawned
 * fresh on every startup.
 *
 * <h3>Player protections</h3>
 * Players who join (normal slot or dealer) have their walk speed set to 0
 * and invulnerability enabled.  A {@code PlayerMoveEvent} listener (registered
 * in {@link io.github.r0319.luckyCasino.blackjack.listener.BlackjackListener})
 * also cancels block-position changes so players stay in their assigned spot.
 * Both effects are removed on {@code /bj leave}.
 *
 * <h3>Zombie-game prevention</h3>
 * When the last player leaves or is kicked mid-game, {@link #cancelActiveGame()}
 * calls {@link BlackjackGame#cancel()} before nulling the reference so all
 * in-flight scheduler tasks become no-ops.
 */
public class BlackjackModule {

    /** Ticks before the betting phase starts after the first player joins (30 s). */
    private static final long GAME_START_DELAY_TICKS = 600L;
    /** Epoch-ms when startTimer fires.  -1 while not scheduled. */
    private long startTimerFiresAt = -1L;

    /** Wool colors used for slot markers (one per slot, cycled). */
    private static final Material[] SLOT_COLORS = {
        Material.WHITE_WOOL,
        Material.LIME_WOOL,
        Material.YELLOW_WOOL,
        Material.LIGHT_BLUE_WOOL
    };

    // ── Core sub-systems ─────────────────────────────────────────────────────
    private final JavaPlugin plugin;
    private final TableManager tableManager;
    private final EconomyManager economyManager;
    private final CardAnimationManager animManager;

    // ── Game state ───────────────────────────────────────────────────────────
    /** The currently running game, or null when no game is active. */
    private BlackjackGame activeGame;
    /** Scheduled task that triggers the betting phase after the join timeout. */
    private BukkitTask startTimer;

    // ── Dealer management ────────────────────────────────────────────────────
    /** UUID of the player currently in the dealer slot, or null for NPC mode. */
    private UUID dealerPlayerUuid = null;
    /** The auto-spawned NoAI villager dealer; null when a human dealer is present. */
    private Villager dealerNpc = null;
    /** PersistentData key used to tag dealer NPC entities for cross-restart cleanup. */
    private final NamespacedKey DEALER_NPC_KEY;

    // ── Slot markers ─────────────────────────────────────────────────────────
    /** Maps slot index → active BlockDisplay marker. */
    private final Map<Integer, BlockDisplay> slotMarkers = new HashMap<>();
    /** PersistentData key used to tag slot marker entities (for crash-recovery cleanup). */
    private final NamespacedKey SLOT_MARKER_KEY;

    // ── Player protections ───────────────────────────────────────────────────
    /** UUIDs of players currently frozen at their seats (walk speed = 0, invulnerable). */
    private final Set<UUID> frozenPlayers = new HashSet<>();

    // ─────────────────────────────────────────────────────────────────────────
    //  Constructor / init
    // ─────────────────────────────────────────────────────────────────────────

    public BlackjackModule(JavaPlugin plugin) {
        this.plugin         = plugin;
        this.tableManager   = new TableManager(plugin);
        this.economyManager = new EconomyManager();
        this.animManager    = new CardAnimationManager(plugin);

        DEALER_NPC_KEY = new NamespacedKey(plugin, "dealer_npc");
        SLOT_MARKER_KEY = new NamespacedKey(plugin, "slot_marker");

        if (!economyManager.setup(plugin)) {
            plugin.getLogger().warning(
                    "[LuckyCasino] Vault / Economy プロバイダが見つかりません。経済機能は無効です。");
        }

        // Remove any NPCs/markers left from a previous server session, then
        // spawn fresh ones.  Chunks must be loaded first so persisted entities
        // are visible to the world scan.
        removeStaleNpcDealers();
        spawnNpcDealerIfNeeded();

        removeStaleSlotMarkers(); // cleans up markers persisted from a previous session
        spawnAllSlotMarkers();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Command registration
    // ─────────────────────────────────────────────────────────────────────────

    /** Registers {@code /bj}, {@code /wallet}, and {@code /pay} commands with Paper. */
    public void registerCommands() {
        BlackjackCommand bjHandler = new BlackjackCommand(this);
        var bjCmd = plugin.getCommand("bj");
        if (bjCmd == null) {
            plugin.getLogger().severe("[LuckyCasino] plugin.yml に 'bj' コマンドが定義されていません。");
        } else {
            bjCmd.setExecutor(bjHandler);
            bjCmd.setTabCompleter(bjHandler);
        }

        WalletCommand walletHandler = new WalletCommand(economyManager);

        var walletCmd = plugin.getCommand("wallet");
        if (walletCmd != null) {
            walletCmd.setExecutor(walletHandler);
            walletCmd.setTabCompleter(walletHandler);
        }

        var payCmd = plugin.getCommand("pay");
        if (payCmd != null) {
            payCmd.setExecutor(walletHandler);
            payCmd.setTabCompleter(walletHandler);
            if (!economyManager.isUsingInternalWallet()) {
                plugin.getLogger().info(
                        "[LuckyCasino] 外部経済プラグインを検出しました。"
                        + " /pay は外部プラグインが優先されます。"
                        + " 内蔵 /pay は /luckycasino:pay で使用可能です。");
            }
        }
    }

    /** Call from {@code onDisable} to cancel pending tasks and remove NPCs / markers. */
    public void shutdown() {
        cancelStartTimer();
        cancelActiveGame();
        removeNpcDealer();
        removeAllSlotMarkers();
        // Restore protections for any still-frozen players (in case of /reload)
        for (UUID uuid : new HashSet<>(frozenPlayers)) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null) restorePlayerState(p);
        }
        frozenPlayers.clear();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Admin: table setup
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sets the dealer standing location to the admin player's current position,
     * saves the table, and respawns the NPC at the new location.
     */
    public void setDealerLocation(Player admin) {
        setDealerLocation(admin.getLocation());
        admin.sendMessage(String.format("§aディーラー位置を §2%s §aに設定しました。",
                locString(new SerializableLocation(admin.getLocation()))));
    }

    /**
     * Sets the dealer location to an explicit {@link Location} (coordinate input variant).
     * Saves the table and respawns the NPC.
     */
    public void setDealerLocation(Location loc) {
        BlackjackTable table = tableManager.getDefaultTable();
        SerializableLocation sl = new SerializableLocation(loc);
        table.setDealerLocation(sl);
        tableManager.save(table);
        plugin.getLogger().info("[LuckyCasino] ディーラー位置設定: " + sl);
        removeNpcDealer();
        spawnNpcDealerIfNeeded();
    }

    /**
     * Sets player slot {@code slotIndex} (0-based) to the admin's current position,
     * saves the table, and respawns the slot marker at the new location.
     */
    public void setPlayerSlot(Player admin, int slotIndex) {
        if (!checkSlotPrereqs(admin, slotIndex)) return;
        setPlayerSlot(slotIndex, admin.getLocation());
        admin.sendMessage(String.format("§aプレイヤースロット §2%d §aの位置を §2%s §aに設定しました。",
                slotIndex + 1, locString(new SerializableLocation(admin.getLocation()))));
    }

    /**
     * Sets player slot {@code slotIndex} (0-based) to an explicit {@link Location}
     * (coordinate input variant).  Does NOT check prerequisites — caller must validate.
     */
    public void setPlayerSlot(int slotIndex, Location loc) {
        BlackjackTable table = tableManager.getDefaultTable();
        SerializableLocation sl = new SerializableLocation(loc);
        table.setPlayerLocation(slotIndex, sl);
        tableManager.save(table);
        spawnSlotMarker(slotIndex, sl.toLocation());
        plugin.getLogger().info("[LuckyCasino] スロット " + (slotIndex + 1) + " 設定: " + sl);
    }

    private boolean checkSlotPrereqs(Player admin, int slotIndex) {
        BlackjackTable table = tableManager.getDefaultTable();
        SerializableLocation[] locs = table.getPlayerLocations();
        if (slotIndex < 0 || slotIndex >= locs.length) {
            admin.sendMessage("§cスロット番号が無効です。1-4 を指定してください。");
            return false;
        }
        if (table.getDealerLocation() == null) {
            admin.sendMessage("§c先にディーラー位置を §e/bj setdealer §cで設定してください。");
            return false;
        }
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Admin: clear positions
    // ─────────────────────────────────────────────────────────────────────────

    /** Removes the dealer location (and its NPC). */
    public void clearDealerLocation(Player admin) {
        BlackjackTable table = tableManager.getDefaultTable();
        table.setDealerLocation(null);
        tableManager.save(table);
        removeNpcDealer(); // stage-1 (tracked ref) + stage-2 (world scan)
        admin.sendMessage("§aディーラー位置を削除しました。");
        plugin.getLogger().info("[LuckyCasino] ディーラー位置削除");
    }

    /** Removes a player slot location (0-based index) and its marker. */
    public void clearPlayerSlot(Player admin, int slotIndex) {
        BlackjackTable table = tableManager.getDefaultTable();
        SerializableLocation[] locs = table.getPlayerLocations();
        if (slotIndex < 0 || slotIndex >= locs.length) {
            admin.sendMessage("§cスロット番号が無効です。1-4 を指定してください。");
            return;
        }
        if (locs[slotIndex] == null) {
            admin.sendMessage("§cスロット " + (slotIndex + 1) + " は設定されていません。");
            return;
        }
        // NOTE: removeSlotMarker must be called BEFORE nulling the location so the
        // world-scan stage can still load the correct chunk using the saved coords.
        removeSlotMarker(slotIndex); // stage-1 (tracked ref) + stage-2 (chunk scan)
        table.setPlayerLocation(slotIndex, null);
        tableManager.save(table);
        admin.sendMessage("§aプレイヤースロット §2" + (slotIndex + 1) + " §aを削除しました。");
        plugin.getLogger().info("[LuckyCasino] スロット " + (slotIndex + 1) + " 削除");
    }

    /**
     * Clears ALL table positions (dealer + all player slots) and removes
     * all associated entities (NPC + markers).
     */
    public void clearTable(Player admin) {
        BlackjackTable table = tableManager.getDefaultTable();

        // Remove NPC + all slot markers (world scan included)
        removeNpcDealer();
        removeAllSlotMarkers();

        // Clear all positions in the table data
        table.setDealerLocation(null);
        for (int i = 0; i < 4; i++) {
            table.setPlayerLocation(i, null);
        }
        tableManager.save(table);

        // Cancel any in-progress game
        cancelStartTimer();
        cancelActiveGame();

        admin.sendMessage("§aテーブルの全設定（ディーラー位置・全スロット）を削除しました。");
        plugin.getLogger().info("[LuckyCasino] テーブル全設定削除");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Admin: table info
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sends a formatted table-information summary to the given sender.
     * Shows state, dealer/player slot locations, active players, and countdown.
     */
    public void sendTableInfo(org.bukkit.command.CommandSender sender) {
        BlackjackTable table = tableManager.getDefaultTable();
        sender.sendMessage("§6§l══ ブラックジャックテーブル情報 ══");
        sender.sendMessage("§eID: §f" + table.getTableId());
        sender.sendMessage("§e状態: §f" + stateLabel(table.getState()));

        // Countdown
        long secsStart = (startTimerFiresAt > 0)
                ? Math.max(0L, (startTimerFiresAt - System.currentTimeMillis()) / 1000L) : -1L;
        long secsNext  = (activeGame != null) ? activeGame.getSecondsUntilNextRound() : -1L;
        if (secsStart >= 0) {
            sender.sendMessage("§eゲーム開始まで: §f" + secsStart + "秒");
        } else if (secsNext >= 0) {
            sender.sendMessage("§e次ラウンドまで: §f" + secsNext + "秒");
        }

        // Dealer
        if (table.getDealerLocation() != null) {
            String dealerInfo = locString(table.getDealerLocation());
            if (dealerPlayerUuid != null) {
                Player dp = plugin.getServer().getPlayer(dealerPlayerUuid);
                dealerInfo += " §a(" + (dp != null ? dp.getName() : "オフライン") + ")";
            } else {
                dealerInfo += " §7(NPC)";
            }
            sender.sendMessage("§eディーラー位置: §f" + dealerInfo);
        } else {
            sender.sendMessage("§eディーラー位置: §c未設定");
        }

        // Player slots
        sender.sendMessage("§eプレイヤースロット:");
        SerializableLocation[] locs = table.getPlayerLocations();
        for (int i = 0; i < locs.length; i++) {
            final int slot = i; // effectively final for lambdas
            String line = "  §7スロット" + (slot + 1) + ": ";
            if (locs[slot] == null) {
                line += "§c未設定";
            } else {
                line += "§f" + locString(locs[slot]);
                if (activeGame != null) {
                    BlackjackPlayer occupant = activeGame.getPlayers().stream()
                            .filter(bp -> bp.getSlot() == slot)
                            .findFirst().orElse(null);
                    if (occupant != null) {
                        Player p = plugin.getServer().getPlayer(occupant.getUuid());
                        String pname = (p != null) ? p.getName() : occupant.getName();
                        double reserved = activeGame.getReservedBet(occupant.getUuid());
                        String reservedStr = reserved > 0
                                ? " §7予約:" + economyManager.format(reserved) : "";
                        line += " §a(" + pname + reservedStr + "§a)";
                    } else {
                        line += " §7(空き)";
                    }
                } else {
                    line += " §7(空き)";
                }
            }
            sender.sendMessage(line);
        }

        // Active player count
        int activePlayers = (activeGame != null) ? activeGame.getPlayers().size() : 0;
        sender.sendMessage("§eアクティブプレイヤー: §f" + activePlayers + "人");
    }

    private static String stateLabel(TableState s) {
        return switch (s) {
            case WAITING    -> "§a待機中";
            case BETTING    -> "§eベット中";
            case DEALING    -> "§bディール中";
            case PLAYER_TURN-> "§dプレイヤーターン";
            case DEALER_TURN-> "§6ディーラーターン";
            case SETTLEMENT -> "§c精算中";
        };
    }

    private static String locString(SerializableLocation sl) {
        return String.format("%.1f, %.1f, %.1f (%s)", sl.getX(), sl.getY(), sl.getZ(), sl.getWorld());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Player-facing actions
    // ─────────────────────────────────────────────────────────────────────────

    /** Join a normal player slot. */
    public void joinTable(Player player) {
        BlackjackTable table = tableManager.getDefaultTable();

        if (table.getDealerLocation() == null) {
            player.sendMessage("§cテーブルがまだセットアップされていません。"
                    + " §e/bj setdealer §cを先に実行してください。");
            return;
        }
        if (table.configuredSlots() == 0) {
            player.sendMessage("§cプレイヤースロットが設定されていません。"
                    + " §e/bj setplayer <1-4> §cを先に実行してください。");
            return;
        }

        // Create game object if this is the first player after a full reset
        if (activeGame == null) {
            activeGame = createGame(table);
        }

        if (table.getState() != TableState.WAITING) {
            player.sendMessage("§cゲームはすでに進行中です。次のラウンドをお待ちください。");
            return;
        }
        if (activeGame.isPlayerInGame(player.getUniqueId())) {
            player.sendMessage("§cすでにテーブルに参加しています。");
            return;
        }

        int slot = activeGame.getFreeSlot();
        if (slot == -1) {
            player.sendMessage("§c空きスロットがありません（最大4名）。");
            return;
        }

        boolean added = activeGame.addPlayer(player, slot);
        if (!added) return;

        // Teleport and protect
        var slotLoc = table.getPlayerLocations()[slot];
        if (slotLoc != null) {
            player.teleport(slotLoc.toLocation());
        }
        applyPlayerProtections(player);

        // Start countdown on first join; skip if a /bj start or auto-restart is already scheduled
        if (activeGame.getPlayers().size() == 1) {
            scheduleGameStart();
        }
        // If all configured slots are filled, start immediately
        if (activeGame.getPlayers().size() >= table.configuredSlots()
                && table.configuredSlots() >= 1) {
            cancelStartTimer();
            activeGame.startBetting();
        }
    }

    /** Join the dealer slot (human dealer / spectator role). */
    public void joinAsDealer(Player player) {
        if (dealerPlayerUuid != null) {
            Player current = plugin.getServer().getPlayer(dealerPlayerUuid);
            String name = (current != null) ? current.getName() : "（オフライン）";
            player.sendMessage("§cすでに " + name + " がディーラーポジションにいます。");
            return;
        }

        BlackjackTable table = tableManager.getDefaultTable();
        if (table.getDealerLocation() == null) {
            player.sendMessage("§cディーラー位置が設定されていません。"
                    + " §e/bj setdealer §cで設定してください。");
            return;
        }

        dealerPlayerUuid = player.getUniqueId();
        removeNpcDealer(); // NPC is replaced by the human

        Location loc = table.getDealerLocation().toLocation();
        player.teleport(loc);
        applyPlayerProtections(player);

        player.sendMessage("§aディーラーポジションに参加しました。 §7/bj leave で離れられます。");
        if (activeGame != null) {
            activeGame.broadcast("§a" + player.getName() + " がディーラーとして参加しました。");
        }
    }

    /** Leave either a normal player slot or the dealer slot. */
    public void leaveAny(Player player) {
        if (player.getUniqueId().equals(dealerPlayerUuid)) {
            leaveDealerSlot(player);
        } else {
            leaveTable(player);
        }
    }

    public void leaveTable(Player player) {
        if (activeGame == null || !activeGame.isPlayerInGame(player.getUniqueId())) {
            player.sendMessage("§cテーブルに参加していません。");
            return;
        }
        activeGame.removePlayer(player.getUniqueId());
        removePlayerProtections(player);
        player.sendMessage("§7テーブルを離れました。");

        if (activeGame.getPlayers().isEmpty()) {
            cancelStartTimer();
            cancelActiveGame();
        }
    }

    private void leaveDealerSlot(Player player) {
        dealerPlayerUuid = null;
        removePlayerProtections(player);
        player.sendMessage("§7ディーラーポジションを離れました。");
        if (activeGame != null) {
            activeGame.broadcast("§7" + player.getName() + " がディーラーポジションを離れました。");
        }
        spawnNpcDealerIfNeeded(); // NPC comes back
    }

    public void placeBet(Player player, double amount) {
        if (!requireActiveGame(player)) return;
        activeGame.placeBet(player, amount);
    }

    public void hit(Player player) {
        if (!requireActiveGame(player)) return;
        activeGame.hit(player);
    }

    public void stand(Player player) {
        if (!requireActiveGame(player)) return;
        activeGame.stand(player);
    }

    public void doubleDown(Player player) {
        if (!requireActiveGame(player)) return;
        activeGame.doubleDown(player);
    }

    /** Admin: cancel the join timer and immediately enter the betting phase. */
    public void forceStart() {
        cancelStartTimer();
        if (activeGame != null && !activeGame.getPlayers().isEmpty()
                && activeGame.getTable().getState() == TableState.WAITING) {
            activeGame.startBetting();
        }
    }

    /** Run the debug card animation at the given player's location. */
    public void runTestCard(Player player) {
        animManager.runTestAnimation(player);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Player protection helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Freezes the player in place and grants invulnerability.
     * Call when a player joins a seat (normal or dealer).
     */
    public void applyPlayerProtections(Player player) {
        frozenPlayers.add(player.getUniqueId());
        player.setWalkSpeed(0f);
        player.setInvulnerable(true);
    }

    /**
     * Restores the player's walk speed and removes invulnerability.
     * Call when a player leaves a seat.
     */
    public void removePlayerProtections(Player player) {
        frozenPlayers.remove(player.getUniqueId());
        restorePlayerState(player);
    }

    private void restorePlayerState(Player player) {
        player.setWalkSpeed(0.2f); // Bukkit default
        player.setInvulnerable(false);
    }

    /** Returns true if this player is currently frozen at a table seat. */
    public boolean isFrozen(Player player) {
        return frozenPlayers.contains(player.getUniqueId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Disconnect handling
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called by {@link io.github.r0319.luckyCasino.blackjack.listener.BlackjackListener}
     * when a player disconnects.  Cleans up game state without trying to send
     * messages to the now-offline player.
     */
    public void handlePlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();

        // Remove from normal game slot
        if (activeGame != null && activeGame.isPlayerInGame(uuid)) {
            activeGame.removePlayer(uuid);
            if (activeGame.getPlayers().isEmpty()) {
                cancelStartTimer();
                cancelActiveGame();
            }
        }

        // Remove from dealer slot
        if (uuid.equals(dealerPlayerUuid)) {
            dealerPlayerUuid = null;
            spawnNpcDealerIfNeeded();
        }

        // Always clean up protections
        frozenPlayers.remove(uuid);
        // Note: walk speed and invulnerability are already reset on login
        // (Minecraft resets them), but call restore anyway for /reload safety
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Dealer NPC management
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Spawns a NoAI villager at the dealer location if:
     * <ul>
     *   <li>a dealer location is configured,</li>
     *   <li>no human player is in the dealer slot, and</li>
     *   <li>no NPC is currently alive.</li>
     * </ul>
     * <p>
     * The dealer location chunk is force-loaded before spawning so that any
     * previously persisted NPC (surviving a server crash) is already loaded
     * into memory and won't cause a duplicate after this call.
     * (Stale NPCs are removed by {@link #removeStaleNpcDealers()} first.)
     */
    public void spawnNpcDealerIfNeeded() {
        BlackjackTable table = tableManager.getDefaultTable();
        if (table.getDealerLocation() == null) return;
        if (dealerPlayerUuid != null)           return; // human dealer present
        if (dealerNpc != null && !dealerNpc.isDead()) return;

        Location loc = table.getDealerLocation().toLocation();
        if (loc.getWorld() == null) return;

        dealerNpc = loc.getWorld().spawn(loc, Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setCustomName("§6ディーラー");
            v.setCustomNameVisible(true);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.getPersistentDataContainer().set(DEALER_NPC_KEY, PersistentDataType.BYTE, (byte) 1);
        });
        plugin.getLogger().info("[LuckyCasino] ディーラーNPCをスポーンしました。");
    }

    /**
     * Removes the dealer NPC completely.
     * <p>
     * Two-stage removal:
     * <ol>
     *   <li>Remove the tracked {@link #dealerNpc} Java reference (fast path).</li>
     *   <li>Load the dealer chunk and scan ALL worlds for any remaining tagged
     *       Villagers — handles the case where the Java reference is stale or where
     *       a persisted NPC from a previous session was not cleaned up on startup.</li>
     * </ol>
     */
    private void removeNpcDealer() {
        // Stage 1: tracked reference
        if (dealerNpc != null) {
            if (!dealerNpc.isDead()) dealerNpc.remove();
            dealerNpc = null;
        }

        // Stage 2: world scan (belt-and-suspenders — removes any stale/duplicate NPCs)
        BlackjackTable table = tableManager.getDefaultTable();
        if (table.getDealerLocation() != null) {
            Location loc = table.getDealerLocation().toLocation();
            if (loc.getWorld() != null) loc.getChunk().load(true);
        }
        for (org.bukkit.World w : plugin.getServer().getWorlds()) {
            for (Entity e : new ArrayList<>(w.getEntitiesByClass(Villager.class))) {
                if (e.getPersistentDataContainer().has(DEALER_NPC_KEY, PersistentDataType.BYTE)) {
                    e.remove();
                }
            }
        }
    }

    /**
     * Searches for villagers tagged with {@link #DEALER_NPC_KEY} and removes them.
     * Called once on startup to clean up NPCs persisted from a previous session.
     * <p>
     * The dealer location chunk is force-loaded first so the stored NPC entity is
     * brought into memory before the world scan — without this, the entity would
     * remain unloaded, wouldn't be found, and a second NPC would be spawned next
     * to it when the chunk later loaded.
     */
    private void removeStaleNpcDealers() {
        // Force-load the dealer chunk so persisted entities become visible
        BlackjackTable table = tableManager.getDefaultTable();
        if (table.getDealerLocation() != null) {
            Location loc = table.getDealerLocation().toLocation();
            if (loc.getWorld() != null) {
                loc.getChunk().load(true);
            }
        }

        int removed = 0;
        for (org.bukkit.World w : plugin.getServer().getWorlds()) {
            for (Entity e : w.getEntitiesByClass(Villager.class)) {
                if (e.getPersistentDataContainer().has(DEALER_NPC_KEY)) {
                    e.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("[LuckyCasino] 古いディーラーNPC " + removed + " 体を削除しました。");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Slot marker management
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Spawns (or replaces) the visual wool marker for the given slot.
     * Markers are 1/4-scale {@link BlockDisplay} entities placed at the slot
     * location so admins can see the seat positions in-world.
     */
    private void spawnSlotMarker(int slotIndex, Location loc) {
        // Remove old marker first
        removeSlotMarker(slotIndex);
        if (loc.getWorld() == null) return;

        Material wool = SLOT_COLORS[slotIndex % SLOT_COLORS.length];

        // Quarter-scale block, centered horizontally (-0.125 on X and Z)
        Transformation transform = new Transformation(
                new Vector3f(-0.125f, 0f, -0.125f),    // translation (center the tiny block)
                new AxisAngle4f(0f, 0f, 1f, 0f),       // left rotation  (none)
                new Vector3f(0.25f,  0.25f, 0.25f),    // scale (1/4)
                new AxisAngle4f(0f, 0f, 1f, 0f)        // right rotation (none)
        );

        BlockDisplay bd = loc.getWorld().spawn(loc, BlockDisplay.class, d -> {
            d.setBlock(wool.createBlockData());
            d.setTransformation(transform);
            d.setBrightness(new Display.Brightness(15, 15)); // Always fully lit
            // NOT persistent: chunk-unload removes it. Re-spawned on startup.
            d.getPersistentDataContainer().set(SLOT_MARKER_KEY, PersistentDataType.INTEGER, slotIndex);
        });
        slotMarkers.put(slotIndex, bd);
        plugin.getLogger().info("[LuckyCasino] スロット " + (slotIndex + 1) + " マーカーをスポーンしました。");
    }

    /**
     * Removes the slot marker for a specific slot index.
     * <p>
     * Two-stage: tracked Java reference first, then a world scan of the slot's
     * chunk to catch any stale/persisted entities the map didn't know about.
     */
    private void removeSlotMarker(int slotIndex) {
        // Stage 1: tracked reference
        BlockDisplay old = slotMarkers.remove(slotIndex);
        if (old != null && !old.isDead()) old.remove();

        // Stage 2: world scan of the slot's chunk
        BlackjackTable table = tableManager.getDefaultTable();
        SerializableLocation[] locs = table.getPlayerLocations();
        if (slotIndex >= 0 && slotIndex < locs.length && locs[slotIndex] != null) {
            Location loc = locs[slotIndex].toLocation();
            if (loc.getWorld() != null) {
                loc.getChunk().load(true);
                for (Entity e : java.util.Arrays.asList(loc.getChunk().getEntities())) {
                    if (e instanceof BlockDisplay) {
                        Integer tag = e.getPersistentDataContainer()
                                .get(SLOT_MARKER_KEY, PersistentDataType.INTEGER);
                        if (tag != null && tag == slotIndex) e.remove();
                    }
                }
            }
        }
    }

    /** Removes all managed slot markers from the world. */
    private void removeAllSlotMarkers() {
        // Collect all slot indices (0-3) regardless of map contents
        for (int i = 0; i < 4; i++) {
            removeSlotMarker(i);
        }
        slotMarkers.clear();
    }

    /** Spawns markers for every currently configured player slot. */
    private void spawnAllSlotMarkers() {
        BlackjackTable table = tableManager.getDefaultTable();
        SerializableLocation[] locs = table.getPlayerLocations();
        for (int i = 0; i < locs.length; i++) {
            if (locs[i] != null) {
                spawnSlotMarker(i, locs[i].toLocation());
            }
        }
    }

    /**
     * Scans all player slot chunks and removes any {@link BlockDisplay} entities
     * tagged with {@link #SLOT_MARKER_KEY} that survived from a previous session.
     * Call once on startup before {@link #spawnAllSlotMarkers()}.
     */
    private void removeStaleSlotMarkers() {
        BlackjackTable table = tableManager.getDefaultTable();
        SerializableLocation[] locs = table.getPlayerLocations();

        // Force-load all player slot chunks so persisted markers are visible
        for (SerializableLocation sl : locs) {
            if (sl != null) {
                Location loc = sl.toLocation();
                if (loc.getWorld() != null) loc.getChunk().load(true);
            }
        }

        int removed = 0;
        for (org.bukkit.World w : plugin.getServer().getWorlds()) {
            for (Entity e : new ArrayList<>(w.getEntitiesByClass(BlockDisplay.class))) {
                if (e.getPersistentDataContainer().has(SLOT_MARKER_KEY, PersistentDataType.INTEGER)) {
                    e.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("[LuckyCasino] 古いスロットマーカー " + removed + " 個を削除しました。");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a new {@link BlackjackGame}, wires up the cancelled callback, and
     * returns it.  Use this instead of calling {@code new BlackjackGame(...)}
     * directly so the callback is always registered.
     */
    private BlackjackGame createGame(BlackjackTable table) {
        BlackjackGame game = new BlackjackGame(plugin, table, economyManager, animManager);
        game.setOnGameCancelledCallback(() -> {
            cancelStartTimer();
            activeGame = null;
        });
        return game;
    }

    /**
     * Cancels and discards the active game.
     * Calls {@link BlackjackGame#cancel()} to prevent zombie scheduler tasks.
     */
    private void cancelActiveGame() {
        if (activeGame != null) {
            activeGame.cancel();
            activeGame = null;
        }
    }

    private void scheduleGameStart() {
        if (startTimer != null) return;
        notifyPlayers("§e" + (GAME_START_DELAY_TICKS / 20) + "秒後にゲームを開始します。"
                + " §7/bj join §eで参加できます。");
        startTimerFiresAt = System.currentTimeMillis() + GAME_START_DELAY_TICKS * 50L;
        startTimer = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            startTimer = null;
            startTimerFiresAt = -1L;
            if (activeGame != null && !activeGame.getPlayers().isEmpty()
                    && activeGame.getTable().getState() == TableState.WAITING) {
                activeGame.startBetting();
            }
        }, GAME_START_DELAY_TICKS);
    }

    private void cancelStartTimer() {
        if (startTimer != null) {
            startTimer.cancel();
            startTimer = null;
        }
        startTimerFiresAt = -1L;
    }

    private boolean requireActiveGame(Player player) {
        if (activeGame == null || !activeGame.isPlayerInGame(player.getUniqueId())) {
            player.sendMessage("§cテーブルに参加していません。 §e/bj join §cで参加してください。");
            return false;
        }
        return true;
    }

    private void notifyPlayers(String message) {
        if (activeGame == null) return;
        for (BlackjackPlayer bp : activeGame.getPlayers()) {
            Player p = plugin.getServer().getPlayer(bp.getUuid());
            if (p != null) p.sendMessage(message);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Accessors
    // ─────────────────────────────────────────────────────────────────────────

    public TableManager getTableManager()       { return tableManager; }
    public EconomyManager getEconomyManager()   { return economyManager; }
    public CardAnimationManager getAnimManager(){ return animManager; }
    public BlackjackGame getActiveGame()        { return activeGame; }
    public UUID getDealerPlayerUuid()           { return dealerPlayerUuid; }

    /** Returns true if the given entity is our managed dealer NPC. */
    public boolean isDealerNpc(org.bukkit.entity.Entity entity) {
        return dealerNpc != null && dealerNpc.equals(entity);
    }
}
