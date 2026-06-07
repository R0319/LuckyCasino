package io.github.r0319.luckyCasino.blackjack.game;

import io.github.r0319.luckyCasino.blackjack.animation.CardAnimationManager;
import io.github.r0319.luckyCasino.blackjack.economy.EconomyManager;
import io.github.r0319.luckyCasino.blackjack.player.BlackjackPlayer;
import io.github.r0319.luckyCasino.blackjack.table.BlackjackTable;
import io.github.r0319.luckyCasino.blackjack.table.SerializableLocation;
import io.github.r0319.luckyCasino.blackjack.table.TableState;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controls the full lifecycle of a single blackjack round.
 *
 * <h3>State flow</h3>
 * WAITING → BETTING → DEALING → PLAYER_TURN → DEALER_TURN → SETTLEMENT → WAITING …
 *
 * <h3>Betting timeout</h3>
 * Players have {@link #BETTING_TIMEOUT_TICKS} (60 s) to place a bet after the
 * BETTING phase starts.  Anyone who has not bet in time is kicked with a chat
 * message.  If all players are kicked the round is cancelled and
 * {@link #setOnGameCancelledCallback} is invoked so the module can clean up.
 *
 * <h3>Zombie-game prevention</h3>
 * When all players leave mid-game, {@link BlackjackModule} calls {@link #cancel()}.
 * This sets the {@link #cancelled} flag, which all scheduled callbacks check at
 * entry — they return immediately rather than driving a game with no players.
 */
public class BlackjackGame {

    // ── Rule constants ───────────────────────────────────────────────────────
    private static final int    DEALER_STAND_THRESHOLD = 17;
    private static final int    BLACKJACK_VALUE        = 21;
    private static final double BLACKJACK_PAYOUT       = 2.5;
    private static final double WIN_PAYOUT             = 2.0;
    private static final double DOUBLE_DOWN_MULTIPLIER = 2.0;

    // ── Timing (ticks) ───────────────────────────────────────────────────────
    private static final long DEAL_DELAY_TICKS   = 20L;
    private static final long DEALER_DRAW_DELAY  = 30L;
    private static final long CLEAR_CARDS_DELAY  = 80L;
    private static final long NEXT_ROUND_DELAY   = 200L;
    /** 60 s window to place a bet before being kicked. */
    private static final long BETTING_TIMEOUT_TICKS = 1200L;

    // ── Dependencies ─────────────────────────────────────────────────────────
    private final JavaPlugin plugin;
    private final BlackjackTable table;
    private final EconomyManager economy;
    private final CardAnimationManager animManager;

    // ── Round state ──────────────────────────────────────────────────────────
    private final List<BlackjackPlayer> players = new ArrayList<>();
    private final Hand dealerHand = new Hand();
    private Deck deck;
    private int currentPlayerIndex = 0;
    private TextDisplay dealerHiddenDisplay;

    /** Pre-round bet reservations: set during WAITING, applied when BETTING starts. */
    private final java.util.Map<UUID, Double> reservedBets = new java.util.HashMap<>();

    /**
     * Epoch-ms when {@code nextRoundTask} will fire.  -1 while not scheduled.
     * Used by {@link io.github.r0319.luckyCasino.blackjack.BlackjackModule} to
     * display countdown info.
     */
    private long nextRoundFiresAt = -1L;

    // ── Scheduled tasks ──────────────────────────────────────────────────────
    private BukkitTask nextRoundTask;
    private BukkitTask bettingTimer;

    /**
     * When true, all pending scheduler callbacks are no-ops.
     * Set by {@link #cancel()} when the module decides this game instance
     * should no longer run (e.g. all players left mid-round).
     */
    private volatile boolean cancelled = false;

    /** Called when all players are kicked due to betting timeout. */
    private Runnable onGameCancelledCallback;

    public BlackjackGame(JavaPlugin plugin, BlackjackTable table,
                         EconomyManager economy, CardAnimationManager animManager) {
        this.plugin = plugin;
        this.table = table;
        this.economy = economy;
        this.animManager = animManager;
    }

    /** Register a callback for when all players are kicked (betting timeout). */
    public void setOnGameCancelledCallback(Runnable r) {
        this.onGameCancelledCallback = r;
    }

    /**
     * Permanently cancels this game instance.
     * All scheduled callbacks become no-ops; tracked tasks are cancelled immediately.
     * Call this before nulling the module's {@code activeGame} reference.
     */
    public void cancel() {
        cancelled = true;
        cancelNextRound(); // Cancels nextRoundTask + bettingTimer
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Player management
    // ═════════════════════════════════════════════════════════════════════════

    public boolean addPlayer(Player player, int slot) {
        if (table.getState() != TableState.WAITING) {
            player.sendMessage("§cゲームはすでに進行中です。");
            return false;
        }
        if (isSlotTaken(slot)) {
            player.sendMessage("§cそのスロットはすでに使用されています。");
            return false;
        }
        if (isPlayerInGame(player.getUniqueId())) {
            player.sendMessage("§cすでにこのテーブルに参加しています。");
            return false;
        }
        players.add(new BlackjackPlayer(player.getUniqueId(), player.getName(), slot));
        broadcast("§a" + player.getName() + " §rがスロット " + (slot + 1) + " で参加しました。");
        return true;
    }

    /**
     * Removes a player.  If it was their turn in PLAYER_TURN phase the turn
     * advances automatically so the round can continue.
     */
    public void removePlayer(UUID uuid) {
        int idx = -1;
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getUuid().equals(uuid)) { idx = i; break; }
        }
        if (idx == -1) return;

        players.remove(idx);
        reservedBets.remove(uuid); // Clear any pending reservation

        if (table.getState() == TableState.PLAYER_TURN) {
            if (idx < currentPlayerIndex) {
                currentPlayerIndex--;
            } else if (idx == currentPlayerIndex) {
                if (players.isEmpty() || currentPlayerIndex >= players.size()) {
                    startDealerTurn();
                } else {
                    advanceToActivePlayer();
                }
            }
        }
    }

    public boolean isPlayerInGame(UUID uuid) {
        return players.stream().anyMatch(p -> p.getUuid().equals(uuid));
    }

    private boolean isSlotTaken(int slot) {
        return players.stream().anyMatch(p -> p.getSlot() == slot);
    }

    public int getFreeSlot() {
        SerializableLocation[] locs = table.getPlayerLocations();
        for (int i = 0; i < locs.length; i++) {
            if (locs[i] == null) continue;
            final int slot = i;
            if (players.stream().noneMatch(p -> p.getSlot() == slot)) return slot;
        }
        return -1;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Phase: WAITING → BETTING
    // ═════════════════════════════════════════════════════════════════════════

    public void startBetting() {
        if (cancelled) return;
        if (players.isEmpty()) return;
        table.setState(TableState.BETTING);
        broadcast("§6§l═══ ブラックジャック開始！ ═══");

        // Apply any pre-round reserved bets first
        applyReservedBets();

        // If everyone's bet was reserved, skip straight to dealing
        if (players.stream().allMatch(BlackjackPlayer::hasBet)) {
            startDealing();
            return;
        }

        broadcast("§e/bj bet <金額> §rでベットしてください。 §7(60秒以内)");

        // Countdown reminders
        scheduleIfBetting(30 * 20L, "§e残り30秒！ §7/bj bet <金額> でベットしてください。");
        scheduleIfBetting(50 * 20L, "§c残り10秒！");
        scheduleIfBetting(55 * 20L, "§c残り5秒！");

        // Kick non-betters at timeout
        bettingTimer = Bukkit.getScheduler().runTaskLater(plugin, this::handleBettingTimeout,
                BETTING_TIMEOUT_TICKS);
    }

    /**
     * Attempts to apply each player's reserved bet.  Called at the start of BETTING.
     * Removes applied (or invalid) reservations from the map.
     */
    private void applyReservedBets() {
        for (BlackjackPlayer bp : players) {
            Double reserved = reservedBets.remove(bp.getUuid());
            if (reserved == null || bp.hasBet()) continue;

            Player p = Bukkit.getPlayer(bp.getUuid());
            if (p == null) continue;

            if (!economy.hasBalance(p, reserved)) {
                p.sendMessage("§c予約ベット §e" + economy.format(reserved)
                        + " §cの残高が不足しています。手動でベットしてください。");
                continue;
            }
            economy.withdraw(p, reserved);
            bp.setBet(reserved);
            p.sendMessage("§a予約ベット §e" + economy.format(reserved) + " §aが自動的に適用されました。");
        }
    }

    private void scheduleIfBetting(long delay, String message) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (cancelled) return;
            if (table.getState() == TableState.BETTING) broadcast(message);
        }, delay);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Betting timeout
    // ═════════════════════════════════════════════════════════════════════════

    private void handleBettingTimeout() {
        if (cancelled) return;
        bettingTimer = null;
        if (table.getState() != TableState.BETTING) return;

        // Collect non-betters (snapshot before mutation)
        List<UUID> toKick = players.stream()
                .filter(bp -> !bp.hasBet())
                .map(BlackjackPlayer::getUuid)
                .collect(Collectors.toList());

        for (UUID uuid : toKick) {
            removePlayer(uuid); // Remove from game BEFORE kicking to prevent double-cleanup
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.kick(Component.text("§cベット時間切れ。テーブルから退場されました。"));
            }
        }

        if (toKick.isEmpty()) return; // Everyone bet in time (shouldn't reach here normally)

        if (players.isEmpty()) {
            // All players kicked → cancel round
            table.setState(TableState.WAITING);
            if (onGameCancelledCallback != null) {
                onGameCancelledCallback.run();
            }
            return;
        }

        // Remaining players all have bets → proceed
        startDealing();
    }

    private void cancelBettingTimer() {
        if (bettingTimer != null) {
            bettingTimer.cancel();
            bettingTimer = null;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Phase: BETTING → DEALING
    // ═════════════════════════════════════════════════════════════════════════

    public boolean placeBet(Player player, double amount) {
        BlackjackPlayer bp = findPlayer(player.getUniqueId());
        if (bp == null) { player.sendMessage("§cテーブルに参加していません。"); return false; }
        if (amount <= 0) { player.sendMessage("§cベット額は0より大きい値を指定してください。"); return false; }

        // ── Pre-round reservation (WAITING state) ────────────────────────────
        if (table.getState() == TableState.WAITING) {
            if (!economy.hasBalance(player, amount)) {
                player.sendMessage("§c残高が不足しています。 (残高: "
                        + economy.format(economy.getBalance(player)) + ")");
                return false;
            }
            double old = reservedBets.getOrDefault(player.getUniqueId(), 0.0);
            reservedBets.put(player.getUniqueId(), amount);
            if (old > 0) {
                player.sendMessage("§a予約ベットを §e" + economy.format(old)
                        + " §a→ §e" + economy.format(amount) + " §aに変更しました。");
            } else {
                player.sendMessage("§a" + economy.format(amount)
                        + " §aを次のラウンドの予約ベットに登録しました。 §7(ラウンド開始時に自動ベット)");
            }
            return true;
        }

        // ── Live bet (BETTING state) ─────────────────────────────────────────
        if (table.getState() != TableState.BETTING) {
            player.sendMessage("§c今はベットできません。");
            return false;
        }
        if (bp.hasBet()) { player.sendMessage("§cすでにベット済みです。"); return false; }
        if (!economy.hasBalance(player, amount)) {
            player.sendMessage("§c残高が不足しています。 (残高: "
                    + economy.format(economy.getBalance(player)) + ")");
            return false;
        }
        economy.withdraw(player, amount);
        bp.setBet(amount);
        player.sendMessage("§a" + economy.format(amount) + " をベットしました。");
        broadcast("§7" + player.getName() + " がベットしました。");

        if (players.stream().allMatch(BlackjackPlayer::hasBet)) {
            cancelBettingTimer(); // All in — no need to wait for timeout
            startDealing();
        }
        return true;
    }

    /**
     * Returns the reserved bet amount for a player, or 0 if none.
     * Used by the info command to display reservation status.
     */
    public double getReservedBet(UUID uuid) {
        return reservedBets.getOrDefault(uuid, 0.0);
    }

    private void startDealing() {
        if (cancelled) return;
        table.setState(TableState.DEALING);
        deck = new Deck();
        dealerHand.clear();
        broadcast("§bカードを配布します...");
        Bukkit.getScheduler().runTaskLater(plugin, this::performDeal, DEAL_DELAY_TICKS);
    }

    private void performDeal() {
        if (cancelled) return;
        for (int i = 0; i < players.size(); i++) {
            BlackjackPlayer bp = players.get(i);
            bp.getHand().clear();
            Card c1 = deck.draw();
            Card c2 = deck.draw();
            bp.getHand().addCard(c1);
            bp.getHand().addCard(c2);

            Player p = Bukkit.getPlayer(bp.getUuid());
            if (p != null) p.sendMessage("§7あなたの手札: " + bp.getHand());

            final long delay = i * 10L;
            final BlackjackPlayer fbp = bp;
            final Card fc1 = c1, fc2 = c2;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (cancelled) return;
                animateDealToSlot(fbp, fc1, fc2);
            }, delay);
        }

        Card dealerC1 = deck.draw();
        Card dealerC2 = deck.draw();
        dealerHand.addCard(dealerC1);
        dealerHand.addCard(dealerC2);

        long dealerDelay = players.size() * 10L + 10L;
        final Card fd1 = dealerC1, fd2 = dealerC2;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (cancelled) return;
            Location dealerLoc = getDealerLoc();
            if (dealerLoc != null) {
                animManager.dealCard(table.getTableId(), fd1, dealerLoc,
                        dealerLoc.clone().add(0.35, 0, 0.5), true);
                dealerHiddenDisplay = animManager.dealCard(table.getTableId(), fd2, dealerLoc,
                        dealerLoc.clone().add(-0.35, 0, 0.5), false);
            }
            broadcast("§7ディーラーの表向きカード: " + fd1.getDisplayName() + " §7[?]");
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (cancelled) return;
                startPlayerTurn();
            }, DEAL_DELAY_TICKS * 2);
        }, dealerDelay);
    }

    private void animateDealToSlot(BlackjackPlayer bp, Card c1, Card c2) {
        Location from = getDealerLoc();
        SerializableLocation slotSL = table.getPlayerLocations()[bp.getSlot()];
        if (from == null || slotSL == null) return;
        Location slotBase = slotSL.toLocation();
        animManager.dealCard(table.getTableId(), c1, from, slotBase.clone().add( 0.35, 0, 0), true);
        animManager.dealCard(table.getTableId(), c2, from, slotBase.clone().add(-0.35, 0, 0), true);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Phase: DEALING → PLAYER_TURN
    // ═════════════════════════════════════════════════════════════════════════

    private void startPlayerTurn() {
        if (cancelled) return;
        table.setState(TableState.PLAYER_TURN);
        currentPlayerIndex = 0;
        advanceToActivePlayer();
    }

    private void advanceToActivePlayer() {
        if (cancelled) return;
        while (currentPlayerIndex < players.size()
                && players.get(currentPlayerIndex).isDone()) {
            currentPlayerIndex++;
        }
        if (currentPlayerIndex >= players.size()) {
            startDealerTurn();
            return;
        }
        promptCurrentPlayer();
    }

    private void promptCurrentPlayer() {
        BlackjackPlayer bp = currentPlayer();
        if (bp == null) { startDealerTurn(); return; }

        Player p = Bukkit.getPlayer(bp.getUuid());
        if (p == null) { currentPlayerIndex++; advanceToActivePlayer(); return; }

        boolean canDouble = bp.getHand().size() == 2 && economy.hasBalance(p, bp.getBet());
        p.sendMessage("§6§l─── あなたのターン ───");
        p.sendMessage("§7手札: " + bp.getHand());
        p.sendMessage("§e/bj hit §7| §e/bj stand"
                + (canDouble ? " §7| §e/bj doubledown" : ""));
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Player actions
    // ═════════════════════════════════════════════════════════════════════════

    public void hit(Player player) {
        if (!isPlayerTurn(player)) return;

        BlackjackPlayer bp = currentPlayer();
        Card card = deck.draw();
        bp.getHand().addCard(card);

        Location from = getDealerLoc();
        SerializableLocation slotSL = table.getPlayerLocations()[bp.getSlot()];
        if (from != null && slotSL != null) {
            Location slotBase = slotSL.toLocation();
            double offset = (bp.getHand().size() - 1) * 0.35 - 0.35;
            animManager.dealCard(table.getTableId(), card, from,
                    slotBase.clone().add(offset, 0, 0), true);
        }

        player.sendMessage("§7引いたカード: " + card.getDisplayName() + "  手札: " + bp.getHand());

        if (bp.getHand().isBust()) {
            player.sendMessage("§c§lバスト！ 合計: " + bp.getHand().getTotal());
            bp.setBusted(true);
            broadcastExcept(player, "§c" + player.getName() + " がバストしました。");
            currentPlayerIndex++;
            advanceToActivePlayer();
        } else if (bp.getHand().getTotal() == BLACKJACK_VALUE) {
            player.sendMessage("§a21！ 自動スタンドします。");
            bp.setStood(true);
            currentPlayerIndex++;
            advanceToActivePlayer();
        }
    }

    public void stand(Player player) {
        if (!isPlayerTurn(player)) return;

        BlackjackPlayer bp = currentPlayer();
        bp.setStood(true);
        player.sendMessage("§7スタンド。合計: §f" + bp.getHand().getTotal());
        currentPlayerIndex++;
        advanceToActivePlayer();
    }

    public void doubleDown(Player player) {
        if (!isPlayerTurn(player)) return;

        BlackjackPlayer bp = currentPlayer();
        if (bp.getHand().size() != 2) {
            player.sendMessage("§cダブルダウンは最初の2枚の時のみ可能です。");
            return;
        }
        if (!economy.hasBalance(player, bp.getBet())) {
            player.sendMessage("§cダブルダウンの追加ベット分の残高が不足しています。");
            return;
        }

        economy.withdraw(player, bp.getBet());
        bp.setBet(bp.getBet() * DOUBLE_DOWN_MULTIPLIER);
        bp.setDoubledDown(true);

        Card card = deck.draw();
        bp.getHand().addCard(card);

        Location from = getDealerLoc();
        SerializableLocation slotSL = table.getPlayerLocations()[bp.getSlot()];
        if (from != null && slotSL != null) {
            Location slotBase = slotSL.toLocation();
            animManager.dealCard(table.getTableId(), card, from,
                    slotBase.clone().add(0.7, 0, 0.25), true);
        }

        player.sendMessage("§6ダブルダウン！ 引いたカード: " + card.getDisplayName()
                + "  手札: " + bp.getHand());

        if (bp.getHand().isBust()) {
            player.sendMessage("§c§lバスト！ 合計: " + bp.getHand().getTotal());
            bp.setBusted(true);
        }
        currentPlayerIndex++;
        advanceToActivePlayer();
    }

    private boolean isPlayerTurn(Player player) {
        if (table.getState() != TableState.PLAYER_TURN) {
            player.sendMessage("§c今はアクションを選択できません。");
            return false;
        }
        BlackjackPlayer bp = currentPlayer();
        if (bp == null || !bp.getUuid().equals(player.getUniqueId())) {
            player.sendMessage("§c今はあなたのターンではありません。");
            return false;
        }
        return true;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Phase: PLAYER_TURN → DEALER_TURN
    // ═════════════════════════════════════════════════════════════════════════

    private void startDealerTurn() {
        if (cancelled) return;
        table.setState(TableState.DEALER_TURN);
        broadcast("§6§l═══ ディーラーのターン ═══");

        if (dealerHiddenDisplay != null) {
            animManager.flipCard(dealerHiddenDisplay);
            dealerHiddenDisplay = null;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (cancelled) return;
            broadcast("§7ディーラーの手札: " + dealerHand);
            dealerDraw();
        }, 20L);
    }

    private void dealerDraw() {
        if (cancelled) return;
        boolean shouldHit = dealerHand.getTotal() < DEALER_STAND_THRESHOLD
                || dealerHand.isSoftSeventeen();

        if (!shouldHit) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (cancelled) return;
                settle();
            }, 20L);
            return;
        }

        Card card = deck.draw();
        dealerHand.addCard(card);

        Location dealerLoc = getDealerLoc();
        if (dealerLoc != null) {
            double offset = (dealerHand.size() - 1) * 0.35 - 0.35;
            animManager.dealCard(table.getTableId(), card, dealerLoc,
                    dealerLoc.clone().add(offset, 0, 0.5), true);
        }

        broadcast("§7ディーラーが引きました: " + card.getDisplayName() + "  手札: " + dealerHand);

        if (dealerHand.isBust()) {
            broadcast("§c§lディーラーがバストしました！");
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (cancelled) return;
                settle();
            }, 20L);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (cancelled) return;
                dealerDraw();
            }, DEALER_DRAW_DELAY);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Phase: DEALER_TURN → SETTLEMENT
    // ═════════════════════════════════════════════════════════════════════════

    private void settle() {
        if (cancelled) return;
        table.setState(TableState.SETTLEMENT);
        broadcast("§6§l═══ 精算 ═══");

        int     dealerTotal     = dealerHand.getTotal();
        boolean dealerBust      = dealerHand.isBust();
        boolean dealerBlackjack = dealerHand.isBlackjack();

        broadcast("§7ディーラー最終手札: " + dealerHand);

        for (BlackjackPlayer bp : players) {
            Player p = Bukkit.getPlayer(bp.getUuid());
            if (p == null) continue;

            int     playerTotal     = bp.getHand().getTotal();
            boolean playerBlackjack = bp.getHand().isBlackjack();

            if (bp.isBusted()) {
                p.sendMessage("§c負け §7— ベット額 " + economy.format(bp.getBet()) + " は没収されました。");
            } else if (playerBlackjack && dealerBlackjack) {
                economy.deposit(p, bp.getBet());
                p.sendMessage("§e引き分け §7（両者ブラックジャック）— "
                        + economy.format(bp.getBet()) + " を返還しました。");
            } else if (playerBlackjack) {
                double payout = Math.floor(bp.getBet() * BLACKJACK_PAYOUT);
                economy.deposit(p, payout);
                p.sendMessage("§6§lブラックジャック！ §a" + economy.format(payout) + " を獲得！");
            } else if (dealerBust || playerTotal > dealerTotal) {
                double payout = bp.getBet() * WIN_PAYOUT;
                economy.deposit(p, payout);
                p.sendMessage("§a勝利！ §r" + economy.format(payout) + " を獲得しました！");
            } else if (playerTotal == dealerTotal) {
                economy.deposit(p, bp.getBet());
                p.sendMessage("§e引き分け §7— " + economy.format(bp.getBet()) + " を返還しました。");
            } else {
                p.sendMessage("§c負け §7— ベット額 " + economy.format(bp.getBet()) + " は没収されました。");
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (cancelled) return;
            animManager.clearTableCards(table.getTableId());
            resetRound();
        }, CLEAR_CARDS_DELAY);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Reset & auto-restart
    // ═════════════════════════════════════════════════════════════════════════

    private void resetRound() {
        if (cancelled) return;
        players.forEach(BlackjackPlayer::reset);
        dealerHand.clear();
        deck = null;
        currentPlayerIndex = 0;
        dealerHiddenDisplay = null;
        table.setState(TableState.WAITING);

        if (!players.isEmpty()) {
            broadcast("§7ラウンド終了。§e10秒後に次のラウンドのベットを開始します。");
            nextRoundFiresAt = System.currentTimeMillis() + NEXT_ROUND_DELAY * 50L;
            nextRoundTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (cancelled) return;
                nextRoundTask = null;
                nextRoundFiresAt = -1L;
                if (!players.isEmpty() && table.getState() == TableState.WAITING) {
                    startBetting();
                }
            }, NEXT_ROUND_DELAY);
        } else {
            broadcast("§7テーブルがリセットされました。");
        }
    }

    public void cancelNextRound() {
        if (nextRoundTask != null) { nextRoundTask.cancel(); nextRoundTask = null; }
        nextRoundFiresAt = -1L;
        cancelBettingTimer();
    }

    /**
     * Returns seconds until the next round starts, or -1 if not scheduled.
     * Used by the info command for countdown display.
     */
    public long getSecondsUntilNextRound() {
        if (nextRoundFiresAt < 0) return -1L;
        long ms = nextRoundFiresAt - System.currentTimeMillis();
        return Math.max(0L, ms / 1000L);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Utility
    // ═════════════════════════════════════════════════════════════════════════

    private BlackjackPlayer currentPlayer() {
        if (currentPlayerIndex < 0 || currentPlayerIndex >= players.size()) return null;
        return players.get(currentPlayerIndex);
    }

    private BlackjackPlayer findPlayer(UUID uuid) {
        return players.stream().filter(p -> p.getUuid().equals(uuid)).findFirst().orElse(null);
    }

    private Location getDealerLoc() {
        return table.getDealerLocation() != null ? table.getDealerLocation().toLocation() : null;
    }

    public void broadcast(String message) {
        for (BlackjackPlayer bp : players) {
            Player p = Bukkit.getPlayer(bp.getUuid());
            if (p != null) p.sendMessage(message);
        }
    }

    private void broadcastExcept(Player except, String message) {
        for (BlackjackPlayer bp : players) {
            Player p = Bukkit.getPlayer(bp.getUuid());
            if (p != null && !p.equals(except)) p.sendMessage(message);
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────
    public BlackjackTable getTable()          { return table; }
    public List<BlackjackPlayer> getPlayers() { return Collections.unmodifiableList(players); }
    public Hand getDealerHand()               { return dealerHand; }
}
