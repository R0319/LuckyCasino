package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.blackjack.Action;
import io.github.r0319.luckycasino.blackjack.BlackjackEvent;
import io.github.r0319.luckycasino.blackjack.BlackjackRound;
import io.github.r0319.luckycasino.blackjack.Hand;
import io.github.r0319.luckycasino.blackjack.Outcome;
import io.github.r0319.luckycasino.bridge.CardCode;
import io.github.r0319.luckycasino.card.Card;
import io.github.r0319.luckycasino.card.Shoe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
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

import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * ブラックジャックのテーブル 1 卓。エンティティの表示、席とチップ、勝負の進行（BlackjackRound のイベント再生）を持つ。
 * メインスレッドからだけ触る。
 */
final class BlackjackTable {

    enum State {
        /** 誰も賭けていない */
        IDLE,
        /** 誰かが賭けて、締め切りを数えている */
        BETTING,
        /** 配ってから片付けるまで */
        PLAYING
    }

    /** 席に着いている人。chips は手元、atRisk は賭けている分。控え（ChipLedger）には合計を書く。 */
    static final class Seat {
        final UUID player;
        final String name;
        long chips;
        long atRisk;
        boolean confirmed;
        /** 勝負中に席を立った。勝負が終わったら精算する */
        boolean away;

        Seat(UUID player, String name, long chips) {
            this.player = player;
            this.name = name;
            this.chips = chips;
        }
    }

    private final LuckyCasinoPaper plugin;
    private final String id;
    private final TableLayout layout;
    private final UUID owner;
    private final Bank bank;
    private final TableSettings settings;
    private final Shoe shoe;
    private final Seat[] seats = new Seat[TableLayout.SEATS];

    // エンティティ
    private final List<Entity> fixtures = new ArrayList<>();
    private final Interaction[] seatHits = new Interaction[TableLayout.SEATS];
    private final ItemDisplay[] mounts = new ItemDisplay[TableLayout.SEATS];
    private Mannequin dealer;
    private TextDisplay status;

    // 勝負
    private State state = State.IDLE;
    private BlackjackRound round;
    private final Deque<BlackjackEvent> queue = new ArrayDeque<>();
    private boolean waitingInput;
    private boolean pumping;
    private int countdown;
    private BukkitTask ticker;
    private final List<BukkitTask> pending = new ArrayList<>();

    // 勝負中の表示
    private final List<List<List<Card>>> handCards = new ArrayList<>();
    private final List<List<List<CardView>>> handViews = new ArrayList<>();
    private final List<List<ChipView>> betViews = new ArrayList<>();
    private final List<List<TextDisplay>> handLabels = new ArrayList<>();
    private final List<ChipView> payoutViews = new ArrayList<>();
    private final List<Card> dealerCards = new ArrayList<>();
    private final List<CardView> dealerViews = new ArrayList<>();
    private TextDisplay dealerLabel;
    private final ChipView[] pendingBetViews = new ChipView[TableLayout.SEATS];

    BlackjackTable(LuckyCasinoPaper plugin, String id, TableLayout layout, UUID owner, Bank bank, TableSettings settings) {
        this.plugin = plugin;
        this.id = id;
        this.layout = layout;
        this.owner = owner;
        this.bank = bank;
        this.settings = settings;
        this.shoe = new Shoe(settings.rules().decks(), settings.rules().penetration(), new SecureRandom());
        for (int i = 0; i < TableLayout.SEATS; i++) {
            handCards.add(new ArrayList<>());
            handViews.add(new ArrayList<>());
            betViews.add(new ArrayList<>());
            handLabels.add(new ArrayList<>());
            pendingBetViews[i] = new ChipView();
        }
        newRound();
    }

    String id() {
        return id;
    }

    TableLayout layout() {
        return layout;
    }

    UUID owner() {
        return owner;
    }

    Bank bank() {
        return bank;
    }

    TableSettings settings() {
        return settings;
    }

    State state() {
        return state;
    }

    boolean spawned() {
        return dealer != null;
    }

