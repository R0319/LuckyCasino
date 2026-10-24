package io.github.r0319.luckycasino.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * カジノのテーブル 1 卓の共通部分。テーブルとディーラーと椅子の表示、席とチップの出し入れ、
 * 1 秒ごとの進行と遅延処理を持つ。ゲームごとの進行はサブクラスが書く。メインスレッドからだけ触る。
 */
abstract class CasinoTable {

    /** 席に着いている人（またはボット）。chips は手元、atRisk は賭けている分。控え（ChipLedger）には合計を書く。 */
    static final class Seat {
        final UUID player;
        final String name;
        final boolean bot;
        long chips;
        long atRisk;
        /** 勝負中に席を立った。勝負が終わったら精算する */
        boolean away;

        Seat(UUID player, String name, long chips, boolean bot) {
            this.player = player;
            this.name = name;
            this.chips = chips;
            this.bot = bot;
        }
    }

    protected final LuckyCasinoPaper plugin;
    private final String id;
    private final GameType type;
    protected final Frame frame;
    private final UUID owner;
    protected final Bank bank;
    protected final Seat[] seats;

    private final List<Entity> fixtures = new ArrayList<>();
    private final Interaction[] seatHits;
    private final ItemDisplay[] mounts;
    protected Mannequin dealer;
    private TextDisplay status;
    private BukkitTask ticker;
    private final List<BukkitTask> pending = new ArrayList<>();
    /** 自己テスト中の観察者（null なら通常運転）。 */
    SelfTest.Observer observer;

    CasinoTable(LuckyCasinoPaper plugin, String id, GameType type, Frame frame, UUID owner, Bank bank) {
        this.plugin = plugin;
        this.id = id;
        this.type = type;
        this.frame = frame;
        this.owner = owner;
        this.bank = bank;
        int count = shape().seats();
        this.seats = new Seat[count];
        this.seatHits = new Interaction[count];
        this.mounts = new ItemDisplay[count];
    }

    // ---- サブクラスが書くところ ----

    abstract TableShape shape();

    /** 席に着くときに選べるチップの枚数。 */
    abstract List<Long> buyIns();

    /** 席に着くのに最低限必要なチップ。 */
    abstract long minChips();

    /** 表示を出した直後。 */
    protected abstract void onSpawn();

    /** 表示を片付ける前。勝負の途中なら精算せずに表示だけ消す（賭け金は呼び出し側が返す）。 */
    protected abstract void onDespawn();

    /** 席に着いた直後。 */
    protected abstract void onSeated(int seat, Player player);

    /** 席に着いている人がテーブルや椅子をクリックしたとき。 */
    abstract void openMenu(Player player);

    /**
     * 席を立ちたいと言われたとき。賭ける前の取り消しなどを済ませ、すぐ精算してよければ true、
     * 勝負が終わるまで待つなら false（away にして、手番は自動で進める）。
     */
    protected abstract boolean prepareLeave(int seat);

    /** 1 秒ごと。 */
    protected abstract void tickSecond();

    /** テーブルの上に出す状況の文字。 */
    protected abstract Component statusText();

    /** 再接続時に Geyser 拡張へ送り直すカードの一覧。 */
    void stateLines(List<String> out) {
    }

    // ---- 共通 ----

    String id() {
        return id;
    }

    GameType type() {
        return type;
    }

    Frame frame() {
        return frame;
    }

    UUID owner() {
        return owner;
    }

    Bank bank() {
        return bank;
    }

    boolean spawned() {
        return dealer != null;
    }

    Seat seat(int index) {
        return seats[index];
    }

    int seatCount() {
        return seats.length;
    }

    int seatOf(UUID player) {
        for (int i = 0; i < seats.length; i++) {
            if (seats[i] != null && seats[i].player.equals(player)) {
                return i;
            }
        }
        return -1;
    }

    boolean empty() {
        for (Seat seat : seats) {
            if (seat != null) {
                return false;
            }
        }
        return true;
    }

    int seatOfHitbox(Entity entity) {
        for (int i = 0; i < seatHits.length; i++) {
            if (seatHits[i] != null && seatHits[i].equals(entity)) {
                return i;
            }
        }
        return -1;
    }

    boolean isMount(Entity entity) {
        for (ItemDisplay mount : mounts) {
            if (mount != null && mount.equals(entity)) {
                return true;
            }
        }
        return false;
    }

    /** テーブル・ディーラー・椅子など、勝負に関係なく出ているエンティティ。 */
    List<Entity> fixtures() {
        return fixtures;
    }

