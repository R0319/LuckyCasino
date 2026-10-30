package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.bigwheel.BigWheel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ビッグホイール。ステージの幕の前に立てた 54 マスのホイールを回し、上の針が指したマスの絵柄に賭けていたら当たり。
 * 客は扇形に並べたスツールに座り、チップは各席の前のベット台に置く。
 * 止まるマスを先に決め、そこで止まるよう回す角度を逆算する。針の上には、いま針が指しているマスの絵柄を出す。
 * ホイールを囲む電球は 2 枚のモデルを入れ替えて点滅させる（待っている間はゆっくり、回っている間は速く）。
 */
final class BigWheelTable extends SpotTable {

    private static final BigWheelLayout LAYOUT = new BigWheelLayout();
    private static final int SPIN_TICKS = 150;
    private static final int STEP = 2;
    private static final double SEGMENT = 360.0 / BigWheel.SEGMENTS;

    private static final List<Spot> SPOTS = List.of(
            new Spot("one", "1", "24 マス。配当 1:1（2 倍が戻る）", Material.YELLOW_CONCRETE, 19),
            new Spot("two", "2", "15 マス。配当 2:1（3 倍が戻る）", Material.BLUE_CONCRETE, 20),
            new Spot("five", "5", "7 マス。配当 5:1（6 倍が戻る）", Material.PURPLE_CONCRETE, 21),
            new Spot("ten", "10", "4 マス。配当 10:1（11 倍が戻る）", Material.GREEN_CONCRETE, 22),
            new Spot("twenty", "20", "2 マス。配当 20:1（21 倍が戻る）", Material.ORANGE_CONCRETE, 23),
            new Spot("joker", "ジョーカー", "1 マスだけ。配当 40:1（41 倍が戻る）", Material.BLACK_CONCRETE, 24),
            new Spot("logo", "ロゴ（LC）", "1 マスだけ。配当 40:1（41 倍が戻る）", Material.RED_CONCRETE, 25));

    /** 回っている間の電球の切り替え間隔（tick）。 */
    private static final int BLINK_FAST = 4;

    private ItemDisplay wheel;
    private ItemDisplay bulbs;
    private boolean bulbPhase;
    private TextDisplay pointerLabel;
    /** ホイールの今の角度（正面から見て反時計回り、度）。 */
    private double wheelAngle;
    private int result = -1;
    private BigWheel.Segment shown;

    BigWheelTable(LuckyCasinoPaper plugin, String id, Frame frame, UUID owner, Bank bank, SpotSettings settings) {
        super(plugin, id, GameType.BIGWHEEL, frame, owner, bank, settings);
    }

    @Override
    TableShape shape() {
        return LAYOUT;
    }

    @Override
    List<Spot> spots() {
        return SPOTS;
    }

    private static BigWheel.Segment segment(String spot) {
        return BigWheel.Segment.valueOf(spot.toUpperCase(Locale.ROOT));
    }

    @Override
    protected double[] spotCenter(Spot spot) {
        return LAYOUT.chip(0, SPOTS.indexOf(spot));
    }

    @Override
    protected double[] chipSpot(Spot spot, int seat) {
        return LAYOUT.chip(seat, SPOTS.indexOf(spot));
    }

    @Override
    protected double[] croupierChips() {
        return LAYOUT.croupierChips();
    }

    @Override
    protected List<String> botSpots(ThreadLocalRandom r) {
        // 当たりやすい 1・2 を多めに
        return List.of(r.nextInt(3) > 0 ? (r.nextBoolean() ? "one" : "two") : SPOTS.get(r.nextInt(SPOTS.size())).id());
    }