    Seat seat(int index) {
        return seats[index];
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

    // ---- 表示の出し入れ ----

    void spawn() {
        if (spawned()) {
            return;
        }
        Location origin = layout.origin();
        float s = TableLayout.TABLE_SCALE;
        fixtures.add(origin.getWorld().spawn(origin, ItemDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setItemStack(Items.model("table/blackjack"));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(s, s, s), new AxisAngle4f()));
            entity.setViewRange(2f);
        }));

        Location dealerAt = layout.at(layout.dealer());
        dealer = dealerAt.getWorld().spawn(dealerAt, Mannequin.class, entity -> {
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            entity.setImmovable(true);
            entity.setSilent(true);
            entity.setDescription(Component.text("ディーラー", NamedTextColor.GOLD));
            entity.setRotation(layout.yaw(), 0f);
        });
        fixtures.add(dealer);

        Location statusAt = layout.at(0, 2.45, layout.dealer()[2]);
        status = statusAt.getWorld().spawn(statusAt, TextDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.text(Component.empty());
        });
        fixtures.add(status);

        for (int i = 0; i < TableLayout.SEATS; i++) {
            double[] p = layout.seat(i);
            fixtures.add(stool(layout.at(p[0], 0, p[2]), Material.DARK_OAK_FENCE, new Vector3f(1f, 0.38f, 1f), 0));
            fixtures.add(stool(layout.at(p[0], 0.38, p[2]), Material.RED_WOOL, new Vector3f(0.5f, 0.1f, 0.5f), 0));
            Location mountAt = layout.at(p[0], 0.25, p[2]);
            mounts[i] = mountAt.getWorld().spawn(mountAt, ItemDisplay.class, entity -> entity.setPersistent(false));
            fixtures.add(mounts[i]);
            Location hitAt = layout.at(p[0], 0, p[2]);
            seatHits[i] = hitAt.getWorld().spawn(hitAt, Interaction.class, entity -> {
                entity.setPersistent(false);
                entity.setInteractionWidth(0.8f);
                entity.setInteractionHeight(1.2f);
                entity.setResponsive(true);
            });
            fixtures.add(seatHits[i]);
        }
        startTicker();
        updateStatus();
    }

    private BlockDisplay stool(Location at, Material material, Vector3f scale, float y) {
        return at.getWorld().spawn(at, BlockDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setBlock(material.createBlockData());
            // BlockDisplay はブロックの角が原点なので、中心に合わせる
            entity.setTransformation(new Transformation(new Vector3f(-scale.x / 2, y, -scale.z / 2), new AxisAngle4f(),
                    scale, new AxisAngle4f()));
        });
    }

    /**
     * 表示を片付け、全員を精算する。勝負の途中なら賭け金はそのまま返す（サーバー停止やチャンクの読み込み解除のとき）。
     */
    void despawn() {
        for (int i = 0; i < seats.length; i++) {
            Seat seat = seats[i];
            if (seat != null) {
                seat.chips += seat.atRisk;
                seat.atRisk = 0;
                cashOut(i, "テーブルが片付けられたため、チップを精算しました");
            }
        }
        cancelTasks();
        clearRoundViews();
        for (ChipView view : pendingBetViews) {
            view.remove();
        }
        fixtures.forEach(Entity::remove);
        fixtures.clear();
        dealer = null;
        status = null;
        newRound();
        state = State.IDLE;
    }

    private void startTicker() {
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickSecond, 20L, 20L);
    }

    private void cancelTasks() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        pending.forEach(BukkitTask::cancel);
        pending.clear();
        queue.clear();
        waitingInput = false;
        pumping = false;
    }

    private void later(long ticks, Runnable task) {
        pending.removeIf(BukkitTask::isCancelled);
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pending.remove(holder[0]);
            task.run();
        }, ticks);
        pending.add(holder[0]);
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
        Seat seat = new Seat(player.getUniqueId(), player.getName(), chips);
        seats[index] = seat;
        writeLedger(seat);
        mounts[index].addPassenger(player);
        player.sendMessage(Component.text(bank.practice()
                ? "練習用のチップ " + chips + " 枚を受け取りました。お金は使いません。"
                : "チップ " + chips + " 枚に両替しました。席を立つと（スニーク）お金に戻ります。", NamedTextColor.GREEN));
        playSound(Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1f);
        if (state != State.PLAYING) {
            plugin.menus().openBet(player, this);
        } else {
            player.sendMessage(Component.text("今の勝負が終わったら賭けられます", NamedTextColor.GRAY));
        }
        updateStatus();
        return true;
    }

    /** 席を立つ（スニーク、ログアウト、テレポートなど）。 */
    void leave(UUID player) {
        int index = seatOf(player);
        if (index < 0) {
            return;
        }
        Seat seat = seats[index];
        if (state != State.PLAYING && seat.confirmed) {
            round.clearBet(index);
            seat.chips += seat.atRisk;
            seat.atRisk = 0;
            seat.confirmed = false;
            pendingBetViews[index].remove();
        }
        if (state == State.PLAYING && seat.atRisk > 0) {
            if (!seat.away) {
                seat.away = true;
                eject(index);
                send(seat, Component.text("勝負が終わったら残りのチップを精算します。以降の行動はスタンドとして扱います。", NamedTextColor.YELLOW));
                if (waitingInput && round.currentSeat() == index) {
                    act(index, Action.STAND);
                }
            }
            return;
        }
        cashOut(index, null);
        checkAllConfirmed();
        updateStatus();
    }

    private void cashOut(int index, String note) {
        Seat seat = seats[index];
        seats[index] = null;
        if (!bank.practice()) {
            // 先に控えを消してからお金を戻す（落ちても二重には戻らない）
            plugin.ledger().remove(seat.player);
        }
        bank.deposit(Bukkit.getOfflinePlayer(seat.player), seat.chips);
        eject(index);
        Component message = bank.practice()
                ? Component.text("席を立ちました（練習用チップ " + seat.chips + " 枚は回収しました）", NamedTextColor.GRAY)
                : Component.text("席を立ちました。チップ " + seat.chips + " 枚をお金に戻しました。", NamedTextColor.GREEN);
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

    private void writeLedger(Seat seat) {
        if (!bank.practice()) {
            plugin.ledger().set(seat.player, seat.chips + seat.atRisk);
        }
    }

    // ---- 賭ける ----

    boolean canBet(int index) {
        Seat seat = seats[index];
        return seat != null && state != State.PLAYING && !seat.confirmed && !seat.away;
    }

    /** 賭け金を決める。 */
    void confirmBet(Player player, long amount) {
        int index = seatOf(player.getUniqueId());
        if (index < 0 || !canBet(index)) {
            return;
        }
        Seat seat = seats[index];
        if (amount < settings.rules().minBet() || amount > settings.rules().maxBet() || amount > seat.chips) {
            player.sendMessage(Component.text("賭け金は " + settings.rules().minBet() + "〜" + settings.rules().maxBet()
                    + " 枚、手元のチップ以内で決めてください", NamedTextColor.RED));
            return;
        }
        round.bet(index, amount);
        seat.chips -= amount;
        seat.atRisk += amount;
        seat.confirmed = true;
        writeLedger(seat);
        pendingBetViews[index].show(layout.at(layout.chips(index)), amount);
        playSound(Sound.BLOCK_CHAIN_PLACE, 1.4f);
        if (state == State.IDLE) {
            state = State.BETTING;
            countdown = settings.bettingSeconds();
            broadcast(Component.text("ベットを受け付けています（あと " + countdown + " 秒）", NamedTextColor.AQUA));
        }
        checkAllConfirmed();
        updateStatus();
    }

    /** 席にいる全員が賭け終えたら、すぐ配る。 */
    private void checkAllConfirmed() {
        if (state != State.BETTING) {
            return;
        }
        boolean anyBet = false;
        for (Seat seat : seats) {
            if (seat == null) {
                continue;
            }
            if (seat.confirmed) {
                anyBet = true;
            } else if (seat.chips >= settings.rules().minBet()) {
                return;
            }
        }
        if (!anyBet) {
            state = State.IDLE;
            return;
        }
        countdown = Math.min(countdown, 2);
    }

    // ---- 進行 ----

    private void newRound() {
        round = new BlackjackRound(settings.rules(), shoe, TableLayout.SEATS);
    }

    private void tickSecond() {
        if (state == State.BETTING) {
            countdown--;
            if (countdown <= 0) {
                deal();
            }
            updateStatus();
        } else if (state == State.PLAYING && waitingInput) {
            countdown--;
            int index = round.currentSeat();
            Seat seat = index >= 0 ? seats[index] : null;
            if (seat != null) {
                Player player = Bukkit.getPlayer(seat.player);
                if (player != null) {
                    player.sendActionBar(Component.text("あなたの番です（あと " + Math.max(countdown, 0) + " 秒）", NamedTextColor.YELLOW));
                }
            }
            if (countdown <= 0 && index >= 0) {
                if (seat != null) {
                    send(seat, Component.text("時間切れのため、スタンドしました", NamedTextColor.GRAY));
                }
                act(index, Action.STAND);
            }
        }
    }

    private void deal() {
        if (round.bets().isEmpty()) {
            state = State.IDLE;
            return;
        }
        state = State.PLAYING;
        for (int i = 0; i < seats.length; i++) {
            pendingBetViews[i].remove();
            Long bet = round.bets().get(i);
            if (bet != null) {
                handCards.get(i).add(new ArrayList<>());
                handViews.get(i).add(new ArrayList<>());
                ChipView chips = new ChipView();
                chips.show(layout.at(layout.chips(i)), bet);
                betViews.get(i).add(chips);
                handLabels.get(i).add(label(layout.handLabel(i, 0, 1)));
            }
        }
        dealerLabel = label(layout.dealerLabel());
        broadcast(Component.text("ノーモアベット。配ります", NamedTextColor.AQUA));
        enqueue(round.deal());
    }

    /** 席の行動（コマンドやメニューから）。 */
    void act(Player player, Action action) {
        int index = seatOf(player.getUniqueId());
        if (index < 0 || state != State.PLAYING || !waitingInput || round.currentSeat() != index) {
            player.sendMessage(Component.text("今はあなたの番ではありません", NamedTextColor.RED));
            return;
        }
        if (!affordableActions(index).contains(action)) {
            player.sendMessage(Component.text("その行動は今は選べません", NamedTextColor.RED));
            return;
        }
        act(index, action);
    }

    private void act(int index, Action action) {
        Seat seat = seats[index];
        long extra = round.extraStake(action);
        if (seat != null && extra > 0) {
            seat.chips -= extra;
            seat.atRisk += extra;
            writeLedger(seat);
        }
        waitingInput = false;
        enqueue(round.act(index, action));
    }

    private List<Action> affordableActions(int index) {
        Seat seat = seats[index];
        List<Action> actions = new ArrayList<>();
        for (Action action : round.allowedActions()) {
            if (seat != null && round.extraStake(action) <= seat.chips) {
                actions.add(action);
            }
        }
        return actions;
    }

    private void enqueue(List<BlackjackEvent> events) {
        queue.addAll(events);
        pump();
    }

    /** イベントを 1 つずつ、それぞれの動きの長さだけ間を空けて再生する。 */
    private void pump() {
        if (pumping) {
            return;
        }
        pumping = true;
        try {
            while (!queue.isEmpty() && !waitingInput) {
                long delay = play(queue.poll());
                if (delay > 0) {
                    pumping = false;
                    later(delay, this::pump);
                    return;
                }
            }
        } finally {
            pumping = false;
        }
    }

    private long play(BlackjackEvent event) {
        return switch (event) {
            case BlackjackEvent.PlayerCard e -> {
                List<List<Card>> cards = handCards.get(e.seat());
                List<List<CardView>> views = handViews.get(e.seat());
                while (cards.size() <= e.hand()) {
                    cards.add(new ArrayList<>());
                    views.add(new ArrayList<>());
                }
                int index = cards.get(e.hand()).size();
                double[] at = layout.playerCard(e.seat(), e.hand(), cards.size(), index);
                views.get(e.hand()).add(CardView.deal(plugin, plugin.bridge(), layout.at(at),
                        TableLayout.delta(layout.shoe(), at), e.card().code(), true));
                cards.get(e.hand()).add(e.card());
                updateHandLabel(e.seat(), e.hand(), null);
                swing();
                yield 8;
            }
            case BlackjackEvent.DealerCard e -> {
                double[] at = layout.dealerCard(dealerViews.size());
                int code = e.hidden() ? CardCode.HIDDEN : e.card().code();
                dealerViews.add(CardView.deal(plugin, plugin.bridge(), layout.at(at),
                        TableLayout.delta(layout.shoe(), at), code, !e.hidden()));
                if (!e.hidden()) {
                    dealerCards.add(e.card());
                }
                updateDealerLabel();
                swing();
                yield 8;
            }
            case BlackjackEvent.HoleCardRevealed e -> {
                dealerCards.add(1, e.card());
                dealerViews.get(1).reveal(e.card().code());
                updateDealerLabel();
                yield 14;
            }
            case BlackjackEvent.Split e -> {
                splitViews(e.seat(), e.hand(), e.newHand());
                playSound(Sound.BLOCK_CHAIN_PLACE, 1.2f);
                yield 10;
            }
            case BlackjackEvent.Doubled e -> {
                betViews.get(e.seat()).get(e.hand()).show(chipSpot(e.seat(), e.hand()), e.bet());
                playSound(Sound.BLOCK_CHAIN_PLACE, 1.4f);
                yield 4;
            }
            case BlackjackEvent.Turn e -> {
                startTurn(e.seat(), e.hand());
                yield 0;
            }
            case BlackjackEvent.HandDone e -> {
                updateHandLabel(e.seat(), e.hand(), null);
                if (e.bust()) {
                    playSound(Sound.ENTITY_VILLAGER_NO, 1f);
                }
                yield 6;
            }
            case BlackjackEvent.DealerDone e -> {
                updateDealerLabel();
                yield 12;
            }
            case BlackjackEvent.Settled e -> {
                settle(e);
                yield 10;
            }
            case BlackjackEvent.RoundOver e -> {
                for (Seat seat : seats) {
                    if (seat != null) {
                        writeLedger(seat);
                    }
                }
                later(70, this::finishRound);
                yield 0;
            }
        };
    }

    private void startTurn(int index, int hand) {
        Seat seat = seats[index];
        Player player = seat == null ? null : Bukkit.getPlayer(seat.player);
        if (seat == null || seat.away || player == null) {
            // 席を立った人は自動でスタンド
            later(10, () -> act(index, Action.STAND));
            return;
        }
        waitingInput = true;
        countdown = settings.turnSeconds();
        updateStatus();
        int total = Hand.total(handCards.get(index).get(hand));
        player.showTitle(Title.title(Component.text("あなたの番", NamedTextColor.YELLOW),
                Component.text("合計 " + total, NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1200), Duration.ofMillis(300))));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.6f);
        Component buttons = Component.empty();
        for (Action action : affordableActions(index)) {
            buttons = buttons.append(button(action)).append(Component.space());
        }
        player.sendMessage(Component.text("手札 " + (handCards.get(index).size() > 1 ? (hand + 1) + " つ目 " : "")
                + "合計 " + total + "： ", NamedTextColor.WHITE).append(buttons));
    }

    private static Component button(Action action) {
        String label = switch (action) {
            case HIT -> "ヒット";
            case STAND -> "スタンド";
            case DOUBLE -> "ダブルダウン";
            case SPLIT -> "スプリット";
        };
        String help = switch (action) {
            case HIT -> "もう 1 枚引く";
            case STAND -> "引かずに止める";
            case DOUBLE -> "賭け金を 2 倍にして 1 枚だけ引く";
            case SPLIT -> "同額を追加で賭けて 2 つの手に分ける";
        };
        NamedTextColor color = switch (action) {
            case HIT -> NamedTextColor.GREEN;
            case STAND -> NamedTextColor.RED;
            case DOUBLE -> NamedTextColor.GOLD;
            case SPLIT -> NamedTextColor.AQUA;
        };
        return Component.text("[" + label + "]", color)
                .hoverEvent(HoverEvent.showText(Component.text(help)))
                .clickEvent(ClickEvent.runCommand("/casino " + action.name().toLowerCase()));
    }

    private void splitViews(int seat, int hand, int newHand) {
        List<List<Card>> cards = handCards.get(seat);
        List<List<CardView>> views = handViews.get(seat);
        while (cards.size() <= newHand) {
            cards.add(new ArrayList<>());
            views.add(new ArrayList<>());
        }
        cards.get(newHand).add(cards.get(hand).remove(1));
        views.get(newHand).add(views.get(hand).remove(1));
        int hands = cards.size();
        for (int h = 0; h < hands; h++) {
            List<CardView> list = views.get(h);
            for (int i = 0; i < list.size(); i++) {
                list.get(i).moveTo(layout.at(layout.playerCard(seat, h, hands, i)));
            }
        }
        List<ChipView> bets = betViews.get(seat);
        long amount = bets.getFirst().amount();
        ChipView second = new ChipView();
        bets.add(second);
        for (int h = 0; h < bets.size(); h++) {
            bets.get(h).show(chipSpot(seat, h), amount);
        }
        List<TextDisplay> labels = handLabels.get(seat);
        labels.add(label(layout.handLabel(seat, newHand, hands)));
        for (int h = 0; h < labels.size(); h++) {
            labels.get(h).teleport(layout.at(layout.handLabel(seat, h, hands)));
            updateHandLabel(seat, h, null);
        }
    }

    private Location chipSpot(int seat, int hand) {
        int hands = handCards.get(seat).size();
        double[] spot = layout.chips(seat);
        double[] card = layout.playerCard(seat, hand, hands, 0);
        return layout.at(card[0], spot[1], spot[2]);
    }

    private void settle(BlackjackEvent.Settled e) {
        Seat seat = seats[e.seat()];
        if (seat != null) {
            seat.atRisk -= e.bet();
            seat.chips += e.payout();
        }
        ChipView bet = betViews.get(e.seat()).get(e.hand());
        String result = switch (e.outcome()) {
            case BLACKJACK -> "ブラックジャック！";
            case WIN -> "勝ち";
            case PUSH -> "引き分け";
            case LOSE -> "負け";
            case BUST -> "バースト";
        };
        NamedTextColor color = switch (e.outcome()) {
            case BLACKJACK, WIN -> NamedTextColor.GREEN;
            case PUSH -> NamedTextColor.YELLOW;
            case LOSE, BUST -> NamedTextColor.RED;
        };
        updateHandLabel(e.seat(), e.hand(), Component.text(result, color));
        switch (e.outcome()) {
            case LOSE, BUST -> bet.slideTo(layout.at(layout.dealerChips()));
            case WIN, BLACKJACK -> {
                ChipView win = new ChipView();
                win.show(layout.at(layout.dealerChips()), e.payout() - e.bet());
                payoutViews.add(win);
                later(2, () -> win.slideTo(payoutSpot(e.seat(), e.hand())));
                playSound(e.outcome() == Outcome.BLACKJACK ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 1.2f);
                if (e.outcome() == Outcome.BLACKJACK) {
                    Location at = layout.at(layout.handLabel(e.seat(), e.hand(), handCards.get(e.seat()).size()));
                    at.getWorld().spawnParticle(Particle.FIREWORK, at, 30, 0.2, 0.2, 0.2, 0.05);
                }
            }
            case PUSH -> {
            }
        }
        if (seat != null) {
            long net = e.payout() - e.bet();
            String sign = net > 0 ? "+" + net : String.valueOf(net);
            send(seat, Component.text(result + "（" + sign + " 枚、手元 " + seat.chips + " 枚）", color));
        }
    }

    /** 払い出したチップを置く位置（賭けたチップの横）。 */
    private Location payoutSpot(int seat, int hand) {
        int hands = handCards.get(seat).size();
        double[] spot = layout.chips(seat);
        double[] card = layout.playerCard(seat, hand, hands, 0);
        return layout.at(card[0] - 0.17, spot[1], spot[2] + 0.1);
    }

    private void finishRound() {
        clearRoundViews();
        newRound();
        state = State.IDLE;
        for (int i = 0; i < seats.length; i++) {
            Seat seat = seats[i];
            if (seat == null) {
                continue;
            }
            seat.confirmed = false;
            if (seat.away) {
                cashOut(i, null);
                continue;
            }
            Player player = Bukkit.getPlayer(seat.player);
            if (player == null) {
                cashOut(i, null);
            } else if (seat.chips < settings.rules().minBet()) {
                player.sendMessage(Component.text("チップが最低額に足りないため、席を立ちます", NamedTextColor.YELLOW));
                cashOut(i, null);
            } else {
                plugin.menus().openBet(player, this);
            }
        }
        updateStatus();
    }

    private void clearRoundViews() {
        for (int i = 0; i < TableLayout.SEATS; i++) {
            handViews.get(i).forEach(list -> list.forEach(CardView::remove));
            handViews.get(i).clear();
            handCards.get(i).clear();
            betViews.get(i).forEach(ChipView::remove);
            betViews.get(i).clear();
            handLabels.get(i).forEach(Entity::remove);
            handLabels.get(i).clear();
        }
        payoutViews.forEach(ChipView::remove);
        payoutViews.clear();
        dealerViews.forEach(CardView::remove);
        dealerViews.clear();
        dealerCards.clear();
        if (dealerLabel != null) {
            dealerLabel.remove();
            dealerLabel = null;
        }
    }

    // ---- 表示の更新 ----

    private TextDisplay label(double[] local) {
        Location at = layout.at(local);
        return at.getWorld().spawn(at, TextDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(0.6f, 0.6f, 0.6f), new AxisAngle4f()));
            entity.setTeleportDuration(CardView.STEP_TICKS);
            entity.text(Component.empty());
        });
    }

    private void updateHandLabel(int seat, int hand, Component result) {
        List<TextDisplay> labels = handLabels.get(seat);
        if (hand >= labels.size()) {
            return;
        }
        List<Card> cards = handCards.get(seat).get(hand);
        Component text = totalText(cards, handCards.get(seat).size() == 1);
        if (result != null) {
            text = text.append(Component.newline()).append(result);
        }
        labels.get(hand).text(text);
    }

    private void updateDealerLabel() {
        if (dealerLabel != null) {
            dealerLabel.text(dealerCards.isEmpty() ? Component.empty() : totalText(dealerCards, dealerViews.size() == dealerCards.size()));
        }
    }

    /** 合計の表示。最初の 2 枚で 21 ならブラックジャック、A を 11 で数えているときはソフトと書く。 */
    private static Component totalText(List<Card> cards, boolean canBeBlackjack) {
        if (cards.isEmpty()) {
            return Component.empty();
        }
        int total = Hand.total(cards);
        if (canBeBlackjack && cards.size() == 2 && total == 21) {
            return Component.text("ブラックジャック", NamedTextColor.GOLD);
        }
        if (total > 21) {
            return Component.text(total + " バースト", NamedTextColor.RED);
        }
        return Component.text((Hand.soft(cards) && total < 21 ? "ソフト " : "") + total, NamedTextColor.WHITE);
    }

    private void updateStatus() {
        if (status == null) {
            return;
        }
        Component text = switch (state) {
            case IDLE -> empty()
                    ? Component.text("ブラックジャック", NamedTextColor.GOLD).append(Component.newline())
                    .append(Component.text("席をクリックして座る", NamedTextColor.GRAY))
                    : Component.text("ベットを待っています", NamedTextColor.AQUA);
            case BETTING -> Component.text("ベット受付中（あと " + Math.max(countdown, 0) + " 秒）", NamedTextColor.AQUA);
            case PLAYING -> {
                int index = round.currentSeat();
                Seat seat = index >= 0 && waitingInput ? seats[index] : null;
                yield seat == null ? Component.text("勝負中", NamedTextColor.YELLOW)
                        : Component.text(seat.name + " の番", NamedTextColor.YELLOW);
            }
        };
        text = text.append(Component.newline()).append(Component.text(
                "賭け金 " + settings.rules().minBet() + "〜" + settings.rules().maxBet()
                        + (bank.practice() ? "（練習用）" : ""), NamedTextColor.GRAY));
        status.text(text);
    }

    private void swing() {
        if (dealer != null) {
            dealer.swingMainHand();
        }
    }

    private void playSound(Sound sound, float pitch) {
        Location at = layout.at(0, TableLayout.TOP, 0);
        at.getWorld().playSound(at, sound, 1f, pitch);
    }

    private void broadcast(Component message) {
        for (Seat seat : seats) {
            if (seat != null && !seat.away) {
                send(seat, message);
            }
        }
    }

    private static void send(Seat seat, Component message) {
        Player player = Bukkit.getPlayer(seat.player);
        if (player != null) {
            player.sendMessage(message);
        }
    }

    /** 再接続時に Geyser 拡張へ送り直すカードの一覧。 */
    void stateLines(List<String> out) {
        handViews.forEach(seat -> seat.forEach(hand -> hand.forEach(card -> out.add(card.stateLine()))));
        dealerViews.forEach(card -> out.add(card.stateLine()));
    }
}
