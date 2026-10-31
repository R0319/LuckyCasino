package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.card.Card;
import io.github.r0319.luckycasino.card.Shoe;
import io.github.r0319.luckycasino.poker.HandValue;
import io.github.r0319.luckycasino.poker.Legal;
import io.github.r0319.luckycasino.poker.PokerAction;
import io.github.r0319.luckycasino.poker.PokerEvent;
import io.github.r0319.luckycasino.poker.PokerRound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * テキサスホールデム（ノーリミット）のテーブル。ディーラーは配るだけで勝負には加わらず、チップは席の間で動く。
 * 勝負の間は手元のチップを全部 atRisk に移し（控えの合計は変わらない）、終わったら残りを手元に戻す。
 */
final class PokerTable extends CasinoTable {

    enum State {
        /** 2 人そろうのを待っている */
        IDLE,
        /** 次の勝負を配るまで数えている */
        STARTING,
        /** 勝負中 */
        PLAYING
    }

    private static final PokerLayout LAYOUT = new PokerLayout();
    private static final int SEATS = PokerLayout.SEATS;

    private final PokerSettings settings;
    private final Shoe deck = new Shoe(1, 0.5, new SecureRandom());
    private State state = State.IDLE;
    private PokerRound round;
    private int button = -1;
    private final boolean[] inHand = new boolean[SEATS];
    private final Deque<PokerEvent> queue = new ArrayDeque<>();
    private boolean waitingInput;
    private boolean pumping;
    private int countdown;
    private Legal legal;

    // 表示（イベントを再生した時点の状態。エンジンはその先まで進んでいることがある）
    private ItemDisplay buttonDisplay;
    private final List<List<PrivateCardView>> holeViews = new ArrayList<>();
    private final List<CardView> boardViews = new ArrayList<>();
    private final ChipView[] betViews = new ChipView[SEATS];
    private final ChipView potView = new ChipView();
    private final List<ChipView> payoutViews = new ArrayList<>();
    private final TextDisplay[] seatLabels = new TextDisplay[SEATS];
    private TextDisplay potLabel;
    private final long[] shownStack = new long[SEATS];
    private final long[] shownBet = new long[SEATS];
    private final Component[] lastAction = new Component[SEATS];
    private final boolean[] allIn = new boolean[SEATS];
    private final boolean[] folded = new boolean[SEATS];
    private long shownPot;

    PokerTable(LuckyCasinoPaper plugin, String id, Frame frame, UUID owner, Bank bank, PokerSettings settings) {
        super(plugin, id, GameType.POKER, frame, owner, bank);
        this.settings = settings;
        for (int i = 0; i < SEATS; i++) {
            holeViews.add(new ArrayList<>());
            betViews[i] = new ChipView();
        }
    }

    @Override
    TableShape shape() {
        return LAYOUT;
    }

    @Override
    List<Long> buyIns() {
        return settings.buyIns();
    }

    @Override
    long minChips() {
        return settings.bigBlind();
    }

    PokerSettings settings() {
        return settings;
    }

    State state() {
        return state;
    }