    @Override
    protected void onSpawn() {
        Location bulbsAt = frame.at(LAYOUT.bulbs(), LAYOUT.modelYaw());
        float b = BigWheelLayout.STAGE_SCALE;
        bulbs = bulbsAt.getWorld().spawn(bulbsAt, ItemDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setItemStack(Items.model("bigwheel/bulbs_0"));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(b, b, b), new AxisAngle4f()));
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setViewRange(2f);
        });
        fixtures().add(bulbs);
        Location wheelAt = frame.at(LAYOUT.wheel());
        wheel = wheelAt.getWorld().spawn(wheelAt, ItemDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setItemStack(Items.model("bigwheel/wheel"));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setTransformation(wheelTransform(wheelAngle));
        });
        fixtures().add(wheel);
        pointerLabel = label(LAYOUT.pointerLabel(), 0.8f);
        fixtures().add(pointerLabel);
        showPointer(BigWheel.at(underPointer(wheelAngle)));
    }

    private void blink() {
        if (bulbs != null) {
            bulbPhase = !bulbPhase;
            bulbs.setItemStack(Items.model(bulbPhase ? "bigwheel/bulbs_1" : "bigwheel/bulbs_0"));
        }
    }

    @Override
    protected void tickSecond() {
        super.tickSecond();
        if (state() != State.PLAYING) {
            blink();
        }
    }

    private static Transformation wheelTransform(double degrees) {
        float s = BigWheelLayout.WHEEL_SCALE;
        return new Transformation(new Vector3f(), new AxisAngle4f((float) Math.toRadians(degrees), 0f, 0f, 1f),
                new Vector3f(s, s, s), new AxisAngle4f());
    }

    /** 角度 degrees のとき針が指すマス。マス i は正面から見て上から時計回りに i×(360/54) 度。 */
    private static int underPointer(double degrees) {
        return Math.floorMod((int) Math.round(degrees / SEGMENT), BigWheel.SEGMENTS);
    }

    private void showPointer(BigWheel.Segment segment) {
        shown = segment;
        if (pointerLabel != null) {
            pointerLabel.text(segmentText(segment));
        }
    }

    static Component segmentText(BigWheel.Segment segment) {
        return switch (segment) {
            case ONE -> Component.text("1", NamedTextColor.YELLOW);
            case TWO -> Component.text("2", NamedTextColor.BLUE);
            case FIVE -> Component.text("5", NamedTextColor.LIGHT_PURPLE);
            case TEN -> Component.text("10", NamedTextColor.GREEN);
            case TWENTY -> Component.text("20", NamedTextColor.GOLD);
            case JOKER -> Component.text("ジョーカー", NamedTextColor.DARK_GRAY);
            case LOGO -> Component.text("ロゴ", NamedTextColor.RED);
        };
    }

    // ---- 勝負 ----

    @Override
    protected int play() {
        result = BigWheel.spin(random);
        double start = wheelAngle;
        double jitter = (random.nextDouble() - 0.5) * SEGMENT * 0.7;
        double target = result * SEGMENT + jitter;
        // 2 周半ほど回してから、ちょうど target の角度で止める
        double base = start + 2 * 360 + random.nextInt(360);
        double end = base + (((target - base) % 360) + 360) % 360;
        double total = end - start;
        swing();
        playSound(Sound.BLOCK_GRINDSTONE_USE, 1.2f);
        for (int t = BLINK_FAST; t <= SPIN_TICKS + 40; t += BLINK_FAST) {
            later(t, this::blink);
        }
        for (int t = STEP; t <= SPIN_TICKS; t += STEP) {
            double progress = (double) t / SPIN_TICKS;
            double angle = start + total * (1 - Math.pow(1 - progress, 3));
            BigWheel.Segment under = BigWheel.at(underPointer(angle));
            boolean late = progress > 0.4;
            later(t, () -> {
                if (wheel != null) {
                    wheel.setInterpolationDelay(0);
                    wheel.setInterpolationDuration(STEP);
                    wheel.setTransformation(wheelTransform(angle));
                }
                if (under != shown) {
                    showPointer(under);
                }
                if (late) {
                    playSound(Sound.BLOCK_BAMBOO_HIT, 1.9f, 0.5f);
                }
            });
        }
        wheelAngle = end % 360;
        later(SPIN_TICKS + 2, () -> {
            showPointer(BigWheel.at(result));
            Location at = frame.at(LAYOUT.pointerLabel());
            at.getWorld().spawnParticle(Particle.END_ROD, at, 15, 0.2, 0.2, 0.1, 0.02);
            playSound(Sound.BLOCK_NOTE_BLOCK_BELL, 1.2f);
        });
        return SPIN_TICKS + 2;
    }

    @Override
    protected Component resultText() {
        BigWheel.Segment s = BigWheel.at(result);
        return segmentText(s).append(Component.text("（" + s.pays() + ":1）", NamedTextColor.WHITE));
    }

    @Override
    protected long payout(String spot, long amount) {
        return BigWheel.payout(segment(spot), amount, result);
    }

    @Override
    protected void settled(Seat seat, String spot, long amount, long payout) {
        if (payout > amount && (spot.equals("joker") || spot.equals("logo"))) {
            achieve(seat, Achievements.Achievement.BIGWHEEL_STAR);
        }
    }

    @Override
    protected void clearRound() {
        result = -1;
    }

    @Override
    protected void onDespawn() {
        super.onDespawn();
        wheel = null;
        bulbs = null;
        pointerLabel = null;
    }
}
