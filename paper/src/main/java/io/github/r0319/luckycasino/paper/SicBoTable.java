package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.sicbo.SicBo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 大小（シックボー）を座敷で。畳の真ん中のどんぶりへ、奥に座った親がサイコロ 3 個を投げ込む。
 * 出目は先に決め、どんぶりの中ででたらめに跳ねさせてから、決めた目を上にして止める。
 */
final class SicBoTable extends SpotTable {

    private static final SicBoLayout LAYOUT = new SicBoLayout();
    /** 親の手からどんぶりに入るまで（tick）。 */
    private static final int TOSS_TICKS = 10;
    private static final int TOSS_STEP = 2;
    /** どんぶりの中で跳ねている間（tick）。 */
    private static final int BOUNCE_TICKS = 30;
    private static final int BOUNCE_STEP = 3;
    private static final int SETTLE = 6;
    private static final int SHOW_AT = TOSS_TICKS + BOUNCE_TICKS + SETTLE + 8;
    /** 投げる前のサイコロ（親の手の中。どんぶりの底の基準からのずれ）。 */
    private static final Vector3f HAND = new Vector3f(0f, 0.5f, -1.0f);
    /** 止まったサイコロのどんぶりの中での位置（基準からのずれ）。 */
    private static final float[][] REST = {{-0.09f, 0.05f}, {0.09f, 0.06f}, {0f, -0.09f}};

    private static final List<Spot> SPOTS = buildSpots();

    private static List<Spot> buildSpots() {
        List<Spot> out = new ArrayList<>();
        for (int n = 1; n <= 6; n++) {
            out.add(new Spot("triple_" + n, "ゾロ目 " + n + "・" + n + "・" + n, "3 個とも " + n + "。配当 180:1（181 倍が戻る）", Material.GOLD_BLOCK, n - 1));
        }
        out.add(new Spot("any_triple", "ゾロ目どれでも", "3 個とも同じ目なら何でも。配当 30:1（31 倍が戻る）", Material.NETHER_STAR, 7));
        for (int n = 1; n <= 6; n++) {
            out.add(new Spot("double_" + n, "ダブル " + n + "・" + n, n + " が 2 個以上。配当 10:1（11 倍が戻る）", Material.BONE_BLOCK, 8 + n));
        }
        out.add(new Spot("small", "小（4〜10）", "合計 4〜10。配当 1:1（2 倍が戻る）。ゾロ目は負け", Material.LIGHT_BLUE_CONCRETE, 16));
        out.add(new Spot("big", "大（11〜17）", "合計 11〜17。配当 1:1（2 倍が戻る）。ゾロ目は負け", Material.RED_CONCRETE, 17));
        for (int t = 4; t <= 17; t++) {
            int pays = SicBo.totalPays(t);
            out.add(new Spot("total_" + t, "合計 " + t, "3 個の合計が " + t + "。配当 " + pays + ":1（" + (pays + 1) + " 倍が戻る）",
                    Material.PAPER, t <= 10 ? 18 + (t - 4) : 27 + (t - 11)));
        }
        out.add(new Spot("odd", "奇数", "合計が奇数。配当 1:1（2 倍が戻る）。ゾロ目は負け", Material.QUARTZ_BLOCK, 26));
        out.add(new Spot("even", "偶数", "合計が偶数。配当 1:1（2 倍が戻る）。ゾロ目は負け", Material.COAL_BLOCK, 35));
        for (int n = 1; n <= 6; n++) {
            out.add(new Spot("single_" + n, "目 " + n, n + " が 1 個で 2 倍、2 個で 3 倍、3 個で 4 倍が戻る", Material.BONE, 35 + n));
        }
        return List.copyOf(out);
    }

    private final ItemDisplay[] dice = new ItemDisplay[3];
    private TextDisplay resultLabel;
    private SicBo roll;

    SicBoTable(LuckyCasinoPaper plugin, String id, Frame frame, UUID owner, Bank bank, SpotSettings settings) {
        super(plugin, id, GameType.SICBO, frame, owner, bank, settings);
    }

    @Override
    TableShape shape() {
        return LAYOUT;
    }

    @Override
    List<Spot> spots() {
        return SPOTS;
    }

