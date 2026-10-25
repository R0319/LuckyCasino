package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.horserace.HorseRace;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
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
 * 競馬（単勝）。毎回の出走表で馬ごとの配当が変わる。着順を先に決め、決めた順にゴールするよう、
 * 途中は抜きつ抜かれつの走り方をさせる。
 */
final class HorseRaceTable extends SpotTable {

    private static final HorseRaceLayout LAYOUT = new HorseRaceLayout();
    private static final int STEP = 2;
    private static final String[] COLORS = {"白", "黒", "赤", "青", "黄", "緑"};
    private static final Material[] ICONS = {Material.WHITE_WOOL, Material.BLACK_WOOL, Material.RED_WOOL,
            Material.BLUE_WOOL, Material.YELLOW_WOOL, Material.GREEN_WOOL};
    private static final TextColor[] INKS = {NamedTextColor.WHITE, NamedTextColor.DARK_GRAY, NamedTextColor.RED,
            NamedTextColor.BLUE, NamedTextColor.YELLOW, NamedTextColor.GREEN};
    /** 大穴の実績になる倍率（百分率）。 */
    private static final int LONGSHOT = 800;

    private HorseRace race;
    private int[] order;
    private final ItemDisplay[] horses = new ItemDisplay[HorseRace.HORSES];
    private final TextDisplay[] oddsLabels = new TextDisplay[HorseRace.HORSES];
    private TextDisplay board;

    HorseRaceTable(LuckyCasinoPaper plugin, String id, Frame frame, UUID owner, Bank bank, SpotSettings settings) {
        super(plugin, id, GameType.HORSERACE, frame, owner, bank, settings);
    }

    @Override
    TableShape shape() {
        return LAYOUT;
    }

    private static int horse(String spot) {
        return Integer.parseInt(spot.substring("horse_".length())) - 1;
    }

    static Component horseName(int horse) {
        return Component.text((horse + 1) + " 番（" + COLORS[horse] + "）", INKS[horse]);
    }

    @Override
    List<Spot> spots() {
        List<Spot> out = new ArrayList<>();
        for (int h = 0; h < HorseRace.HORSES; h++) {
            String odds = race == null ? "" : "当たると " + HorseRace.percentText(race.percent(h)) + "が戻る（勝つ確率 約 "
                    + Math.round(race.chance(h) * 100) + "%）";
            out.add(new Spot("horse_" + (h + 1), (h + 1) + " 番（" + COLORS[h] + "）", (h + 1) + " 番の馬が 1 着なら当たり。" + odds, ICONS[h], 19 + h));
        }
        return out;
    }

    @Override
    protected double[] spotCenter(Spot spot) {
        return LAYOUT.box(horse(spot.id()));
    }

    @Override
    protected double[] croupierChips() {
        return LAYOUT.croupierChips();
    }

    @Override
    protected List<String> botSpots(ThreadLocalRandom r) {
        // 強い馬ほど選ばれやすい
        return List.of("horse_" + (race.run(r)[0] + 1));
    }

    @Override
    protected void onSpawn() {
        for (int h = 0; h < HorseRace.HORSES; h++) {
            Location at = frame.at(LAYOUT.start(h));
            String model = "horserace/horse_" + (h + 1);
            horses[h] = at.getWorld().spawn(at, ItemDisplay.class, entity -> {
                entity.setPersistent(false);
                entity.setItemStack(Items.model(model));
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                entity.setTransformation(at(0, 0));
            });
            fixtures().add(horses[h]);
            oddsLabels[h] = label(LAYOUT.oddsLabel(h), 0.45f);
            fixtures().add(oddsLabels[h]);
        }
        board = label(LAYOUT.board(), 0.6f);
        fixtures().add(board);
        newRace();
    }

    /** 出走表を決め直し、枠の上の倍率を出す。 */
    private void newRace() {
        race = HorseRace.card(random);
        order = null;
        for (int h = 0; h < HorseRace.HORSES; h++) {
            if (oddsLabels[h] != null) {
                oddsLabels[h].text(Component.text(HorseRace.percentText(race.percent(h)), NamedTextColor.GOLD));
            }
        }
        if (board != null) {
            board.text(Component.text("次のレースの単勝を受付中", NamedTextColor.AQUA));
        }
    }

    /** スタートから dx 進み、dy 跳ねた形。 */
    private static Transformation at(double dx, double dy) {
        float s = HorseRaceLayout.HORSE_SCALE;
        return new Transformation(new Vector3f((float) dx, (float) dy, 0), new Quaternionf(), new Vector3f(s, s, s), new Quaternionf());
    }

    private static void move(ItemDisplay entity, Transformation to, int ticks) {
        if (entity == null) {
            return;
        }
        entity.setInterpolationDelay(0);
        entity.setInterpolationDuration(ticks);
        entity.setTransformation(to);
    }

    /** u（0〜1）まで走ったときの進み具合（0〜1）。端は 0 と 1 で、途中は馬ごとの癖で前後する。 */
    static double progress(double u, double pace, double wave, double phase) {
        return u + pace * u * (1 - u) + wave * Math.sin(Math.PI * u) * Math.sin(2 * Math.PI * u + phase);
    }

    // ---- 勝負 ----

