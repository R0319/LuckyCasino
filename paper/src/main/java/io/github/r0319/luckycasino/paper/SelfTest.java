package io.github.r0319.luckycasino.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ボットだけで卓を回す自己テスト（/casino selftest）。ワールドの初期地点の近くに保存しない卓を出し、
 * 全席にボットを座らせて指定した回数だけ勝負させ、毎回次を確かめる。
 * <ul>
 *   <li>チップの合計 = 最初の合計 + 補充した分 + 払い戻し − 賭け金（ポーカーは参加者の間で動くだけ）</li>
 *   <li>勝負の区切りで賭けたままのチップ（atRisk）が残っていない</li>
 *   <li>勝負の区切りで、卓の備品以外のエンティティ（カード・チップ・文字）が残っていない</li>
 *   <li>進行中に例外が起きていない、進行が止まっていない</li>
 * </ul>
 * leave を付けると、勝負の途中（賭けたチップがある間）にボットを席から立たせ、切断・ログアウトと同じ扱いになるかも確かめる。
 * 勝負中に立った席はすぐには精算されず、勝負が最後まで進んでから精算される（賭けが戻って負け逃げできない）こと。
 */
final class SelfTest {

    /** 卓から自己テストへの知らせ。 */
    interface Observer {
        void error(Throwable error);

        /** 席を立った（ボットのチップを回収した）。 */
        void cashOut(int seat, long total);

        /** 賭けが 1 つ精算された（払い戻しは賭け金込み。負けは 0）。 */
        void payout(int seat, long bet, long payout);

        /** 勝負が 1 回終わり、片付けと精算が済んだ。 */
        void roundFinished();
    }

    private static final long BOT_CHIPS = 1000;
    /** 1 回の勝負がこれより長くかかったら止まったとみなす（秒）。 */
    private static final int STALL_SECONDS = 180;
    private static SelfTest running;

    private final LuckyCasinoPaper plugin;
    private final GameType type;
    private final int rounds;
    private final CommandSender sender;
    private final boolean leaveTest;
    private CasinoTable table;
    private Chunk chunk;
    private BukkitTask watchdog;

    private long supplied;
    private long cashedOut;
    private long net;
    private long bets;
    private int finished;
    private int idleSeconds;
    private final List<String> failures = new ArrayList<>();
    private int errors;
    private int leaves;

    private SelfTest(LuckyCasinoPaper plugin, GameType type, int rounds, CommandSender sender, boolean leaveTest) {
        this.plugin = plugin;
        this.type = type;
        this.rounds = rounds;
        this.sender = sender;
        this.leaveTest = leaveTest;
    }

    static void start(LuckyCasinoPaper plugin, GameType type, int rounds, CommandSender sender, boolean leaveTest) {
        if (running != null) {
            sender.sendMessage(L10n.tr("自己テストはもう動いています（%s）", L10n.tr(running.type.displayName())).color(NamedTextColor.RED));
            return;
        }
        running = new SelfTest(plugin, type, Math.max(1, Math.min(rounds, 200)), sender, leaveTest);
        running.begin();
    }

    static void stopAll() {
        if (running != null) {
            running.finish(L10n.plain("プラグインの停止で中断しました"));
        }
    }