    /** 賭けの種類（チップを置く列）と、その中での番号。 */
    private static int[] group(String id) {
        int dash = id.lastIndexOf('_');
        int n = dash < 0 ? 0 : Integer.parseInt(id.substring(dash + 1));
        return switch (id.substring(0, dash < 0 ? id.length() : dash)) {
            case "small" -> new int[]{0, 0};
            case "big" -> new int[]{1, 0};
            case "odd" -> new int[]{2, 0};
            case "even" -> new int[]{3, 0};
            case "single" -> new int[]{4, n - 1};
            case "total" -> new int[]{5, n - 4};
            case "double" -> new int[]{6, n - 1};
            case "triple" -> new int[]{7, n};
            case "any" -> new int[]{7, 0};
            default -> throw new IllegalArgumentException(id);
        };
    }

    @Override
    protected double[] spotCenter(Spot spot) {
        return chipSpot(spot, 0);
    }

    @Override
    protected double[] chipSpot(Spot spot, int seat) {
        int[] g = group(spot.id());
        return LAYOUT.chip(seat, g[0], g[1]);
    }

    @Override
    protected double[] croupierChips() {
        return LAYOUT.croupierChips();
    }

    @Override
    protected List<String> botSpots(ThreadLocalRandom r) {
        List<String> out = new ArrayList<>();
        out.add(r.nextBoolean() ? "small" : "big");
        if (r.nextInt(3) == 0) {
            out.add(SPOTS.get(r.nextInt(SPOTS.size())).id());
        }
        return out;
    }