    @Override
    protected int play() {
        order = race.run(random);
        double length = HorseRaceLayout.FINISH_X - HorseRaceLayout.START_X;
        // 着順どおりのゴールの時刻（tick）
        int[] finish = new int[HorseRace.HORSES];
        int t = 160 + random.nextInt(12);
        for (int place = 0; place < order.length; place++) {
            finish[order[place]] = t;
            t += 2 + random.nextInt(place == 0 ? 3 : 8);
        }
        int last = t;
        double[] pace = new double[HorseRace.HORSES];
        double[] phase = new double[HorseRace.HORSES];
        for (int h = 0; h < HorseRace.HORSES; h++) {
            pace[h] = (random.nextDouble() - 0.5) * 0.6;
            phase[h] = random.nextDouble() * 2 * Math.PI;
        }
        swing();
        broadcast(Component.text("スタートしました！", NamedTextColor.GOLD));
        playSound(Sound.BLOCK_NOTE_BLOCK_BELL, 1.6f);
        for (int tick = STEP; tick <= last + 10; tick += STEP) {
            double[] x = new double[HorseRace.HORSES];
            Transformation[] pose = new Transformation[HorseRace.HORSES];
            for (int h = 0; h < HorseRace.HORSES; h++) {
                double u = (double) tick / finish[h];
                x[h] = u <= 1 ? length * progress(u, pace[h], 0.05, phase[h])
                        : length + 0.15 * (1 - Math.exp(-(tick - finish[h]) / 8.0));
                boolean running = u <= 1.1;
                double bob = running && (tick / STEP + h) % 2 == 0 ? 0.025 : 0;
                pose[h] = at(x[h], bob);
            }
            int leader = 0;
            for (int h = 1; h < HorseRace.HORSES; h++) {
                if (x[h] > x[leader]) {
                    leader = h;
                }
            }
            int lead = leader;
            int now = tick;
            later(tick, () -> {
                for (int h = 0; h < HorseRace.HORSES; h++) {
                    move(horses[h], pose[h], STEP);
                }
                if (now < finish[order[0]] && now % 10 == 0 && board != null) {
                    board.text(Component.text("先頭 ", NamedTextColor.GRAY).append(horseName(lead)));
                }
                if (now % 6 == 0 && now < last) {
                    playSound(Sound.ENTITY_HORSE_GALLOP, 1.0f, 0.4f);
                }
            });
        }
        int winnerAt = finish[order[0]];
        later(winnerAt / 2, () -> broadcast(Component.text("中間地点、先頭は ", NamedTextColor.GRAY).append(horseName(leaderAt(winnerAt / 2, finish, pace, phase, length)))));
        later(winnerAt - 30, () -> broadcast(Component.text("最後の直線！", NamedTextColor.YELLOW)));
        later(winnerAt, () -> {
            if (board != null) {
                board.text(Component.text("1 着 ", NamedTextColor.GOLD).append(horseName(order[0])));
            }
            Location at = frame.at(HorseRaceLayout.FINISH_X, TableShape.TOP + 0.2, HorseRaceLayout.laneZ(order[0]));
            at.getWorld().spawnParticle(Particle.END_ROD, at, 15, 0.1, 0.15, 0.1, 0.03);
            playSound(Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f);
        });
        later(last + 6, () -> {
            if (board != null) {
                board.text(Component.text("1 着 ", NamedTextColor.GOLD).append(horseName(order[0])).append(Component.newline())
                        .append(Component.text("2 着 ", NamedTextColor.GRAY)).append(horseName(order[1]))
                        .append(Component.text("　3 着 ", NamedTextColor.GRAY)).append(horseName(order[2])));
            }
        });
        return last + 8;
    }

    private static int leaderAt(int tick, int[] finish, double[] pace, double[] phase, double length) {
        int leader = 0;
        double best = -1;
        for (int h = 0; h < finish.length; h++) {
            double x = length * progress(Math.min(1, (double) tick / finish[h]), pace[h], 0.05, phase[h]);
            if (x > best) {
                best = x;
                leader = h;
            }
        }
        return leader;
    }

    @Override
    protected Component resultText() {
        return Component.text("1 着 ", NamedTextColor.GOLD).append(horseName(order[0]))
                .append(Component.text("（" + HorseRace.percentText(race.percent(order[0])) + "）", NamedTextColor.WHITE));
    }

    @Override
    protected long payout(String spot, long amount) {
        return race.payout(horse(spot), amount, order[0]);
    }

    @Override
    protected void settled(Seat seat, String spot, long amount, long payout) {
        if (payout > amount && race.percent(horse(spot)) >= LONGSHOT) {
            achieve(seat, Achievements.Achievement.HORSERACE_LONGSHOT);
        }
    }

    /** 馬をスタートへ歩いて戻し、次のレースの出走表を出す。 */
    @Override
    protected void clearRound() {
        for (ItemDisplay horse : horses) {
            move(horse, at(0, 0), 20);
        }
        if (order != null) {
            newRace();
        }
    }

    @Override
    protected void onDespawn() {
        super.onDespawn();
        java.util.Arrays.fill(horses, null);
        java.util.Arrays.fill(oddsLabels, null);
        board = null;
    }
}
