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

/**
 * ボットだけで卓を回す自己テスト（/casino selftest）。ワールドの初期地点の近くに保存しない卓を出し、
 * 全席にボットを座らせて指定した回数だけ勝負させ、毎回次を確かめる。
 * <ul>
 *   <li>チップの合計 = 最初の合計 + 補充した分 + 払い戻し − 賭け金（ポーカーは参加者の間で動くだけ）</li>
 *   <li>勝負の区切りで賭けたままのチップ（atRisk）が残っていない</li>
 *   <li>勝負の区切りで、卓の備品以外のエンティティ（カード・チップ・文字）が残っていない</li>
 *   <li>進行中に例外が起きていない、進行が止まっていない</li>
 * </ul>
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

    private SelfTest(LuckyCasinoPaper plugin, GameType type, int rounds, CommandSender sender) {
        this.plugin = plugin;
        this.type = type;
        this.rounds = rounds;
        this.sender = sender;
    }

    static void start(LuckyCasinoPaper plugin, GameType type, int rounds, CommandSender sender) {
        if (running != null) {
            sender.sendMessage(Component.text("自己テストはもう動いています（" + running.type.displayName() + "）", NamedTextColor.RED));
            return;
        }
        running = new SelfTest(plugin, type, Math.max(1, Math.min(rounds, 200)), sender);
        running.begin();
    }

    static void stopAll() {
        if (running != null) {
            running.finish("プラグインの停止で中断しました");
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
        Frame frame = new Frame(world, x, y, z, 0f);
        String id = "selftest-" + UUID.randomUUID().toString().substring(0, 4);
        table = plugin.tables().createTable(type, id, frame, null, true);
        table.observer = new Observer() {
            @Override
            public void error(Throwable error) {
                errors++;
                fail("例外: " + error);
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
        log("開始: " + type.displayName() + " を " + rounds + " 回（" + world.getName() + " " + x + " " + y + " " + z + "、ボット "
                + table.seatCount() + " 人）");
        watchdog = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (++idleSeconds > STALL_SECONDS) {
                fail("進行が " + STALL_SECONDS + " 秒止まっています（" + finished + " 回目のあと）");
                finish(null);
            }
        }, 20L, 20L);
    }

    private void seatBot(int index) {
        supplied += BOT_CHIPS;
        table.sitBot(index, "Bot" + (index + 1), BOT_CHIPS);
    }

    private void onRoundFinished() {
        finished++;
        idleSeconds = 0;
        check("勝負 " + finished);
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
                fail(when + ": 席 " + (i + 1) + " に賭けたままのチップ " + seat.atRisk + " 枚");
            }
            if (seat.chips < 0) {
                fail(when + ": 席 " + (i + 1) + " のチップがマイナス（" + seat.chips + "）");
            }
            onTable += seat.chips + seat.atRisk;
        }
        long expected = supplied + net;
        if (onTable + cashedOut != expected) {
            fail(when + ": チップの合計が合いません（卓 " + onTable + " + 回収 " + cashedOut + " ≠ " + expected + "）");
        }
        List<String> leftovers = leftovers();
        if (!leftovers.isEmpty()) {
            fail(when + ": 片付け漏れのエンティティ " + leftovers.size() + " 個 " + leftovers);
        }
    }

    /** 卓の周りにある、備品でもプレイヤーでもないエンティティ。 */
    private List<String> leftovers() {
        Location center = table.frame().at(0, 1, 0);
        List<String> out = new ArrayList<>();
        for (Entity entity : center.getWorld().getNearbyEntities(center, 4, 3, 4)) {
            if (entity instanceof Player || !entity.isValid() || table.fixtures().contains(entity)) {
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
        plugin.getLogger().warning("[自己テスト] " + message);
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
                fail("片付けたあとも席 " + (i + 1) + " が埋まっています");
                onTable += table.seat(i).chips + table.seat(i).atRisk;
            }
        }
        if (onTable + cashedOut != supplied + net) {
            fail("片付けたあとのチップの合計が合いません（回収 " + cashedOut + " ≠ " + (supplied + net) + "）");
        }
        List<String> leftovers = new ArrayList<>();
        Location center = table.frame().at(0, 1, 0);
        for (Entity entity : center.getWorld().getNearbyEntities(center, 4, 3, 4)) {
            if (!(entity instanceof Player) && entity.isValid()) {
                leftovers.add(entity.getType().name().toLowerCase());
            }
        }
        if (!leftovers.isEmpty()) {
            fail("片付けたあとも残っているエンティティ " + leftovers);
        }
        chunk.removePluginChunkTicket(plugin);
        if (note != null) {
            fail(note);
        }
        boolean ok = failures.isEmpty() && finished >= rounds;
        log((ok ? "成功" : "失敗") + ": " + type.displayName() + " " + finished + "/" + rounds + " 回、精算 " + bets + " 件、"
                + "胴元から見た損益 " + (-net) + " 枚、補充 " + supplied + " 枚、例外 " + errors + " 件、問題 " + failures.size() + " 件");
        failures.forEach(message -> log(" - " + message));
    }

    private void log(String message) {
        plugin.getLogger().info("[自己テスト] " + message);
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
            sender.sendMessage(Component.text("[自己テスト] " + message, NamedTextColor.GRAY));
        }
    }
}
