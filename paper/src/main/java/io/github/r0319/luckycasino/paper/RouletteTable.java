package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.roulette.BetType;
import io.github.r0319.luckycasino.roulette.RouletteBet;
import io.github.r0319.luckycasino.roulette.RouletteRound;
import io.github.r0319.luckycasino.roulette.Wheel;
import net.kyori.adventure.text.Component;
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
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ルーレットのテーブル（ヨーロピアン）。結果を先に乱数で決めてから、ボールがそのポケットに止まるよう
 * ホイールとボールの動きを逆算して見せる。
 */
final class RouletteTable extends CasinoTable {

    enum State {
        IDLE, BETTING, SPINNING, PAYING
    }

    private static final RouletteLayout LAYOUT = new RouletteLayout();
    /** 回してから止まるまで（tick）。 */
    private static final int SPIN_TICKS = 160;
    private static final int STEP = 2;

    private final RouletteSettings settings;
    private final Random random = new SecureRandom();
    private State state = State.IDLE;
    private RouletteRound round;
    private int countdown;

    private ItemDisplay wheel;
    private ItemDisplay ball;
    private TextDisplay resultLabel;
    private TextDisplay historyLabel;
    private float wheelAngle;
    private final Deque<Integer> history = new ArrayDeque<>();
    /** 席ごとの、置いた場所ごとのチップの表示。 */
    private final Map<Integer, Map<String, ChipView>> chips = new HashMap<>();
    private final List<ChipView> payoutViews = new ArrayList<>();

    RouletteTable(LuckyCasinoPaper plugin, String id, Frame frame, UUID owner, Bank bank, RouletteSettings settings) {
        super(plugin, id, GameType.ROULETTE, frame, owner, bank);
        this.settings = settings;
        newRound();
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
        return settings.minBet();
    }

    RouletteSettings settings() {
        return settings;
    }

    State state() {
        return state;
    }

    RouletteRound round() {
        return round;
    }

    private void newRound() {
        round = new RouletteRound(settings.minBet(), settings.maxBet(), settings.maxTotal());
    }