    @Override
    protected void onSpawn() {
        Location bowlAt = frame.at(LAYOUT.bowl());
        fixtures().add(bowlAt.getWorld().spawn(bowlAt, ItemDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setItemStack(Items.model("sicbo/bowl"));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        }));
        Location diceAt = frame.at(LAYOUT.dice());
        int[] faces = {4, 5, 6};
        for (int i = 0; i < 3; i++) {
            Transformation rest = dieAt(i, faces[i], 0.4f * i);
            dice[i] = diceAt.getWorld().spawn(diceAt, ItemDisplay.class, entity -> {
                entity.setPersistent(false);
                entity.setItemStack(Items.model("sicbo/die"));
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                entity.setTransformation(rest);
            });
            fixtures().add(dice[i]);
        }
    }

    private static Transformation scaled(Vector3f translation, Quaternionf rotation, float s) {
        return new Transformation(translation, rotation, new Vector3f(s, s, s), new Quaternionf());
    }

    /**
     * 目 face を上にする回転。モデルは上 1・下 6・南 2・北 5・西 3・東 4 で、ItemDisplay は Y 軸まわりに 180 度回して描くので、
     * 描かれる向きは 2 が北、5 が南、3 が東、4 が西。
     */
    static Quaternionf faceUp(int face) {
        float q = (float) (Math.PI / 2);
        return switch (face) {
            case 1 -> new Quaternionf();
            case 2 -> new Quaternionf().rotateX(q);
            case 3 -> new Quaternionf().rotateZ(q);
            case 4 -> new Quaternionf().rotateZ(-q);
            case 5 -> new Quaternionf().rotateX(-q);
            case 6 -> new Quaternionf().rotateX(2 * q);
            default -> throw new IllegalArgumentException("face " + face);
        };
    }

    /** i 個目のサイコロを、目 face を上にして止めた形。yaw は真上から見た向き。 */
    private static Transformation dieAt(int i, int face, float yaw) {
        return scaled(new Vector3f(REST[i][0], 0, REST[i][1]), new Quaternionf().rotateY(yaw).mul(faceUp(face)), SicBoLayout.DIE_SCALE);
    }

    private static void move(ItemDisplay entity, Transformation to, int ticks) {
        if (entity == null) {
            return;
        }
        entity.setInterpolationDelay(0);
        entity.setInterpolationDuration(ticks);
        entity.setTransformation(to);
    }

    // ---- 勝負 ----

    private Quaternionf randomSpin() {
        return new Quaternionf().rotateXYZ(random.nextFloat() * 6.3f, random.nextFloat() * 6.3f, random.nextFloat() * 6.3f);
    }

    @Override
    protected int play() {
        roll = SicBo.roll(random);
        resultLabel = label(LAYOUT.resultLabel(), 0.8f);
        swing();
        broadcast(Component.text("親がサイコロを投げます", NamedTextColor.GRAY));
        // 親の手の中へ（すぐ）、そこから弧を描いてどんぶりへ
        Vector3f[] land = new Vector3f[3];
        for (int i = 0; i < 3; i++) {
            Vector3f hand = new Vector3f(HAND).add((i - 1) * 0.06f, 0, 0);
            land[i] = new Vector3f((random.nextFloat() - 0.5f) * 0.25f, 0.08f, (random.nextFloat() - 0.5f) * 0.25f);
            move(dice[i], scaled(hand, randomSpin(), SicBoLayout.DIE_SCALE), 0);
            for (int t = TOSS_STEP; t <= TOSS_TICKS; t += TOSS_STEP) {
                float u = (float) t / TOSS_TICKS;
                Vector3f at = new Vector3f(hand).lerp(land[i], u).add(0, 0.35f * 4 * u * (1 - u), 0);
                Transformation pose = scaled(at, randomSpin(), SicBoLayout.DIE_SCALE);
                int die = i;
                later(t, () -> move(dice[die], pose, TOSS_STEP));
            }
        }
        // どんぶりの中で跳ねる（だんだん低く）
        for (int t = TOSS_TICKS + BOUNCE_STEP; t <= TOSS_TICKS + BOUNCE_TICKS; t += BOUNCE_STEP) {
            float left = 1f - (float) (t - TOSS_TICKS) / BOUNCE_TICKS;
            Transformation[] hop = new Transformation[3];
            for (int i = 0; i < 3; i++) {
                double a = random.nextDouble() * 2 * Math.PI;
                float r = 0.08f + random.nextFloat() * 0.12f;
                Vector3f at = new Vector3f((float) Math.sin(a) * r, random.nextFloat() * 0.14f * left, (float) Math.cos(a) * r);
                hop[i] = scaled(at, randomSpin(), SicBoLayout.DIE_SCALE);
            }
            boolean first = t == TOSS_TICKS + BOUNCE_STEP;
            later(t, () -> {
                for (int i = 0; i < 3; i++) {
                    move(dice[i], hop[i], BOUNCE_STEP);
                }
                playSound(Sound.BLOCK_AMETHYST_BLOCK_HIT, 1.6f + random.nextFloat() * 0.4f, first ? 1f : 0.5f + left * 0.5f);
            });
        }
        // 決めた目を上にして止める
        Transformation[] rest = new Transformation[3];
        for (int i = 0; i < 3; i++) {
            rest[i] = dieAt(i, roll.die(i), (random.nextFloat() - 0.5f) * 1.2f);
        }
        later(TOSS_TICKS + BOUNCE_TICKS + BOUNCE_STEP, () -> {
            for (int i = 0; i < 3; i++) {
                move(dice[i], rest[i], SETTLE);
            }
            playSound(Sound.BLOCK_AMETHYST_BLOCK_PLACE, 1.4f);
        });
        later(SHOW_AT, () -> {
            if (resultLabel != null) {
                resultLabel.text(resultText());
            }
            Location at = frame.at(LAYOUT.resultLabel());
            at.getWorld().spawnParticle(Particle.END_ROD, at, 15, 0.2, 0.15, 0.2, 0.02);
            playSound(Sound.BLOCK_NOTE_BLOCK_BELL, roll.triple() ? 1.8f : 1.2f);
        });
        return SHOW_AT + 2;
    }

    @Override
    protected Component resultText() {
        Component dice = Component.text(roll.die(0) + "・" + roll.die(1) + "・" + roll.die(2) + " ", NamedTextColor.WHITE);
        if (roll.triple()) {
            return dice.append(Component.text("ゾロ目！", NamedTextColor.GOLD));
        }
        int total = roll.total();
        return dice.append(Component.text("合計 " + total + " ", NamedTextColor.WHITE))
                .append(total <= 10 ? Component.text("小", NamedTextColor.AQUA) : Component.text("大", NamedTextColor.RED))
                .append(Component.text(total % 2 == 1 ? "・奇数" : "・偶数", NamedTextColor.GRAY));
    }

    @Override
    protected long payout(String spot, long amount) {
        return roll.payout(spot, amount);
    }

    @Override
    protected void settled(Seat seat, String spot, long amount, long payout) {
        if (payout > amount && spot.startsWith("triple_")) {
            achieve(seat, Achievements.Achievement.SICBO_TRIPLE);
        }
    }

    @Override
    protected void clearRound() {
        if (resultLabel != null) {
            resultLabel.remove();
        }
        resultLabel = null;
        roll = null;
    }

    @Override
    protected void onDespawn() {
        super.onDespawn();
        java.util.Arrays.fill(dice, null);
    }
}
