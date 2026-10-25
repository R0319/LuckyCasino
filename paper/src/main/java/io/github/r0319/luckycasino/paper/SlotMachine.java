package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.slot.Line;
import io.github.r0319.luckycasino.slot.Reels;
import io.github.r0319.luckycasino.slot.Spin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * スロットマシン（1 人用、リール 3 本・5 ライン固定）。止まる位置を先に乱数で決め、
 * リールがそこに止まるよう動きを逆算して見せる。賭け金は 5 本のラインに等分する。
 */
final class SlotMachine extends CasinoTable {

    enum State {
        IDLE, SPINNING, SHOWING
    }

    private static final SlotLayout LAYOUT = new SlotLayout();
    private static final int STEP = 2;
    /** 回っているときの速さ（度 / tick）。2 tick で 180 度を超えないこと（補間が逆回りになる）。 */
    private static final double SPEED = 67.5;
    private static final double FACE_DEGREES = 360.0 / Reels.STOPS;
    /** リールが止まり終わる tick（左から）と、リーチのときに右のリールを延ばす分。 */
    private static final int[] STOP_AT = {32, 44, 56};
    private static final int REACH_EXTRA = 26;
    /** 止まる直前に行き過ぎて戻る角度。 */
    private static final double OVERSHOOT = 6;
    /** これより速く回っている間はぶれた絵柄のモデルにする（度 / tick）。 */
    private static final double BLUR_SPEED = 25;

    private final SlotSettings settings;
    private final Random random = new SecureRandom();
    private State state = State.IDLE;
    private long bet;
    private final ItemDisplay[] reels = new ItemDisplay[Reels.REELS];
    private final double[] reelAngle = new double[Reels.REELS];
    private final boolean[] blurred = new boolean[Reels.REELS];
    private ItemDisplay lever;
    private ItemDisplay lamps;
    /** 当たりの光る帯（Line の順に 1 本ずつ）。見せないときは大きさ 0。 */
    private final ItemDisplay[] winlines = new ItemDisplay[Line.COUNT];
    private Interaction leverHit;
    private TextDisplay resultLabel;
    private final List<ItemDisplay> shower = new ArrayList<>();
    private boolean lampPhase;
    /** ランプを速く流している間（当たり・リーチ）。 */
    private boolean celebrating;

    SlotMachine(LuckyCasinoPaper plugin, String id, Frame frame, UUID owner, Bank bank, SlotSettings settings) {
        super(plugin, id, GameType.SLOT, frame, owner, bank);
        this.settings = settings;
        this.bet = settings.minBet();
        for (int i = 0; i < reelAngle.length; i++) {
            reelAngle[i] = random.nextInt(Reels.STOPS) * FACE_DEGREES;
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
        return settings.minBet();
    }

    SlotSettings settings() {
        return settings;
    }

    State state() {
        return state;
    }

    long bet() {
        return bet;
    }

    // ---- 表示 ----

    @Override
    protected void onSpawn() {
        for (int i = 0; i < reels.length; i++) {
            reels[i] = display(LAYOUT.reel(i), reelModel(i, false), reelTransform(reelAngle[i]));
            reels[i].setInterpolationDuration(STEP);
            blurred[i] = false;
            fixtures().add(reels[i]);
        }
        lever = display(LAYOUT.lever(), "slot/lever", leverTransform(0));
        fixtures().add(lever);
        lamps = display(LAYOUT.modelAt(), "slot/lamps_a", scaled(1f));
        lamps.setBrightness(new Display.Brightness(15, 15));
        fixtures().add(lamps);
        for (Line line : Line.values()) {
            ItemDisplay band = display(LAYOUT.modelAt(), "slot/winline", lineTransform(line, false));
            band.setBrightness(new Display.Brightness(15, 15));
            winlines[line.ordinal()] = band;
            fixtures().add(band);
        }
        Location hitAt = frame.at(LAYOUT.leverHit());
        leverHit = hitAt.getWorld().spawn(hitAt, Interaction.class, entity -> {
            entity.setPersistent(false);
            entity.setInteractionWidth(0.35f);
            entity.setInteractionHeight(0.65f);
            entity.setResponsive(true);
        });
        fixtures().add(leverHit);
        resultLabel = label(LAYOUT.resultLabel(), 0.8f);
        fixtures().add(resultLabel);
        lampTick();
    }

    private ItemDisplay display(double[] local, String model, Transformation transform) {
        Location at = frame.at(local, 180f);
        return at.getWorld().spawn(at, ItemDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setItemStack(Items.model(model));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setTransformation(transform);
        });
    }

