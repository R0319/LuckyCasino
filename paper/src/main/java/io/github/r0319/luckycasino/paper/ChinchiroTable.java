package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.chinchiro.Chinchiro;
import io.github.r0319.luckycasino.chinchiro.Chinchiro.Hand;
import io.github.r0319.luckycasino.chinchiro.Chinchiro.Kind;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * チンチロを座敷で。子（客）が張ったら締め切り、まず奥に座った親（ディーラー）がどんぶりにサイコロ 3 個を振る。
 * 親の役で決まらなければ（2〜5 の目）、張った子が席の順に自分で振る（/casino roll かどんぶりのクリック。
 * 時間切れやボットは自動で振る）。役が出るまで 1 回の番に 3 回まで。役と配当は common の Chinchiro。
 * 出目は振るたびに乱数で決め、どんぶりの中ででたらめに跳ねさせてから、その目を上にして止める。
 */
final class ChinchiroTable extends SpotTable {

    private static final ChinchiroLayout LAYOUT = new ChinchiroLayout();
    /** 手からどんぶりに入るまで（tick）。 */
    private static final int TOSS_TICKS = 10;
    private static final int TOSS_STEP = 2;
    /** どんぶりの中で跳ねている間（tick）。 */
    private static final int BOUNCE_TICKS = 24;
    private static final int BOUNCE_STEP = 3;
    private static final int SETTLE = 6;
    /** 振ってから役が見えるまで（tick）。 */
    private static final int ROLL_TICKS = TOSS_TICKS + BOUNCE_TICKS + SETTLE + 6;
    /** 役が見えてから次へ進むまで（tick）。 */
    private static final int PAUSE = 30;
    /** ボットや席にいない子が振るまでの間（tick）。 */
    private static final int AUTO_DELAY = 20;
    /** 子が自分で振るのを待つ時間（tick）。過ぎたら自動で振る。 */
    private static final int TURN_TICKS = 15 * 20;
    /** 止まったサイコロのどんぶりの中での位置（基準からのずれ）。 */
    private static final float[][] REST = {{-0.09f, 0.05f}, {0.09f, 0.06f}, {0f, -0.09f}};

    private static final List<Spot> SPOTS = List.of(new Spot("bet", "子として張る",
            "親の役と勝負します。勝てば役の倍率（ピンゾロ 5 倍・ゾロ目 3 倍・シゴロ 2 倍・目 1 倍）で受け取り、"
                    + "負ければ親の役の倍率で払います（最大 5 倍）。張ったあとも賭け金の 4 倍を手元に残してください",
            Material.BOWL, 22));

    private final ItemDisplay[] dice = new ItemDisplay[3];
    private TextDisplay resultLabel;
    private final TextDisplay[] handLabels = new TextDisplay[ChinchiroLayout.SEATS];
    /** 親の役（振り終わるまで null）。 */
    private Hand dealerHand;
    /** 席ごとの、子から見た倍率（決まるまで null）。 */
    private final Integer[] results = new Integer[ChinchiroLayout.SEATS];
    /** 席ごとの子の役（振らなかった席は null）。 */
    private final Hand[] hands = new Hand[ChinchiroLayout.SEATS];
    private final Deque<Integer> queue = new ArrayDeque<>();
    /** 振る番の席（-1 は親、-2 は誰の番でもない）。 */
    private int turn = -2;
    /** 今の番で振った回数。 */
    private int tries;
    /** 子が振るのを待っている（振ったら false）。 */
    private boolean waiting;
    /** 古い時間切れの予約を見分ける番号。 */
    private int turnToken;

    ChinchiroTable(LuckyCasinoPaper plugin, String id, Frame frame, UUID owner, Bank bank, SpotSettings settings) {
        super(plugin, id, GameType.CHINCHIRO, frame, owner, bank, settings);
    }

    @Override
    TableShape shape() {
        return LAYOUT;
    }

    @Override
    List<Spot> spots() {
        return SPOTS;
    }

    @Override
    protected double[] spotCenter(Spot spot) {
        return LAYOUT.chip(0);
    }

    @Override
    protected double[] chipSpot(Spot spot, int seat) {
        return LAYOUT.chip(seat);
    }

    @Override
    protected double[] croupierChips() {
        return LAYOUT.croupierChips();
    }