    private void begin() {
        World world = plugin.getServer().getWorlds().getFirst();
        Location spawn = world.getSpawnLocation();
        int x = spawn.getBlockX() + 12;
        int z = spawn.getBlockZ() + 12;
        chunk = world.getChunkAt(x >> 4, z >> 4);
        chunk.addPluginChunkTicket(plugin);
        int y = world.getHighestBlockYAt(x, z) + 1;
        double center = type.singleBlock() ? 0.5 : 0;
        Frame frame = new Frame(world, x + center, y, z + center, 0f);
        String id = "selftest-" + UUID.randomUUID().toString().substring(0, 4);
        table = plugin.tables().createTable(type, id, frame, null, true);
        table.observer = new Observer() {
            @Override
            public void error(Throwable error) {
                errors++;
                fail(L10n.plain("例外: %s", error));
            }

            @Override
            public void cashOut(int seat, long total) {
                cashedOut += total;
            }

            @Override
            public void payout(int seat, long bet, long payout) {
                bets++;
                net += payout - bet;
            }

            @Override
            public void roundFinished() {
                onRoundFinished();
            }
        };
        table.spawn();
        for (int i = 0; i < table.seatCount(); i++) {
            seatBot(i);
        }
        log(L10n.plain("開始: %s を %s 回（%s %s %s %s、ボット %s 人）%s", L10n.tr(type.displayName()), rounds, world.getName(), x, y, z, table.seatCount(),
                leaveTest ? L10n.plain("、途中で席を立つ") : ""));
        watchdog = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (leaveTest) {
                leaveMidRound();
            }
            if (++idleSeconds > STALL_SECONDS) {
                fail(L10n.plain("進行が %s 秒止まっています（%s 回目のあと）", STALL_SECONDS, finished));
                finish(null);
            }
        }, 20L, 20L);
    }

    /** 賭けたチップがあるボットを 1 人選んで席を立たせる（切断・ログアウトと同じ処理）。すぐ精算されたら失敗。 */
    private void leaveMidRound() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        // 賭けを受け付けている間に立つと置いた賭けが戻る（勝負前なので正しい）。勝負が始まってからだけ試す
        if (random.nextInt(3) != 0 || table.bettingOpen()) {
            return;
        }
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < table.seatCount(); i++) {
            CasinoTable.Seat seat = table.seat(i);
            if (seat != null && seat.bot && !seat.away && seat.atRisk > 0) {
                candidates.add(i);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        int index = candidates.get(random.nextInt(candidates.size()));
        CasinoTable.Seat seat = table.seat(index);
        long atRisk = seat.atRisk;
        table.leave(seat.player);
        CasinoTable.Seat after = table.seat(index);
        if (after == seat && after.away) {
            leaves++;
        } else if (after == null) {
            fail(L10n.plain("勝負中（賭け %s 枚）に席を立ったのに、勝負の終わりを待たずに精算されました（席 %s）", atRisk, index + 1));
        }
    }

    private void seatBot(int index) {
        supplied += BOT_CHIPS;
        table.sitBot(index, "Bot" + (index + 1), BOT_CHIPS);
    }

    private void onRoundFinished() {
        finished++;
        idleSeconds = 0;
        check(L10n.plain("勝負 %s", finished));
        plugin.getLogger().info(L10n.plain("[自己テスト] %s/%s 回目まで終了（問題 %s 件）", finished, rounds, failures.size()));
        if (finished >= rounds) {
            // 卓の処理の途中なので、片付けは次の tick で
            plugin.getServer().getScheduler().runTask(plugin, () -> finish(null));
            return;
        }
        // チップが尽きて立ったボットの席を埋め直す（ポーカーが 1 人にならないように）
        for (int i = 0; i < table.seatCount(); i++) {
            if (table.seat(i) == null) {
                seatBot(i);
            }
        }
    }

    private void check(String when) {
        long onTable = 0;
        for (int i = 0; i < table.seatCount(); i++) {
            CasinoTable.Seat seat = table.seat(i);
            if (seat == null) {
                continue;
            }
            if (seat.atRisk != 0) {
                fail(L10n.plain("%s: 席 %s に賭けたままのチップ %s 枚", when, i + 1, seat.atRisk));
            }
            if (seat.chips < 0) {
                fail(L10n.plain("%s: 席 %s のチップがマイナス（%s）", when, i + 1, seat.chips));
            }
            onTable += seat.chips + seat.atRisk;
        }
        long expected = supplied + net;
        if (onTable + cashedOut != expected) {
            fail(L10n.plain("%s: チップの合計が合いません（卓 %s + 回収 %s ≠ %s）", when, onTable, cashedOut, expected));
        }
        List<String> leftovers = leftovers();
        if (!leftovers.isEmpty()) {
            fail(L10n.plain("%s: 片付け漏れのエンティティ %s 個 %s", when, leftovers.size(), leftovers));
        }
    }

    /** 卓の周りにある、備品でもプレイヤーでもないエンティティ。 */
    private List<String> leftovers() {
        Location center = table.frame().at(0, 1, 0);
        List<String> out = new ArrayList<>();
        for (Entity entity : center.getWorld().getNearbyEntities(center, 4, 3, 4)) {
            if (entity instanceof Player || !entity.isValid() || table.owns(entity)) {
                continue;
            }
            out.add(entity.getType().name().toLowerCase());
        }
        return out;
    }

    private void fail(String message) {
        if (failures.size() < 20) {
            failures.add(message);
        }
        plugin.getLogger().warning(L10n.plain("[自己テスト] %s", message));
    }

    private void finish(String note) {
        if (running != this) {
            return;
        }
        running = null;
        if (watchdog != null) {
            watchdog.cancel();
        }
        table.despawn();
        long onTable = 0;
        for (int i = 0; i < table.seatCount(); i++) {
            if (table.seat(i) != null) {
                fail(L10n.plain("片付けたあとも席 %s が埋まっています", i + 1));
                onTable += table.seat(i).chips + table.seat(i).atRisk;
            }
        }
        if (onTable + cashedOut != supplied + net) {
            fail(L10n.plain("片付けたあとのチップの合計が合いません（回収 %s ≠ %s）", cashedOut, supplied + net));
        }
        List<String> leftovers = new ArrayList<>();
        Location center = table.frame().at(0, 1, 0);
        for (Entity entity : center.getWorld().getNearbyEntities(center, 4, 3, 4)) {
            if (!(entity instanceof Player) && entity.isValid()) {
                leftovers.add(entity.getType().name().toLowerCase());
            }
        }
        if (!leftovers.isEmpty()) {
            fail(L10n.plain("片付けたあとも残っているエンティティ %s", leftovers));
        }
        chunk.removePluginChunkTicket(plugin);
        if (note != null) {
            fail(note);
        }
        boolean ok = failures.isEmpty() && finished >= rounds;
        log(L10n.plain("%s: %s %s/%s 回、精算 %s 件、胴元から見た損益 %s 枚、補充 %s 枚、例外 %s 件、問題 %s 件",
                L10n.tr(ok ? "成功" : "失敗"), L10n.tr(type.displayName()), finished, rounds, bets, -net, supplied, errors, failures.size()));
        if (leaveTest) {
            log(L10n.plain("勝負の途中で席を立った回数 %s", leaves));
        }
        failures.forEach(message -> log(" - " + message));
    }

    private void log(String message) {
        plugin.getLogger().info(L10n.plain("[自己テスト] %s", message));
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
            sender.sendMessage(L10n.tr("[自己テスト] %s", message).color(NamedTextColor.GRAY));
        }
    }
}