    private static String reelModel(int index, boolean blur) {
        return "slot/reel" + (index + 1) + (blur ? "_blur" : "");
    }

    /**
     * リールを、正面の絵柄が下へ流れる向きに degrees 度回したところ。コマ k は degrees = k × 22.5 で正面に来る。
     * 部品は台の向き + 180 度で出しているので、台の局所座標で +X 軸まわりに回すには、Display の X 軸まわりに逆向きに回す。
     */
    private static Transformation reelTransform(double degrees) {
        return new Transformation(new Vector3f(), new AxisAngle4f((float) Math.toRadians(-degrees), 1f, 0f, 0f),
                new Vector3f(1f, 1f, 1f), new AxisAngle4f());
    }

    /** レバーを手前に degrees 度倒したところ。 */
    private static Transformation leverTransform(double degrees) {
        return new Transformation(new Vector3f(), new AxisAngle4f((float) Math.toRadians(-degrees), 1f, 0f, 0f),
                new Vector3f(1f, 1f, 1f), new AxisAngle4f());
    }

    private static Transformation scaled(float s) {
        return new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(s, s, s), new AxisAngle4f());
    }

    /**
     * 当たりの帯（モデルは中央の段の横線）を、そのラインの位置に動かしたところ。上下の段は平行にずらし、
     * 斜めは窓の中央を中心に回す。Display の座標はモデルの座標と x・z の向きが逆なので、z 軸まわりの回転も逆になる。
     */
    static Transformation lineTransform(Line line, boolean shown) {
        float s = shown ? 1f : 0f;
        Vector3f scale = new Vector3f(s, s, s);
        int slope = line.row(0) - line.row(2);
        if (slope == 0) {
            return new Transformation(new Vector3f(0f, (float) (line.row(0) * SlotLayout.ROW), 0f), new Quaternionf(), scale, new Quaternionf());
        }
        // 右下がり（左が上の段）はモデルで z 軸まわりに負の向き、Display では正の向き
        float angle = (float) Math.toRadians(SlotLayout.DIAGONAL_DEGREES * Math.signum(slope));
        Quaternionf rotation = new Quaternionf().rotateZ(angle);
        Vector3f pivot = new Vector3f(0f, (float) (SlotLayout.AXIS_Y - 0.5), (float) -SlotLayout.LINE_Z);
        Vector3f translation = new Vector3f(pivot).sub(rotation.transform(new Vector3f(pivot)));
        return new Transformation(translation, rotation, scale, new Quaternionf());
    }

    private void showLines(List<Line> lines, boolean shown) {
        for (Line line : lines) {
            ItemDisplay band = winlines[line.ordinal()];
            if (band != null) {
                band.setTransformation(lineTransform(line, shown));
            }
        }
    }

    private void hideLines() {
        showLines(List.of(Line.values()), false);
    }

    @Override
    protected void onDespawn() {
        clearShower();
        state = State.IDLE;
        celebrating = false;
        for (int i = 0; i < reels.length; i++) {
            reels[i] = null;
        }
        lever = null;
        lamps = null;
        Arrays.fill(winlines, null);
        leverHit = null;
        resultLabel = null;
    }

    /** 看板の縁の電球を交互に光らせる。当たりとリーチの間は速く、回っている間は少し速く。 */
    private void lampTick() {
        if (lamps == null) {
            return;
        }
        lampPhase = !lampPhase;
        lamps.setItemStack(Items.model(lampPhase ? "slot/lamps_b" : "slot/lamps_a"));
        later(celebrating ? 2 : state == State.SPINNING ? 5 : 16, this::lampTick);
    }

    // ---- 席 ----

    @Override
    protected void onSeated(int seat, Player player) {
        bet = Math.min(Math.max(bet, settings.minBet()), Math.max(settings.minBet(), seats[seat].chips));
        if (!settings.bets().contains(bet)) {
            bet = settings.minBet();
        }
        if (player == null) {
            later(10, this::botSpin);
        } else {
            plugin.menus().openSlot(player, this);
        }
        updateStatus();
    }

    @Override
    void openMenu(Player player) {
        plugin.menus().openSlot(player, this);
    }

    @Override
    boolean clickFixture(Player player, Entity entity) {
        if (leverHit == null || !leverHit.equals(entity)) {
            return false;
        }
        if (seatOf(player.getUniqueId()) < 0) {
            player.sendMessage(Component.text(seats[0] == null ? "先に椅子をクリックして座ってください" : "この台は " + seats[0].name + " が使っています",
                    NamedTextColor.GRAY));
            return true;
        }
        spin(player);
        return true;
    }

    /** 座った位置からはレバーが筐体（バリア）の奥に見えるので、視線がレバーの当たり判定を通っていれば回す。 */
    @Override
    boolean clickAimed(Player player) {
        if (leverHit == null || seatOf(player.getUniqueId()) < 0) {
            return false;
        }
        Location eye = player.getEyeLocation();
        if (leverHit.getBoundingBox().expand(0.05).rayTrace(eye.toVector(), eye.getDirection(), 4.5) == null) {
            return false;
        }
        spin(player);
        return true;
    }

    @Override
    protected boolean prepareLeave(int index) {
        return seats[index].atRisk == 0;
    }

    @Override
    protected void tickSecond() {
    }

    /** 1 回の賭け金を変える。 */
    void setBet(Player player, long amount) {
        if (!settings.bets().contains(amount)) {
            return;
        }
        bet = amount;
        player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_PLACE, 0.6f, 1.6f);
        updateStatus();
    }

    // ---- 回す ----

    void spin(Player player) {
        int index = seatOf(player.getUniqueId());
        if (index < 0) {
            return;
        }
        String error = startSpin(index);
        if (error != null) {
            player.sendMessage(Component.text(error, NamedTextColor.RED));
        }
    }

    /** 回し始める。回せなければ理由を返す（null なら成功）。 */
    private String startSpin(int index) {
        Seat seat = seats[index];
        if (seat == null || seat.away || !spawned()) {
            return "今は回せません";
        }
        if (state != State.IDLE) {
            return "リールが止まるまで待ってください";
        }
        if (seat.chips < bet) {
            return "チップが足りません（1 回 " + bet + " 枚）";
        }
        seat.chips -= bet;
        seat.atRisk += bet;
        writeLedger(seat);
        state = State.SPINNING;
        Spin spin = Spin.random(random, bet, settings.paytable());
        resultLabel.text(Component.empty());
        hideLines();
        updateStatus();
        pullLever();
        int end = animateReels(spin);
        later(end + 4, () -> reveal(index, spin));
        return null;
    }

    private void pullLever() {
        playSound(Sound.BLOCK_LEVER_CLICK, 0.8f);
        lever.setInterpolationDelay(0);
        lever.setInterpolationDuration(3);
        lever.setTransformation(leverTransform(55));
        later(4, () -> {
            if (lever != null) {
                lever.setInterpolationDelay(0);
                lever.setInterpolationDuration(7);
                lever.setTransformation(leverTransform(0));
            }
        });
    }

    /** リールを回して止める。最後のリールが止まり終わる tick を返す。 */
    private int animateReels(Spin spin) {
        List<Line> reachLines = spin.reachLines();
        boolean reach = !reachLines.isEmpty();
        int[] ends = STOP_AT.clone();
        if (reach) {
            ends[2] += REACH_EXTRA;
        }
        double[][] paths = new double[Reels.REELS][];
        for (int i = 0; i < paths.length; i++) {
            paths[i] = reelPath(reelAngle[i], spin.stops()[i], ends[i]);
            reelAngle[i] = Math.floorMod(spin.stops()[i], Reels.STOPS) * FACE_DEGREES;
        }
        int last = ends[2];
        for (int t = STEP; t <= last; t += STEP) {
            int tick = t;
            later(tick, () -> {
                for (int i = 0; i < paths.length; i++) {
                    if (tick < paths[i].length) {
                        moveReel(i, paths[i], tick);
                    }
                }
                if (tick % 4 == 0 && tick < last - 2) {
                    playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 1.8f, 0.35f);
                }
            });
        }
        for (int i = 0; i < ends.length; i++) {
            int reel = i;
            later(ends[i] - 3, () -> playSound(Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1.5f + reel * 0.1f));
        }
        if (reach) {
            later(ends[1], () -> {
                celebrating = true;
                playSound(Sound.BLOCK_NOTE_BLOCK_PLING, 1.2f);
                for (Seat seat : seats) {
                    Player player = online(seat);
                    if (player != null) {
                        player.sendActionBar(Component.text("リーチ！", NamedTextColor.GOLD));
                    }
                }
            });
            for (int k = 1; k <= 4; k++) {
                float pitch = 1.2f + 0.15f * k;
                later(ends[1] + k * 6L, () -> playSound(Sound.BLOCK_NOTE_BLOCK_PLING, pitch));
            }
            // リーチのラインの帯を、右のリールが止まるまでゆっくり点滅させる
            for (int t = ends[1]; t < ends[2] - 2; t += 6) {
                boolean on = ((t - ends[1]) / 6) % 2 == 0;
                later(t, () -> showLines(reachLines, on));
            }
            later(ends[2] - 2, this::hideLines);
        }
        return last;
    }

    private void moveReel(int index, double[] path, int tick) {
        ItemDisplay reel = reels[index];
        if (reel == null) {
            return;
        }
        double speed = Math.abs(path[tick] - path[tick - STEP]) / STEP;
        boolean blur = speed > BLUR_SPEED;
        if (blur != blurred[index]) {
            blurred[index] = blur;
            reel.setItemStack(Items.model(reelModel(index, blur)));
        }
        reel.setInterpolationDelay(0);
        reel.setInterpolationDuration(STEP);
        reel.setTransformation(reelTransform(path[tick]));
    }

    /**
     * 1 本のリールの角度（tick ごと、0 〜 end）。少し戻してから加速し、一定の速さで回り、
     * end − 4 tick に止まる位置を少し行き過ぎてから戻る。全体の速さを少し変えて、止まる位置がコマ stop にそろうようにする。
     */
    static double[] reelPath(double start, int stop, int end) {
        final int windup = 2;
        final int accel = 4;
        final int decel = 14;
        int main = end - 4 - windup;
        int cruise = Math.max(0, main - accel - decel);
        double nominal = SPEED * accel / 2 + SPEED * cruise + SPEED * decel / 3;
        double base = start - 8;
        double target = Math.floorMod(stop, Reels.STOPS) * FACE_DEGREES;
        double offset = target - (base + nominal);
        offset = ((offset % 360) + 540) % 360 - 180;
        double travel = nominal + offset;
        double scale = (travel + OVERSHOOT) / nominal;
        double[] path = new double[end + 1];
        for (int t = 0; t <= end; t++) {
            if (t <= windup) {
                path[t] = start - 8.0 * t / windup;
            } else if (t <= windup + main) {
                double tau = t - windup;
                double f;
                if (tau < accel) {
                    f = SPEED * tau * tau / (2.0 * accel);
                } else if (tau < accel + cruise) {
                    f = SPEED * accel / 2 + SPEED * (tau - accel);
                } else {
                    double u = Math.min(1, (tau - accel - cruise) / decel);
                    f = SPEED * accel / 2 + SPEED * cruise + SPEED * decel / 3 * (1 - Math.pow(1 - u, 3));
                }
                path[t] = base + scale * f;
            } else {
                double u = (t - windup - main) / 4.0;
                double ease = u * u * (3 - 2 * u);
                path[t] = base + travel + OVERSHOOT * (1 - ease);
            }
        }
        return path;
    }

    // ---- 結果 ----

    private void reveal(int index, Spin spin) {
        state = State.SHOWING;
        Seat seat = seats[index];
        if (seat != null) {
            seat.atRisk -= spin.bet();
            seat.chips += spin.payout();
            writeLedger(seat);
            if (observer != null) {
                observer.payout(index, spin.bet(), spin.payout());
            }
        }
        Spin.LineWin best = spin.best();
        List<Line> lines = spin.wins().stream().map(Spin.LineWin::line).toList();
        int wait;
        if (best == null) {
            celebrating = false;
            resultLabel.text(Component.text("はずれ", NamedTextColor.GRAY));
            wait = 12;
        } else if (!spin.big()) {
            celebrating = true;
            resultLabel.text(Component.text("+" + spin.payout() + " 枚", NamedTextColor.GOLD));
            playSound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f);
            later(4, () -> playSound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f));
            blinkLines(lines, 32);
            wait = 36;
        } else {
            celebrating = true;
            resultLabel.text(Component.text("大当たり！ +" + spin.payout() + " 枚", NamedTextColor.GOLD));
            playSound(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f);
            blinkLines(lines, 70);
            chipShower(best.win().times() >= 100 ? 18 : 10);
            Location top = frame.at(0, 2.1, 0);
            top.getWorld().spawnParticle(Particle.FIREWORK, top, 40, 0.3, 0.2, 0.3, 0.08);
            Player player = online(seat);
            if (player != null) {
                player.showTitle(Title.title(Component.text("大当たり！", NamedTextColor.GOLD),
                        Component.text(best.win().name() + (lines.size() > 1 ? "（" + lines.size() + " ライン）" : "") + "  +" + spin.payout() + " 枚",
                                NamedTextColor.YELLOW),
                        Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(1800), Duration.ofMillis(400))));
            }
            wait = 76;
        }
        Player player = online(seat);
        if (player != null) {
            // チャットには結果と当たった役だけを出す（回すのはレバー、賭け金はメニューで）
            if (best == null) {
                player.sendMessage(Component.text("はずれ", NamedTextColor.GRAY));
            } else {
                long net = spin.payout() - spin.bet();
                player.sendMessage(Component.text((spin.big() ? "大当たり" : "当たり") + "  " + spin.payout() + " 枚もどり", NamedTextColor.GOLD)
                        .append(Component.text("（差し引き " + (net >= 0 ? "+" : "−") + Math.abs(net) + " 枚）", net >= 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY)));
                long lineBet = spin.bet() / Line.COUNT;
                for (Spin.LineWin w : spin.wins()) {
                    player.sendMessage(Component.text("  " + w.line().displayName() + "：" + w.win().name(), NamedTextColor.YELLOW)
                            .append(Component.text("  +" + lineBet * w.win().times() + " 枚", NamedTextColor.GRAY)));
                }
            }
        }
        later(wait, this::finishRound);
    }

    /** 当たったラインの帯をそろって点滅させる。 */
    private void blinkLines(List<Line> lines, int ticks) {
        for (int t = 0; t < ticks; t += 4) {
            boolean on = (t / 4) % 2 == 0;
            later(Math.max(1, t), () -> showLines(lines, on));
        }
    }

    /** 受け皿からチップを噴き出させる（放物線で手前へ飛んで消える）。 */
    private void chipShower(int count) {
        String[] models = {"chip/c10_1", "chip/c50_1", "chip/c100_1", "chip/c500_1", "chip/c1000_1"};
        for (int n = 0; n < count; n++) {
            double[] p = LAYOUT.tray().clone();
            double[] v = {(random.nextDouble() - 0.5) * 0.12, 0.16 + random.nextDouble() * 0.1, 0.05 + random.nextDouble() * 0.07};
            float spinAxis = random.nextFloat();
            String model = models[random.nextInt(models.length)];
            int delay = n * 2;
            later(Math.max(1, delay), () -> {
                if (!spawned()) {
                    return;
                }
                Location at = frame.at(p);
                ItemDisplay chip = at.getWorld().spawn(at, ItemDisplay.class, entity -> {
                    entity.setPersistent(false);
                    entity.setItemStack(Items.model(model));
                    entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                    entity.setTeleportDuration(STEP);
                    entity.setInterpolationDuration(STEP);
                    entity.setTransformation(scaled(0.5f));
                });
                shower.add(chip);
                for (int t = STEP; t <= 26; t += STEP) {
                    int tick = t;
                    later(tick, () -> {
                        if (!chip.isValid()) {
                            return;
                        }
                        double[] q = {p[0] + v[0] * tick, p[1] + v[1] * tick - 0.012 * tick * tick, p[2] + v[2] * tick};
                        chip.teleport(frame.at(q));
                        chip.setInterpolationDelay(0);
                        chip.setTransformation(new Transformation(new Vector3f(),
                                new AxisAngle4f(tick * 0.6f, spinAxis, 1f - spinAxis, 0.3f), new Vector3f(0.5f, 0.5f, 0.5f), new AxisAngle4f()));
                    });
                }
                later(28, () -> {
                    chip.remove();
                    shower.remove(chip);
                });
            });
        }
        playSound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.4f);
    }

    private void clearShower() {
        shower.forEach(Entity::remove);
        shower.clear();
    }

    private void finishRound() {
        clearShower();
        celebrating = false;
        hideLines();
        state = State.IDLE;
        settleAwaySeats();
        updateStatus();
        if (observer != null) {
            observer.roundFinished();
        }
        if (seats[0] != null && seats[0].bot) {
            later(6, this::botSpin);
        }
    }

    private void botSpin() {
        Seat seat = seats[0];
        if (seat == null || !seat.bot || state != State.IDLE) {
            return;
        }
        List<Long> affordable = settings.bets().stream().filter(b -> b <= seat.chips).toList();
        if (affordable.isEmpty()) {
            cashOut(0, null);
            return;
        }
        bet = affordable.get(random.nextInt(affordable.size()));
        startSpin(0);
    }

    @Override
    protected Component statusText() {
        Seat seat = seats[0];
        Component head = seat == null
                ? Component.text("スロット", NamedTextColor.GOLD).append(Component.newline())
                .append(Component.text("椅子をクリックして座る", NamedTextColor.GRAY))
                : Component.text(seat.name + "（1 回 " + bet + " 枚）", NamedTextColor.AQUA);
        return head.append(limitsLine("1 回 " + String.join(" / ", settings.bets().stream().map(String::valueOf).toList()) + " 枚"));
    }
}