    @Override
    protected void onSpawn() {
        Location wheelAt = frame.at(LAYOUT.wheel());
        float s = RouletteLayout.WHEEL_SCALE;
        wheel = wheelAt.getWorld().spawn(wheelAt, ItemDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setItemStack(Items.model("roulette/wheel"));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setTransformation(wheelTransform(0));
        });
        fixtures().add(wheel);
        Location ballAt = frame.at(LAYOUT.aroundWheel(0, RouletteLayout.BALL_POCKET, 0.045));
        ball = ballAt.getWorld().spawn(ballAt, ItemDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setItemStack(Items.model("roulette/ball"));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.5f, 0.5f, 0.5f), new AxisAngle4f()));
            entity.setTeleportDuration(STEP);
        });
        fixtures().add(ball);
        resultLabel = label(LAYOUT.resultLabel(), 1.0f);
        fixtures().add(resultLabel);
        historyLabel = label(LAYOUT.history(), 0.5f);
        fixtures().add(historyLabel);
        updateHistory();
    }

    private static Transformation wheelTransform(float degrees) {
        float s = RouletteLayout.WHEEL_SCALE;
        return new Transformation(new Vector3f(), new AxisAngle4f((float) Math.toRadians(degrees), 0f, 1f, 0f),
                new Vector3f(s, s, s), new AxisAngle4f());
    }

    @Override
    protected void onDespawn() {
        clearChips();
        newRound();
        state = State.IDLE;
        wheel = null;
        ball = null;
    }

    @Override
    protected void onSeated(int seat, Player player) {
        if (player == null) {
            if (canBet()) {
                later(10 + seat * 4L, () -> botBet(seat));
            }
        } else if (canBet()) {
            plugin.menus().openRoulette(player, this);
        } else {
            player.sendMessage(Component.text("今の回が終わったら賭けられます", NamedTextColor.GRAY));
        }
    }

    @Override
    void openMenu(Player player) {
        plugin.menus().openRoulette(player, this);
    }

    @Override
    protected boolean prepareLeave(int index) {
        Seat seat = seats[index];
        if (canBet() && round.total(index) > 0) {
            long refund = round.clear(index);
            seat.atRisk -= refund;
            seat.chips += refund;
            removeChips(index);
            later(1, this::checkBets);
        }
        return seat.atRisk == 0;
    }

    @Override
    protected boolean canStand(int seat) {
        return state == State.SPINNING || state == State.PAYING || (canBet() && round.total(seat) > 0);
    }

    @Override
    protected double standingRange() {
        return settings.standingRange();
    }

    boolean canBet() {
        return state == State.IDLE || state == State.BETTING;
    }

    /** 賭けを置く。置けなければ理由を返す（null なら成功）。 */
    String place(int index, RouletteBet bet) {
        Seat seat = seats[index];
        if (seat == null || seat.away || !canBet()) {
            return "今は賭けられません";
        }
        if (bet.amount() > seat.chips) {
            return "チップが足りません";
        }
        RouletteBet merged;
        try {
            merged = round.place(index, bet);
        } catch (IllegalArgumentException e) {
            return "1 か所 " + settings.minBet() + "〜" + settings.maxBet() + " 枚、1 回の合計 " + settings.maxTotal() + " 枚までです";
        }
        seat.chips -= bet.amount();
        seat.atRisk += bet.amount();
        writeLedger(seat);
        showChips(index, merged);
        playSound(Sound.BLOCK_CHAIN_PLACE, 1.4f);
        if (state == State.IDLE) {
            state = State.BETTING;
            countdown = settings.bettingSeconds();
            broadcast(Component.text("ベットを受け付けています（あと " + countdown + " 秒）", NamedTextColor.AQUA));
        }
        updateStatus();
        return null;
    }

    /** 自分の賭けを全部取り消す。 */
    void clearBets(int index) {
        Seat seat = seats[index];
        if (seat == null || !canBet()) {
            return;
        }
        long refund = round.clear(index);
        seat.atRisk -= refund;
        seat.chips += refund;
        writeLedger(seat);
        removeChips(index);
        checkBets();
        updateStatus();
    }

    private void checkBets() {
        if (state == State.BETTING && !round.hasBets()) {
            state = State.IDLE;
            updateStatus();
        }
    }

    private void botBet(int index) {
        Seat seat = seats[index];
        if (seat == null || !seat.bot || !canBet()) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int count = 1 + r.nextInt(4);
        for (int i = 0; i < count; i++) {
            long amount = settings.minBet() * (1 + r.nextInt(3));
            RouletteBet bet = switch (r.nextInt(9)) {
                case 0 -> RouletteBet.straight(r.nextInt(37), amount);
                case 1 -> {
                    int n = 1 + r.nextInt(33);
                    yield RouletteBet.split(n, n + 3, amount);
                }
                case 2 -> RouletteBet.street(1 + r.nextInt(12), amount);
                case 3 -> {
                    int n = 1 + 3 * r.nextInt(11) + r.nextInt(2);
                    yield RouletteBet.corner(n, amount);
                }
                case 4 -> RouletteBet.line(1 + r.nextInt(11), amount);
                case 5 -> RouletteBet.dozen(1 + r.nextInt(3), amount);
                case 6 -> RouletteBet.column(1 + r.nextInt(3), amount);
                case 7 -> RouletteBet.split(0, 1 + r.nextInt(3), amount);
                default -> RouletteBet.outside(RouletteLayout.OUTSIDE[r.nextInt(6)], amount);
            };
            place(index, bet);
        }
    }

    // ---- 進行 ----

    @Override
    protected void tickSecond() {
        if (state != State.BETTING) {
            return;
        }
        countdown--;
        if (countdown == 3) {
            broadcast(Component.text("まもなく締め切ります", NamedTextColor.YELLOW));
        }
        if (countdown <= 0) {
            spin();
        }
        updateStatus();
    }

    private void spin() {
        if (!round.hasBets()) {
            state = State.IDLE;
            return;
        }
        state = State.SPINNING;
        plugin.menus().closeRoulette(this);
        broadcast(Component.text("ノーモアベット", NamedTextColor.AQUA));
        int result = round.spin(random);
        resultLabel.text(Component.empty());
        animateSpin(result);
        later(SPIN_TICKS + 10, this::payout);
    }

    /**
     * ホイールは上から見て反時計回り、ボールは時計回りに回し、同時に止める。
     * ポケット i（上から見て時計回り、奥から 360/37 度刻み）は、ホイールを φ 度回すと i×(360/37) − φ 度の位置に来る。
     */
    private void animateSpin(int result) {
        float startWheel = wheelAngle;
        float wheelTurn = 720f + random.nextInt(360);
        float endWheel = startWheel + wheelTurn;
        double target = Wheel.indexOf(result) * (360.0 / Wheel.POCKETS) - endWheel;
        double startBall = random.nextInt(360);
        double ballTurn = 4 * 360 + Math.floorMod((long) Math.round(target - startBall), 360);
        double ballOffset = (target - startBall) - Math.round(target - startBall);
        playSound(Sound.BLOCK_GRINDSTONE_USE, 1.6f);
        for (int t = 0; t <= SPIN_TICKS; t += STEP) {
            double progress = (double) t / SPIN_TICKS;
            float w = (float) (startWheel + wheelTurn * easeOut(progress, 2));
            double b = startBall + (ballTurn + ballOffset) * easeOut(progress, 3);
            double radius;
            double height = 0.045;
            if (progress < 0.7) {
                radius = RouletteLayout.BALL_TRACK;
            } else {
                double k = (progress - 0.7) / 0.3;
                radius = RouletteLayout.BALL_TRACK + (RouletteLayout.BALL_POCKET - RouletteLayout.BALL_TRACK) * Math.min(1, k * 1.3);
                // ポケットに落ちる直前に何度か跳ねる
                if (k < 0.8) {
                    height += Math.abs(Math.sin(k * Math.PI * 4)) * 0.04 * (1 - k);
                }
            }
            int tick = t;
            double[] pos = LAYOUT.aroundWheel(b, radius, height);
            later(Math.max(1, tick), () -> {
                if (wheel != null) {
                    wheel.setInterpolationDelay(0);
                    wheel.setInterpolationDuration(STEP);
                    wheel.setTransformation(wheelTransform(w));
                }
                if (ball != null) {
                    ball.teleport(frame.at(pos));
                }
                if (tick > SPIN_TICKS * 0.7 && tick % 12 == 0 && tick < SPIN_TICKS * 0.95) {
                    playSound(Sound.BLOCK_BAMBOO_HIT, 1.8f);
                }
            });
        }
        wheelAngle = endWheel % 360f;
    }

    private static double easeOut(double x, int power) {
        return 1 - Math.pow(1 - x, power);
    }

    private void payout() {
        state = State.PAYING;
        int result = round.result();
        history.addFirst(result);
        while (history.size() > 12) {
            history.removeLast();
        }
        updateHistory();
        Component number = numberText(result);
        resultLabel.text(number);
        playSound(Sound.BLOCK_NOTE_BLOCK_BELL, 1.2f);
        Location at = frame.at(LAYOUT.resultLabel());
        at.getWorld().spawnParticle(Particle.END_ROD, at, 15, 0.2, 0.2, 0.2, 0.02);

        Map<Integer, long[]> totals = new HashMap<>();
        for (RouletteRound.Payout payout : round.payouts()) {
            Seat seat = seats[payout.seat()];
            long bet = payout.bet().amount();
            if (seat != null) {
                seat.atRisk -= bet;
                seat.chips += payout.payout();
            }
            if (observer != null) {
                observer.payout(payout.seat(), bet, payout.payout());
            }
            totals.computeIfAbsent(payout.seat(), k -> new long[2]);
            totals.get(payout.seat())[0] += bet;
            totals.get(payout.seat())[1] += payout.payout();
            ChipView view = chips.getOrDefault(payout.seat(), Map.of()).get(key(payout.bet()));
            if (view == null) {
                continue;
            }
            if (payout.payout() == 0) {
                view.slideTo(frame.at(LAYOUT.croupierChips()));
            } else {
                ChipView win = new ChipView();
                win.show(frame.at(LAYOUT.croupierChips()), payout.payout() - bet);
                payoutViews.add(win);
                double[] spot = offset(LAYOUT.spot(payout.bet()), payout.seat());
                later(3, () -> win.slideTo(frame.at(spot[0] + 0.05, spot[1], spot[2] - 0.05)));
            }
        }
        totals.forEach((index, sums) -> {
            Seat seat = seats[index];
            if (seat == null) {
                return;
            }
            writeLedger(seat);
            long net = sums[1] - sums[0];
            Player player = online(seat);
            if (player != null) {
                player.showTitle(Title.title(number, Component.text(net > 0 ? "+" + net + " 枚" : net == 0 ? "±0" : net + " 枚",
                                net > 0 ? NamedTextColor.GREEN : net == 0 ? NamedTextColor.YELLOW : NamedTextColor.RED),
                        Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(2000), Duration.ofMillis(400))));
                if (net > 0) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                }
                player.sendMessage(Component.text("結果 ", NamedTextColor.WHITE).append(number)
                        .append(Component.text("：払い戻し " + sums[1] + " 枚（手元 " + seat.chips + " 枚）", NamedTextColor.GRAY)));
            }
        });
        later(80, this::finishRound);
    }

    private void finishRound() {
        clearChips();
        newRound();
        state = State.IDLE;
        settleAwaySeats();
        if (observer != null) {
            observer.roundFinished();
        }
        for (int i = 0; i < seats.length; i++) {
            Seat seat = seats[i];
            if (seat == null) {
                continue;
            }
            if (seat.bot) {
                int index = i;
                later(10 + i * 4L, () -> botBet(index));
            } else {
                Player player = online(seat);
                if (player != null) {
                    player.sendMessage(Component.text("次の回のベットを受け付けています", NamedTextColor.AQUA));
                    plugin.menus().openRoulette(player, this);
                }
            }
        }
        updateStatus();
    }

    // ---- 表示 ----

    private static String key(RouletteBet bet) {
        return bet.type() + ":" + bet.anchor();
    }

    /** 席ごとに少しずらして、同じ場所の他の人のチップと重ならないようにする。 */
    private static double[] offset(double[] spot, int seat) {
        double d = (seat - 2.5) * 0.012;
        return new double[]{spot[0] + d, spot[1] + seat * 0.0005, spot[2] + d * 0.5};
    }

    private void showChips(int index, RouletteBet bet) {
        ChipView view = chips.computeIfAbsent(index, k -> new HashMap<>()).computeIfAbsent(key(bet), k -> new ChipView());
        view.show(frame.at(offset(LAYOUT.spot(bet), index)), bet.amount(), false);
    }

    private void removeChips(int index) {
        Map<String, ChipView> views = chips.remove(index);
        if (views != null) {
            views.values().forEach(ChipView::remove);
        }
    }

    private void clearChips() {
        chips.values().forEach(map -> map.values().forEach(ChipView::remove));
        chips.clear();
        payoutViews.forEach(ChipView::remove);
        payoutViews.clear();
    }

    static Component numberText(int n) {
        NamedTextColor color = n == 0 ? NamedTextColor.GREEN : Wheel.red(n) ? NamedTextColor.RED : NamedTextColor.WHITE;
        String name = n == 0 ? "緑" : Wheel.red(n) ? "赤" : "黒";
        return Component.text(n + " " + name, color);
    }

    private void updateHistory() {
        if (historyLabel == null) {
            return;
        }
        Component text = Component.text("これまでの結果", NamedTextColor.GOLD);
        Component line = Component.empty();
        for (int n : history) {
            NamedTextColor color = n == 0 ? NamedTextColor.GREEN : Wheel.red(n) ? NamedTextColor.RED : NamedTextColor.WHITE;
            line = line.append(Component.text(n + " ", color));
        }
        historyLabel.text(text.append(Component.newline()).append(history.isEmpty() ? Component.text("-", NamedTextColor.GRAY) : line));
    }

    @Override
    protected Component statusText() {
        Component text = switch (state) {
            case IDLE -> empty()
                    ? Component.text("ルーレット", NamedTextColor.GOLD).append(Component.newline())
                    .append(Component.text("椅子をクリックして座る", NamedTextColor.GRAY))
                    : Component.text("ベットを待っています", NamedTextColor.AQUA);
            case BETTING -> Component.text("ベット受付中（あと " + Math.max(countdown, 0) + " 秒）", NamedTextColor.AQUA);
            case SPINNING -> Component.text("ノーモアベット", NamedTextColor.YELLOW);
            case PAYING -> Component.text("払い戻し中", NamedTextColor.YELLOW);
        };
        return text.append(limitsLine("1 か所 " + settings.minBet() + "〜" + settings.maxBet() + "、合計 " + settings.maxTotal() + " まで"));
    }

    /** 外側の賭けの種類（メニュー用）。 */
    static RouletteBet outside(BetType type, long amount) {
        return RouletteBet.outside(type, amount);
    }
}