    @Override
    protected List<String> botSpots(ThreadLocalRandom r) {
        return List.of("bet");
    }

    @Override
    protected long reserve(long total) {
        return total * Chinchiro.RESERVE;
    }

    @Override
    protected void onSpawn() {
        Location bowlAt = frame.at(LAYOUT.bowl());
        fixtures().add(bowlAt.getWorld().spawn(bowlAt, ItemDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setItemStack(Items.model("chinchiro/bowl"));
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        }));
        Location diceAt = frame.at(LAYOUT.dice());
        int[] faces = {4, 5, 6};
        for (int i = 0; i < 3; i++) {
            Transformation rest = dieAt(i, faces[i], 0.4f * i);
            dice[i] = diceAt.getWorld().spawn(diceAt, ItemDisplay.class, entity -> {
                entity.setPersistent(false);
                entity.setItemStack(Items.model("chinchiro/die"));
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
        return scaled(new Vector3f(REST[i][0], 0, REST[i][1]), new Quaternionf().rotateY(yaw).mul(faceUp(face)), ChinchiroLayout.DIE_SCALE);
    }

    private static void move(ItemDisplay entity, Transformation to, int ticks) {
        if (entity == null) {
            return;
        }
        entity.setInterpolationDelay(0);
        entity.setInterpolationDuration(ticks);
        entity.setTransformation(to);
    }

    private Quaternionf randomSpin() {
        return new Quaternionf().rotateXYZ(random.nextFloat() * 6.3f, random.nextFloat() * 6.3f, random.nextFloat() * 6.3f);
    }

    /** 席 seat（-1 は親）がサイコロを振る演出。roll の目で止まり、ROLL_TICKS で役が見える。 */
    private void animateRoll(int seat, Chinchiro.Roll roll) {
        float[] h = ChinchiroLayout.hand(seat);
        Vector3f hand = new Vector3f(h[0], h[1], h[2]);
        for (int i = 0; i < 3; i++) {
            Vector3f from = new Vector3f(hand).add((i - 1) * 0.06f, 0, 0);
            Vector3f land = new Vector3f((random.nextFloat() - 0.5f) * 0.25f, 0.08f, (random.nextFloat() - 0.5f) * 0.25f);
            move(dice[i], scaled(from, randomSpin(), ChinchiroLayout.DIE_SCALE), 0);
            for (int t = TOSS_STEP; t <= TOSS_TICKS; t += TOSS_STEP) {
                float u = (float) t / TOSS_TICKS;
                Vector3f at = new Vector3f(from).lerp(land, u).add(0, 0.35f * 4 * u * (1 - u), 0);
                Transformation pose = scaled(at, randomSpin(), ChinchiroLayout.DIE_SCALE);
                int die = i;
                later(t, () -> move(dice[die], pose, TOSS_STEP));
            }
        }
        for (int t = TOSS_TICKS + BOUNCE_STEP; t <= TOSS_TICKS + BOUNCE_TICKS; t += BOUNCE_STEP) {
            float left = 1f - (float) (t - TOSS_TICKS) / BOUNCE_TICKS;
            Transformation[] hop = new Transformation[3];
            for (int i = 0; i < 3; i++) {
                double a = random.nextDouble() * 2 * Math.PI;
                float r = 0.08f + random.nextFloat() * 0.12f;
                Vector3f at = new Vector3f((float) Math.sin(a) * r, random.nextFloat() * 0.14f * left, (float) Math.cos(a) * r);
                hop[i] = scaled(at, randomSpin(), ChinchiroLayout.DIE_SCALE);
            }
            float volume = 0.5f + left * 0.5f;
            later(t, () -> {
                for (int i = 0; i < 3; i++) {
                    move(dice[i], hop[i], BOUNCE_STEP);
                }
                playSound(Sound.BLOCK_AMETHYST_BLOCK_HIT, 1.6f + random.nextFloat() * 0.4f, volume);
            });
        }
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
    }

    private static NamedTextColor color(Hand hand) {
        return switch (hand.kind()) {
            case PINZORO, ZORO -> NamedTextColor.GOLD;
            case SHIGORO -> NamedTextColor.LIGHT_PURPLE;
            case ME -> NamedTextColor.WHITE;
            case MENASHI -> NamedTextColor.GRAY;
            case HIFUMI -> NamedTextColor.RED;
        };
    }

    private static Component handText(Chinchiro.Roll roll) {
        return Component.text(roll.a() + "・" + roll.b() + "・" + roll.c() + " ", NamedTextColor.GRAY)
                .append(Component.text(roll.hand().label(), color(roll.hand())));
    }

    private String who(int seat) {
        return seat < 0 ? "親" : seats[seat] != null ? seats[seat].name : "子";
    }

    /** 子から見た倍率を短く。 */
    private static Component outcome(int m) {
        return m > 0 ? Component.text("勝ち +" + m + " 倍", NamedTextColor.GREEN)
                : m == 0 ? Component.text("引き分け", NamedTextColor.YELLOW)
                : Component.text("負け " + m + " 倍", NamedTextColor.RED);
    }

    // ---- 勝負 ----

    @Override
    protected int play() {
        Arrays.fill(results, null);
        Arrays.fill(hands, null);
        dealerHand = null;
        queue.clear();
        for (int i = 0; i < seats.length; i++) {
            if (total(i) > 0) {
                queue.add(i);
            }
        }
        resultLabel = label(LAYOUT.resultLabel(), 0.7f);
        swing();
        startTurn(-1);
        return UNTIL_FINISHED;
    }

    /** 席 seat（-1 は親）の番を始める。 */
    private void startTurn(int seat) {
        turn = seat;
        tries = 0;
        turnToken++;
        if (seat < 0) {
            broadcast(Component.text("親が振ります", NamedTextColor.GRAY));
            later(10, this::rollNow);
            updateStatus();
            return;
        }
        nextTry();
    }

    /** 子の番で、次の 1 回を振る準備（人なら待ち、ボットなどはすぐ振る）。 */
    private void nextTry() {
        int seat = turn;
        Seat s = seats[seat];
        Player player = s == null || s.bot || s.away ? null : online(s);
        int token = ++turnToken;
        waiting = true;
        updateStatus();
        if (player == null) {
            later(AUTO_DELAY, () -> {
                if (token == turnToken && waiting) {
                    rollNow();
                }
            });
            return;
        }
        player.sendMessage(Component.text("あなたの番です（" + (tries + 1) + " / " + Chinchiro.TRIES + " 回目） ", NamedTextColor.AQUA)
                .append(Component.text("[振る]", NamedTextColor.GOLD).clickEvent(ClickEvent.runCommand("/casino roll")))
                .append(Component.text(" かどんぶりをクリック。" + TURN_TICKS / 20 + " 秒で自動で振ります", NamedTextColor.GRAY)));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.4f);
        later(TURN_TICKS, () -> {
            if (token == turnToken && waiting) {
                rollNow();
            }
        });
    }

    /** /casino roll やどんぶりのクリック。自分の番で、振るのを待っているときだけ振る。 */
    void roll(Player player) {
        int seat = seatOf(player.getUniqueId());
        if (seat < 0 || state() != State.PLAYING || turn != seat || !waiting) {
            player.sendMessage(Component.text("今はあなたの振る番ではありません", NamedTextColor.GRAY));
            return;
        }
        rollNow();
    }

    @Override
    void openMenu(Player player) {
        int seat = seatOf(player.getUniqueId());
        if (seat >= 0 && state() == State.PLAYING && turn == seat && waiting) {
            rollNow();
            return;
        }
        super.openMenu(player);
    }

    private void rollNow() {
        int seat = turn;
        waiting = false;
        tries++;
        Chinchiro.Roll roll = Chinchiro.roll(random);
        if (seat < 0) {
            swing();
        }
        animateRoll(seat, roll);
        later(ROLL_TICKS, () -> rolled(seat, roll));
        updateStatus();
    }

    /** 1 回振った役が見えた。 */
    private void rolled(int seat, Chinchiro.Roll roll) {
        Hand hand = roll.hand();
        if (resultLabel != null) {
            resultLabel.text(Component.text(who(seat) + " ", NamedTextColor.AQUA).append(handText(roll)));
        }
        broadcast(Component.text(who(seat) + "（" + tries + " 回目）: ", NamedTextColor.GRAY).append(handText(roll)));
        if (hand.kind() == Kind.PINZORO || hand.kind() == Kind.ZORO || hand.kind() == Kind.SHIGORO) {
            Location at = frame.at(LAYOUT.resultLabel());
            at.getWorld().spawnParticle(Particle.END_ROD, at, 20, 0.2, 0.15, 0.2, 0.03);
            playSound(Sound.BLOCK_NOTE_BLOCK_BELL, 1.8f);
        } else if (hand.kind() == Kind.HIFUMI) {
            playSound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f);
        }
        if (hand.kind() == Kind.MENASHI && tries < Chinchiro.TRIES) {
            later(PAUSE / 2, () -> {
                if (seat < 0) {
                    rollNow();
                } else {
                    nextTry();
                }
            });
            return;
        }
        if (seat < 0) {
            dealerDone(hand);
        } else {
            playerDone(seat, hand);
        }
    }