    void spawn() {
        if (spawned()) {
            return;
        }
        TableShape shape = shape();
        Location origin = frame.origin();
        float s = TableShape.MODEL_SCALE;
        fixtures.add(origin.getWorld().spawn(origin, ItemDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setItemStack(Items.model(shape.model()));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(s, s, s), new AxisAngle4f()));
            entity.setViewRange(2f);
        }));

        Location dealerAt = frame.at(shape.dealer());
        dealer = dealerAt.getWorld().spawn(dealerAt, Mannequin.class, entity -> {
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            entity.setImmovable(true);
            entity.setSilent(true);
            entity.setDescription(Component.text("ディーラー", NamedTextColor.GOLD));
            entity.setRotation(frame.yaw(), 0f);
        });
        fixtures.add(dealer);

        Location statusAt = frame.at(shape.status());
        status = statusAt.getWorld().spawn(statusAt, TextDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.text(Component.empty());
        });
        fixtures.add(status);

        for (int i = 0; i < seats.length; i++) {
            double[] p = shape.seat(i);
            fixtures.add(block(frame.at(p[0], 0, p[2]), Material.DARK_OAK_FENCE, new Vector3f(1f, 0.38f, 1f)));
            fixtures.add(block(frame.at(p[0], 0.38, p[2]), Material.RED_WOOL, new Vector3f(0.5f, 0.1f, 0.5f)));
            Location mountAt = frame.at(p[0], 0.25, p[2]);
            mounts[i] = mountAt.getWorld().spawn(mountAt, ItemDisplay.class, entity -> entity.setPersistent(false));
            fixtures.add(mounts[i]);
            Location hitAt = frame.at(p[0], 0, p[2]);
            seatHits[i] = hitAt.getWorld().spawn(hitAt, Interaction.class, entity -> {
                entity.setPersistent(false);
                entity.setInteractionWidth(0.8f);
                entity.setInteractionHeight(1.2f);
                entity.setResponsive(true);
            });
            fixtures.add(seatHits[i]);
        }
        onSpawn();
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> guarded(this::tickSecond), 20L, 20L);
        updateStatus();
    }

    /** BlockDisplay はブロックの角が原点なので、中心に合わせて置く。 */
    protected BlockDisplay block(Location at, Material material, Vector3f scale) {
        return at.getWorld().spawn(at, BlockDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setBlock(material.createBlockData());
            entity.setTransformation(new Transformation(new Vector3f(-scale.x / 2, 0, -scale.z / 2), new AxisAngle4f(),
                    scale, new AxisAngle4f()));
        });
    }

    /**
     * 表示を片付け、全員を精算する。勝負の途中なら賭け金はそのまま返す（サーバー停止やチャンクの読み込み解除のとき）。
     */
    void despawn() {
        cancelTasks();
        onDespawn();
        for (int i = 0; i < seats.length; i++) {
            Seat seat = seats[i];
            if (seat != null) {
                seat.chips += seat.atRisk;
                seat.atRisk = 0;
                cashOut(i, "テーブルが片付けられたため、チップを精算しました");
            }
        }
        fixtures.forEach(Entity::remove);
        fixtures.clear();
        dealer = null;
        status = null;
    }

    private void cancelTasks() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        pending.forEach(BukkitTask::cancel);
        pending.clear();
    }

    /** ticks 後に task を実行する。テーブルを片付けると取り消される。 */
    protected void later(long ticks, Runnable task) {
        pending.removeIf(BukkitTask::isCancelled);
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pending.remove(holder[0]);
            guarded(task);
        }, Math.max(1, ticks));
        pending.add(holder[0]);
    }

    /** 進行中の例外でテーブルが止まらないよう、記録して続ける（自己テストでは失敗として数える）。 */
    protected void guarded(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "テーブル " + id + " の処理で例外が起きました", e);
            if (observer != null) {
                observer.error(e);
            }
        }
    }

    // ---- 席 ----

    /** 両替が済んだプレイヤーを席に着かせる。 */
    boolean sit(Player player, int index, long chips) {
        if (seats[index] != null || !spawned()) {
            return false;
        }
        if (!bank.withdraw(player, chips)) {
            player.sendMessage(Component.text("お金が足りません", NamedTextColor.RED));
            return false;
        }
        Seat seat = new Seat(player.getUniqueId(), player.getName(), chips, false);
        seats[index] = seat;
        writeLedger(seat);
        mounts[index].addPassenger(player);
        player.sendMessage(Component.text(bank.practice()
                ? "練習用のチップ " + chips + " 枚を受け取りました。お金は使いません。"
                : "チップ " + chips + " 枚に両替しました。席を立つと（スニーク）お金に戻ります。", NamedTextColor.GREEN));
        playSound(Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1f);
        onSeated(index, player);
        updateStatus();
        return true;
    }

    /** 自己テスト用のボットを座らせる（お金は動かさない）。 */
    void sitBot(int index, String name, long chips) {
        seats[index] = new Seat(UUID.randomUUID(), name, chips, true);
        onSeated(index, null);
        updateStatus();
    }

    /** 席を立つ（スニーク、ログアウト、テレポートなど）。 */
    void leave(UUID player) {
        int index = seatOf(player);
        if (index < 0) {
            return;
        }
        Seat seat = seats[index];
        if (seat.away) {
            return;
        }
        if (prepareLeave(index)) {
            cashOut(index, null);
        } else {
            seat.away = true;
            eject(index);
            send(seat, Component.text("勝負が終わったら残りのチップを精算します。以降は自動で進めます。", NamedTextColor.YELLOW));
            onAway(index);
        }
        updateStatus();
    }

    /** 勝負中に席を立った人の手番を進めるなど。 */
    protected void onAway(int seat) {
    }

    protected void cashOut(int index, String note) {
        Seat seat = seats[index];
        seats[index] = null;
        if (seat.bot) {
            if (observer != null) {
                observer.cashOut(index, seat.chips + seat.atRisk);
            }
            updateStatus();
            return;
        }
        if (!bank.practice()) {
            // 先に控えを消してからお金を戻す（落ちても二重には戻らない）
            plugin.ledger().remove(seat.player);
        }
        bank.deposit(Bukkit.getOfflinePlayer(seat.player), seat.chips + seat.atRisk);
        eject(index);
        long total = seat.chips + seat.atRisk;
        Component message = bank.practice()
                ? Component.text("席を立ちました（練習用チップ " + total + " 枚は回収しました）", NamedTextColor.GRAY)
                : Component.text("席を立ちました。チップ " + total + " 枚をお金に戻しました。", NamedTextColor.GREEN);
        if (note != null) {
            message = Component.text(note + "：", NamedTextColor.YELLOW).append(message);
        }
        send(seat, message);
        updateStatus();
    }

    private void eject(int index) {
        if (mounts[index] != null) {
            mounts[index].eject();
        }
    }

    /** チップが変わるたびに呼ぶ。手元 + 賭けている分を控えに書く。 */
    protected void writeLedger(Seat seat) {
        if (!bank.practice() && !seat.bot) {
            plugin.ledger().set(seat.player, seat.chips + seat.atRisk);
        }
    }

    /** 勝負の区切りで、席を立った人・オフラインの人・チップが足りない人を精算する。 */
    protected void settleAwaySeats() {
        for (int i = 0; i < seats.length; i++) {
            Seat seat = seats[i];
            if (seat == null) {
                continue;
            }
            writeLedger(seat);
            if (seat.away || (!seat.bot && Bukkit.getPlayer(seat.player) == null)) {
                cashOut(i, null);
            } else if (seat.chips < minChips()) {
                send(seat, Component.text("チップが最低額に足りないため、席を立ちます", NamedTextColor.YELLOW));
                cashOut(i, null);
            }
        }
    }

    // ---- 表示の小物 ----

    protected void updateStatus() {
        if (status != null) {
            status.text(statusText());
        }
    }

    protected TextDisplay label(double[] local, float scale) {
        Location at = frame.at(local);
        return at.getWorld().spawn(at, TextDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(scale, scale, scale), new AxisAngle4f()));
            entity.setTeleportDuration(CardView.STEP_TICKS);
            entity.text(Component.empty());
        });
    }

    protected void swing() {
        if (dealer != null) {
            dealer.swingMainHand();
        }
    }

    protected void playSound(Sound sound, float pitch) {
        Location at = frame.at(0, TableShape.TOP, 0);
        at.getWorld().playSound(at, sound, 1f, pitch);
    }

    protected void broadcast(Component message) {
        for (Seat seat : seats) {
            if (seat != null && !seat.away) {
                send(seat, message);
            }
        }
    }

    protected static void send(Seat seat, Component message) {
        if (seat.bot) {
            return;
        }
        Player player = Bukkit.getPlayer(seat.player);
        if (player != null) {
            player.sendMessage(message);
        }
    }

    protected static Player online(Seat seat) {
        return seat == null || seat.bot ? null : Bukkit.getPlayer(seat.player);
    }

    /** 状況の文字の下に出す賭け金の行。 */
    protected Component limitsLine(String text) {
        return Component.newline().append(Component.text(text + (bank.practice() ? "（練習用）" : ""), NamedTextColor.GRAY));
    }
}