    @Override
    protected void onSpawn() {
        Location at = frame.at(LAYOUT.button(0));
        buttonDisplay = at.getWorld().spawn(at, ItemDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setItemStack(Items.model("poker/button"));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(0.3f, 0.3f, 0.3f), new AxisAngle4f()));
            entity.setTeleportDuration(8);
        });
        fixtures().add(buttonDisplay);
    }

    @Override
    protected void onDespawn() {
        queue.clear();
        waitingInput = false;
        pumping = false;
        clearHandViews();
        round = null;
        state = State.IDLE;
        Arrays.fill(inHand, false);
        buttonDisplay = null;
    }

    @Override
    protected void onSeated(int seat, Player player) {
        if (player != null) {
            player.sendMessage(L10n.tr("ブラインド %s/%s。2 人以上そろうと配り始めます", settings.smallBlind(), settings.bigBlind()).color(NamedTextColor.AQUA));
            if (state == State.PLAYING) {
                player.sendMessage(L10n.tr("今の勝負が終わったら参加します").color(NamedTextColor.GRAY));
            }
        }
        maybeStart();
    }

    @Override
    void openMenu(Player player) {
        int index = seatOf(player.getUniqueId());
        if (index >= 0 && waitingInput && round != null && round.currentSeat() == index) {
            plugin.menus().openRaise(player, this);
        } else {
            plugin.menus().openSeatInfo(player, this);
        }
    }

    @Override
    protected boolean prepareLeave(int index) {
        return !(state == State.PLAYING && inHand[index]);
    }

    @Override
    protected void onAway(int index) {
        if (waitingInput && round != null && round.currentSeat() == index) {
            apply(index, () -> round.timeout(index));
        }
    }

    // ---- 始める ----

    private boolean eligible(int index) {
        Seat seat = seats[index];
        return seat != null && !seat.away && seat.chips >= settings.bigBlind() && (seat.bot || online(seat) != null);
    }

    private int eligibleCount() {
        int count = 0;
        for (int i = 0; i < SEATS; i++) {
            if (eligible(i)) {
                count++;
            }
        }
        return count;
    }

    private void maybeStart() {
        if (state == State.IDLE && eligibleCount() >= 2) {
            state = State.STARTING;
            countdown = settings.startSeconds();
            updateStatus();
        }
    }

    private void startHand() {
        Map<Integer, Long> stacks = new LinkedHashMap<>();
        for (int i = 0; i < SEATS; i++) {
            if (eligible(i)) {
                stacks.put(i, seats[i].chips);
            }
        }
        if (stacks.size() < 2) {
            state = State.IDLE;
            updateStatus();
            return;
        }
        // ボタンは前回の左隣（時計回り）の参加者へ
        int next = button;
        do {
            next = (next + 1 + SEATS) % SEATS;
        } while (!stacks.containsKey(next));
        button = next;
        deck.shuffle();
        round = new PokerRound(stacks, SEATS, button, settings.smallBlind(), settings.bigBlind(), deck);
        state = State.PLAYING;
        shownPot = 0;
        stacks.forEach((index, stack) -> {
            Seat seat = seats[index];
            seat.atRisk = stack;
            seat.chips = 0;
            inHand[index] = true;
            shownStack[index] = stack;
            shownBet[index] = 0;
            lastAction[index] = Component.empty();
            allIn[index] = false;
            folded[index] = false;
            seatLabels[index] = label(LAYOUT.seatLabel(index), 0.6f);
            updateSeatLabel(index);
        });
        potLabel = label(LAYOUT.potLabel(), 0.6f);
        broadcast(L10n.tr("新しい勝負を配ります（%s 人）", stacks.size()).color(NamedTextColor.AQUA));
        enqueue(round.start());
    }

    // ---- 1 秒ごと ----

    @Override
    protected void tickSecond() {
        if (state == State.STARTING) {
            if (eligibleCount() < 2) {
                state = State.IDLE;
            } else if (--countdown <= 0) {
                startHand();
            }
            updateStatus();
        } else if (state == State.PLAYING && waitingInput && round != null) {
            countdown--;
            int index = round.currentSeat();
            Seat seat = index >= 0 ? seats[index] : null;
            Player player = online(seat);
            if (player != null) {
                player.sendActionBar(L10n.tr("あなたの番です（あと %s 秒）", Math.max(countdown, 0)).color(NamedTextColor.YELLOW));
            }
            if (countdown <= 0 && index >= 0) {
                if (seat != null) {
                    send(seat, L10n.tr(legal != null && legal.canCheck() ? "時間切れのため、チェックしました" : "時間切れのため、フォールドしました")
                            .color(NamedTextColor.GRAY));
                }
                apply(index, () -> round.timeout(index));
            }
        }
    }

    // ---- 行動 ----

    /** 今の番の人が選べる行動（メニュー用）。番でなければ null。 */
    Legal legalFor(Player player) {
        int index = seatOf(player.getUniqueId());
        return index >= 0 && waitingInput && round != null && round.currentSeat() == index ? legal : null;
    }

    long pot() {
        return round == null ? 0 : round.pot();
    }

    /** /casino fold|check|call|allin から。 */
    void act(Player player, PokerAction action) {
        int index = turnOf(player);
        if (index < 0) {
            return;
        }
        switch (action) {
            case FOLD -> apply(index, () -> round.fold(index));
            case CHECK -> apply(index, () -> round.check(index));
            case CALL -> apply(index, () -> round.call(index));
            default -> openRaise(player);
        }
    }

    void allIn(Player player) {
        int index = turnOf(player);
        if (index >= 0) {
            apply(index, () -> round.allIn(index));
        }
    }

    /** このラウンドで出す合計を amount にするベット・レイズ。 */
    void raiseTo(Player player, long amount) {
        int index = turnOf(player);
        if (index < 0) {
            return;
        }
        if (!legal.canRaise()) {
            player.sendMessage(L10n.tr("今はレイズできません").color(NamedTextColor.RED));
            return;
        }
        long to = Math.max(legal.minRaiseTo(), Math.min(amount, legal.maxRaiseTo()));
        apply(index, () -> round.raiseTo(index, to));
    }

    void openRaise(Player player) {
        if (turnOf(player) >= 0) {
            plugin.menus().openRaise(player, this);
        }
    }

    private int turnOf(Player player) {
        int index = seatOf(player.getUniqueId());
        if (index < 0 || !waitingInput || round == null || round.currentSeat() != index) {
            player.sendMessage(L10n.tr("今はあなたの番ではありません").color(NamedTextColor.RED));
            return -1;
        }
        return index;
    }

    private void apply(int index, Supplier<List<PokerEvent>> action) {
        if (!waitingInput || round == null || round.currentSeat() != index) {
            return;
        }
        List<PokerEvent> events;
        try {
            events = action.get();
        } catch (IllegalStateException | IllegalArgumentException e) {
            send(seats[index], L10n.tr("その行動は今は選べません").color(NamedTextColor.RED));
            return;
        }
        waitingInput = false;
        enqueue(events);
    }

    private void enqueue(List<PokerEvent> events) {
        queue.addAll(events);
        pump();
    }

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

    private long play(PokerEvent event) {
        return switch (event) {
            case PokerEvent.ButtonMoved e -> {
                buttonDisplay.teleport(frame.at(LAYOUT.button(e.seat())));
                yield 8;
            }
            case PokerEvent.Blind e -> {
                shownStack[e.seat()] -= e.amount();
                shownBet[e.seat()] = e.amount();
                lastAction[e.seat()] = Component.text((e.big() ? "BB " : "SB ") + e.amount());
                showBet(e.seat());
                updateSeatLabel(e.seat());
                playSound(Sound.BLOCK_CHAIN_PLACE, 1.4f);
                yield 6;
            }
            case PokerEvent.HoleCards e -> {
                dealHole(e.seat(), 0, e.first());
                later(4, () -> dealHole(e.seat(), 1, e.second()));
                swing();
                Player player = online(seats[e.seat()]);
                if (player != null) {
                    player.sendMessage(L10n.tr("あなたの手札: ").color(NamedTextColor.WHITE)
                            .append(cardText(e.first())).append(Component.space()).append(cardText(e.second())));
                }
                yield 9;
            }
            case PokerEvent.Turn e -> {
                startTurn(e.seat(), e.legal());
                yield 0;
            }
            case PokerEvent.Acted e -> {
                acted(e);
                yield 8;
            }
            case PokerEvent.BetsCollected e -> {
                Location pot = frame.at(LAYOUT.pot());
                List<ChipView> moving = new ArrayList<>();
                for (int i = 0; i < SEATS; i++) {
                    if (betViews[i].amount() > 0) {
                        betViews[i].slideTo(pot);
                        moving.add(betViews[i]);
                        payoutViews.add(betViews[i]);
                        betViews[i] = new ChipView();
                    }
                    shownBet[i] = 0;
                }
                shownPot = e.pot();
                later(9, () -> {
                    moving.forEach(ChipView::remove);
                    potView.showPlain(frame.at(LAYOUT.pot()), shownPot);
                });
                updatePotLabel();
                playSound(Sound.BLOCK_CHAIN_PLACE, 1.0f);
                yield 12;
            }
            case PokerEvent.Board e -> {
                int first = boardViews.size();
                for (int k = 0; k < e.cards().size(); k++) {
                    int index = first + k;
                    Card card = e.cards().get(k);
                    later(1 + k * 6L, () -> {
                        double[] at = LAYOUT.board(index);
                        boardViews.add(CardView.deal(plugin, plugin.bridge(), frame.at(at), Frame.delta(LAYOUT.deck(), at), card.code(), true));
                    });
                }
                swing();
                for (int i = 0; i < SEATS; i++) {
                    if (inHand[i] && !folded[i] && !allIn[i]) {
                        lastAction[i] = Component.empty();
                        updateSeatLabel(i);
                    }
                }
                String name = switch (e.street()) {
                    case FLOP -> "フロップ";
                    case TURN -> "ターン";
                    case RIVER -> "リバー";
                    case PREFLOP -> "";
                };
                Component line = L10n.tr("%s: ", L10n.tr(name)).color(NamedTextColor.AQUA);
                for (Card card : e.cards()) {
                    line = line.append(cardText(card)).append(Component.space());
                }
                broadcast(line);
                yield 8 + e.cards().size() * 6L;
            }
            case PokerEvent.Refund e -> {
                shownPot -= e.amount();
                shownStack[e.seat()] += e.amount();
                potView.showPlain(frame.at(LAYOUT.pot()), shownPot);
                updatePotLabel();
                updateSeatLabel(e.seat());
                send(seats[e.seat()], L10n.tr("誰も合わせなかった %s 枚が戻りました", e.amount()).color(NamedTextColor.GRAY));
                yield 6;
            }
            case PokerEvent.Shown e -> {
                holeViews.get(e.seat()).forEach(PrivateCardView::reveal);
                lastAction[e.seat()] = L10n.tr(e.value().displayName());
                updateSeatLabel(e.seat());
                Seat seat = seats[e.seat()];
                unlock(seat, e.value());
                broadcast(L10n.tr("%s: ", seatName(e.seat())).color(NamedTextColor.WHITE)
                        .append(cardText(e.first())).append(Component.space()).append(cardText(e.second()))
                        .append(Component.space()).append(L10n.tr(e.value().displayName()).color(NamedTextColor.GOLD)));
                yield 16;
            }
            case PokerEvent.Won e -> {
                won(e);
                yield 24;
            }
            case PokerEvent.HandOver e -> {
                Map<Integer, Long> stacks = round.stacks();
                for (int i = 0; i < SEATS; i++) {
                    Seat seat = seats[i];
                    if (inHand[i] && seat != null) {
                        achieve(seat, Achievements.Achievement.FIRST_PLAY);
                        seat.chips = stacks.get(i);
                        seat.atRisk = 0;
                        writeLedger(seat);
                    }
                }
                later(70, this::finishHand);
                yield 0;
            }
        };
    }

    private void unlock(Seat seat, HandValue value) {
        switch (value.category()) {
            case FOUR_OF_A_KIND -> achieve(seat, Achievements.Achievement.FOUR_OF_A_KIND);
            case STRAIGHT_FLUSH -> achieve(seat, value.royal()
                    ? Achievements.Achievement.ROYAL_FLUSH : Achievements.Achievement.STRAIGHT_FLUSH);
            default -> {
            }
        }
    }

    private void dealHole(int index, int k, Card card) {
        double[] at = LAYOUT.holeCard(index, k);
        float yaw = PokerLayout.yawOffset(index);
        holeViews.get(index).add(PrivateCardView.deal(plugin, frame.at(at, yaw), Frame.delta(LAYOUT.deck(), at, yaw),
                card.code(), online(seats[index])));
    }

    private void acted(PokerEvent.Acted e) {
        int index = e.seat();
        shownStack[index] -= e.added();
        shownBet[index] = e.streetBet();
        Component text = switch (e.action()) {
            case FOLD -> L10n.tr("フォールド");
            case CHECK -> L10n.tr("チェック");
            case CALL -> L10n.tr("コール %s", e.added());
            case BET -> L10n.tr("ベット %s", e.streetBet());
            case RAISE -> L10n.tr("レイズ %s", e.streetBet());
        };
        if (e.allIn()) {
            text = L10n.tr("オールイン %s", e.streetBet());
        }
        allIn[index] = e.allIn();
        lastAction[index] = text;
        if (e.action() == PokerAction.FOLD) {
            folded[index] = true;
            Location muck = frame.at(LAYOUT.deck());
            holeViews.get(index).forEach(card -> card.muck(muck));
            holeViews.get(index).clear();
            playSound(Sound.ITEM_BOOK_PAGE_TURN, 0.7f);
        } else if (e.action() == PokerAction.CHECK) {
            playSound(Sound.BLOCK_WOOD_HIT, 1.2f);
        } else {
            showBet(index);
            playSound(Sound.BLOCK_CHAIN_PLACE, e.allIn() ? 0.8f : 1.4f);
        }
        updateSeatLabel(index);
        updatePotLabel();
        Seat seat = seats[index];
        broadcast(L10n.tr("%s: ", seatName(index)).append(text).color(e.allIn() ? NamedTextColor.GOLD : NamedTextColor.GRAY));
    }

    private void won(PokerEvent.Won e) {
        int index = e.seat();
        shownPot -= e.amount();
        shownStack[index] += e.amount();
        potView.showPlain(frame.at(LAYOUT.pot()), Math.max(shownPot, 0));
        ChipView win = new ChipView();
        win.show(frame.at(LAYOUT.pot()), e.amount());
        payoutViews.add(win);
        Location target = frame.at(LAYOUT.bet(index));
        later(2, () -> win.slideTo(target));
        lastAction[index] = (e.value() == null ? Component.empty() : L10n.tr(e.value().displayName()).append(Component.space()))
                .append(Component.text("+" + e.amount()));
        updateSeatLabel(index);
        updatePotLabel();
        Seat seat = seats[index];
        Component name = seatName(index);
        Component pot = e.pot() == 0 ? L10n.tr("ポット") : L10n.tr("サイドポット %s", e.pot());
        broadcast(L10n.tr("%s が%s %s 枚を獲得", name, pot, e.amount())
                .append(e.value() == null ? Component.empty() : L10n.tr("（%s）", L10n.tr(e.value().displayName())))
                .color(NamedTextColor.GREEN));
        Player player = online(seat);
        if (player != null) {
            player.showTitle(Title.title(L10n.tr("獲得 +%s 枚", e.amount()).color(NamedTextColor.GREEN),
                    L10n.tr(e.value() == null ? "全員が降りました" : e.value().displayName()).color(NamedTextColor.GOLD),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1800), Duration.ofMillis(400))));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        }
        Location at = frame.at(LAYOUT.seatLabel(index));
        at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 12, 0.3, 0.2, 0.3, 0.02);
    }

    private void finishHand() {
        clearHandViews();
        round = null;
        legal = null;
        state = State.IDLE;
        Arrays.fill(inHand, false);
        settleAwaySeats();
        if (observer != null) {
            observer.roundFinished();
        }
        maybeStart();
        if (state == State.STARTING) {
            broadcast(L10n.tr("%s 秒後に次の勝負を配ります", settings.startSeconds()).color(NamedTextColor.GRAY));
        }
        updateStatus();
    }

    private void startTurn(int index, Legal legal) {
        this.legal = legal;
        waitingInput = true;
        countdown = settings.turnSeconds();
        updateStatus();
        for (int i = 0; i < SEATS; i++) {
            updateSeatLabel(i);
        }
        Seat seat = seats[index];
        if (seat != null && seat.bot && !seat.away) {
            later(12 + ThreadLocalRandom.current().nextInt(10), () -> botAct(index));
            return;
        }
        Player player = online(seat);
        if (seat == null || seat.away || player == null) {
            later(10, () -> apply(index, () -> round.timeout(index)));
            return;
        }
        player.showTitle(Title.title(L10n.tr("あなたの番").color(NamedTextColor.YELLOW),
                (legal.canCheck() ? L10n.tr("チェックできます") : L10n.tr("コール %s 枚", legal.toCall())).color(NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1200), Duration.ofMillis(300))));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.6f);
        player.sendMessage(actionButtons(legal));
    }

    private Component seatName(int index) {
        Seat seat = seats[index];
        return seat == null ? L10n.tr("席 %s", index + 1) : Component.text(seat.name);
    }

    /** チャットに出す行動のボタン。 */
    private Component actionButtons(Legal legal) {
        Component line = L10n.tr("ポット %s 枚： ", round.pot()).color(NamedTextColor.WHITE);
        if (legal.toCall() > 0) {
            line = line.append(button(L10n.tr("フォールド"), L10n.tr("降りる"), "fold", NamedTextColor.RED)).append(Component.space());
            line = line.append(button(legal.callIsAllIn() ? L10n.tr("コール（オールイン %s）", legal.toCall()) : L10n.tr("コール %s", legal.toCall()),
                    L10n.tr("同じ額を出す"), "call", NamedTextColor.GREEN)).append(Component.space());
        } else {
            line = line.append(button(L10n.tr("チェック"), L10n.tr("賭けずに回す"), "check", NamedTextColor.GREEN)).append(Component.space());
        }
        if (legal.canRaise()) {
            boolean bet = legal.toCall() == 0 && legal.streetBet() == 0;
            line = line.append(button(L10n.tr(bet ? "ベット %s" : "レイズ %s", legal.minRaiseTo()),
                    L10n.tr(bet ? "最低額でベット（このラウンドの合計）" : "最低額でレイズ（このラウンドの合計）"),
                    "raise " + legal.minRaiseTo(), NamedTextColor.GOLD)).append(Component.space());
            long potTo = potRaise(legal);
            if (potTo > legal.minRaiseTo() && potTo < legal.maxRaiseTo()) {
                line = line.append(button(L10n.tr("ポット %s", potTo), L10n.tr(bet ? "ポットと同じ額でベット" : "ポットと同じ額でレイズ"),
                                "raise " + potTo, NamedTextColor.GOLD))
                        .append(Component.space());
            }
            line = line.append(button(L10n.tr("金額を選ぶ"), L10n.tr("メニューで額を決める"), "raise", NamedTextColor.YELLOW)).append(Component.space());
        }
        if (legal.maxRaiseTo() > legal.streetBet() && (legal.canRaise() || !legal.callIsAllIn())) {
            line = line.append(button(L10n.tr("オールイン"), L10n.tr("手元のチップを全部出す（%s 枚）", legal.maxRaiseTo() - legal.streetBet()),
                    "allin", NamedTextColor.DARK_RED));
        }
        return line;
    }

    /** ポットと同じ額のレイズ（コールした後のポット分を上乗せ）の、このラウンドの合計。 */
    long potRaise(Legal legal) {
        long currentBet = legal.streetBet() + legal.toCall();
        return Math.max(legal.minRaiseTo(), Math.min(legal.maxRaiseTo(), currentBet + round.pot() + legal.toCall()));
    }

    private static Component button(Component label, Component help, String command, NamedTextColor color) {
        return Component.text("[", color).append(label).append(Component.text("]"))
                .hoverEvent(HoverEvent.showText(help))
                .clickEvent(ClickEvent.runCommand("/casino " + command));
    }

    /** ボットの行動。オールインやサイドポットも起きるよう、少し荒く打つ。 */
    private void botAct(int index) {
        if (!waitingInput || round == null || round.currentSeat() != index) {
            return;
        }
        Legal l = legal;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int roll = r.nextInt(100);
        Supplier<List<PokerEvent>> action;
        if (roll < 3) {
            action = () -> round.allIn(index);
        } else if (l.canCheck()) {
            if (l.canRaise() && roll < 25) {
                long to = Math.min(l.maxRaiseTo(), l.minRaiseTo() * (1 + r.nextInt(3)));
                action = () -> round.raiseTo(index, to);
            } else {
                action = () -> round.check(index);
            }
        } else if (roll < 20 && l.toCall() > settings.bigBlind()) {
            action = () -> round.fold(index);
        } else if (roll < 32 && l.canRaise()) {
            long to = l.minRaiseTo();
            action = () -> round.raiseTo(index, to);
        } else {
            action = () -> round.call(index);
        }
        apply(index, action);
    }

    // ---- 表示 ----

    private void showBet(int index) {
        betViews[index].show(frame.at(LAYOUT.bet(index)), shownBet[index]);
    }

    private void updateSeatLabel(int index) {
        TextDisplay label = seatLabels[index];
        Seat seat = seats[index];
        if (label == null) {
            return;
        }
        boolean turn = waitingInput && round != null && round.currentSeat() == index;
        Component text = Component.text(index == button ? "Ⓓ " : "").append(seatName(index))
                .color(turn ? NamedTextColor.YELLOW : folded[index] ? NamedTextColor.DARK_GRAY : NamedTextColor.WHITE);
        text = text.append(Component.newline()).append(L10n.tr("%s 枚", shownStack[index]).color(NamedTextColor.GOLD));
        if (lastAction[index] != null && !lastAction[index].equals(Component.empty())) {
            text = text.append(Component.newline()).append(lastAction[index].color(
                    folded[index] ? NamedTextColor.DARK_GRAY : NamedTextColor.AQUA));
        }
        label.text(text);
    }

    private void updatePotLabel() {
        if (potLabel != null) {
            long total = shownPot;
            for (long bet : shownBet) {
                total += bet;
            }
            potLabel.text(total > 0 ? L10n.tr("ポット %s", total).color(NamedTextColor.GOLD) : Component.empty());
        }
    }

    private void clearHandViews() {
        holeViews.forEach(list -> {
            list.forEach(PrivateCardView::remove);
            list.clear();
        });
        boardViews.forEach(CardView::remove);
        boardViews.clear();
        for (int i = 0; i < SEATS; i++) {
            betViews[i].remove();
            if (seatLabels[i] != null) {
                seatLabels[i].remove();
                seatLabels[i] = null;
            }
        }
        potView.remove();
        payoutViews.forEach(ChipView::remove);
        payoutViews.clear();
        if (potLabel != null) {
            potLabel.remove();
            potLabel = null;
        }
        Arrays.fill(shownBet, 0);
        shownPot = 0;
    }

    static Component cardText(Card card) {
        String suit = switch (card.suit()) {
            case SPADE -> "♠";
            case HEART -> "♥";
            case DIAMOND -> "♦";
            case CLUB -> "♣";
        };
        String rank = switch (card.rank()) {
            case ACE -> "A";
            case JACK -> "J";
            case QUEEN -> "Q";
            case KING -> "K";
            default -> String.valueOf(card.rank().ordinal() + 1);
        };
        NamedTextColor color = switch (card.suit()) {
            case HEART, DIAMOND -> NamedTextColor.RED;
            default -> NamedTextColor.WHITE;
        };
        return Component.text(suit + rank, color);
    }

    @Override
    protected Component statusText() {
        Component text = switch (state) {
            case IDLE -> empty()
                    ? L10n.tr("ポーカー（テキサスホールデム）").color(NamedTextColor.GOLD).append(Component.newline())
                    .append(L10n.tr("椅子をクリックして座る").color(NamedTextColor.GRAY))
                    : L10n.tr("あと 1 人そろうと始まります").color(NamedTextColor.AQUA);
            case STARTING -> L10n.tr("まもなく配ります（%s 秒）", Math.max(countdown, 0)).color(NamedTextColor.AQUA);
            case PLAYING -> {
                int index = round == null ? -1 : round.currentSeat();
                Seat seat = index >= 0 && waitingInput ? seats[index] : null;
                yield seat == null ? L10n.tr("勝負中").color(NamedTextColor.YELLOW)
                        : L10n.tr("%s の番（あと %s 秒）", seat.name, Math.max(countdown, 0)).color(NamedTextColor.YELLOW);
            }
        };
        return text.append(limitsLine(L10n.tr("ブラインド %s/%s", settings.smallBlind(), settings.bigBlind())));
    }

    @Override
    void stateLines(List<String> out) {
        boardViews.forEach(card -> out.add(card.stateLine()));
    }
}