    private void dealerDone(Hand hand) {
        dealerHand = hand;
        Integer settled = Chinchiro.settledByDealer(hand);
        if (settled != null) {
            for (int seat : queue) {
                results[seat] = settled;
            }
            queue.clear();
            broadcast(Component.text("親の " + hand.label() + " で決まりました。子は ", NamedTextColor.YELLOW).append(outcome(settled)));
            later(PAUSE, this::finish);
            return;
        }
        broadcast(Component.text("親は " + hand.label() + "。子が順に振ります", NamedTextColor.YELLOW));
        later(PAUSE, this::nextPlayer);
    }

    private void playerDone(int seat, Hand hand) {
        hands[seat] = hand;
        int m = Chinchiro.versus(dealerHand, hand);
        results[seat] = m;
        showHand(seat, Component.text(hand.label() + " ", color(hand)).append(outcome(m)));
        later(PAUSE, this::nextPlayer);
    }

    private void nextPlayer() {
        Integer seat = queue.poll();
        if (seat == null) {
            finish();
            return;
        }
        startTurn(seat);
    }

    private void finish() {
        turn = -2;
        waiting = false;
        updateStatus();
        playFinished();
    }

    /** 子の役と勝ち負けを、その席のチップの上に出す。 */
    private void showHand(int seat, Component text) {
        if (handLabels[seat] == null) {
            handLabels[seat] = label(LAYOUT.handLabel(seat), 0.45f);
        }
        handLabels[seat].text(text);
    }

    @Override
    protected Component resultText() {
        return dealerHand == null ? Component.text("勝負なし", NamedTextColor.GRAY)
                : Component.text("親 ", NamedTextColor.WHITE).append(Component.text(dealerHand.label(), color(dealerHand)));
    }

    @Override
    protected long payout(String spot, long amount) {
        return amount;
    }

    @Override
    protected long payout(int seat, String spot, long amount) {
        Integer m = results[seat];
        return Chinchiro.back(amount, m == null ? 0 : m);
    }

    @Override
    protected void settled(Seat seat, String spot, long amount, long payout) {
        for (int i = 0; i < seats.length; i++) {
            if (seats[i] == seat && hands[i] != null && hands[i].kind() == Kind.PINZORO) {
                achieve(seat, Achievements.Achievement.CHINCHIRO_PINZORO);
            }
        }
    }

    @Override
    protected Component statusText() {
        if (state() != State.PLAYING || turn == -2) {
            return super.statusText();
        }
        Component text = turn < 0 ? Component.text("親が振っています", NamedTextColor.YELLOW)
                : Component.text(who(turn) + " の番（" + Math.max(tries + (waiting ? 1 : 0), 1) + " / " + Chinchiro.TRIES + " 回目）", NamedTextColor.YELLOW);
        if (dealerHand != null) {
            text = text.append(Component.newline()).append(Component.text("親 " + dealerHand.label(), color(dealerHand)));
        }
        return text;
    }

    @Override
    protected void clearRound() {
        if (resultLabel != null) {
            resultLabel.remove();
        }
        resultLabel = null;
        for (int i = 0; i < handLabels.length; i++) {
            if (handLabels[i] != null) {
                handLabels[i].remove();
                handLabels[i] = null;
            }
        }
        turn = -2;
        waiting = false;
        turnToken++;
        queue.clear();
    }

    @Override
    protected void onDespawn() {
        super.onDespawn();
        Arrays.fill(dice, null);
    }
}
