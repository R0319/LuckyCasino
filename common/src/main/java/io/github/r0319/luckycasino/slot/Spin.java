package io.github.r0319.luckycasino.slot;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1 回の回転の結果。止まる位置を先に決め、リールの動きはそこに止まるように逆算する。
 * 選んだラインそれぞれに 1 ライン分の賭け金を賭け、ラインごとの配当を足したものが戻る。
 *
 * @param stops   リールごとの、中央の段に止まるコマ
 * @param lineBet 1 ラインの賭け金
 * @param lineSet 賭けたライン
 * @param wins    当たったライン（Line の順）
 * @param payout  戻ってくるチップ（賭け金を含む。外れは 0）
 */
public record Spin(int[] stops, long lineBet, LineSet lineSet, List<LineWin> wins, long payout) {

    public static Spin of(int[] stops, long lineBet, LineSet lineSet, Paytable paytable) {
        if (stops.length != Reels.REELS || lineBet <= 0 || lineSet == null) {
            throw new IllegalArgumentException("invalid spin");
        }
        List<LineWin> wins = new ArrayList<>();
        long payout = 0;
        for (Line line : lineSet.lines()) {
            Paytable.Win win = paytable.evaluate(line.symbols(stops));
            if (win.times() > 0) {
                wins.add(new LineWin(line, win));
                payout = Math.addExact(payout, Math.multiplyExact(lineBet, win.times()));
            }
        }
        return new Spin(stops.clone(), lineBet, lineSet, List.copyOf(wins), payout);
    }

    /** 1 回の賭け金（1 ライン × 本数）。 */
    public long bet() {
        return lineBet * lineSet.size();
    }

    /** そのラインにいくら戻ったか。 */
    public long payout(LineWin win) {
        return lineBet * win.win().times();
    }

    public static Spin random(Random random, long lineBet, LineSet lineSet, Paytable paytable) {
        int[] stops = new int[Reels.REELS];
        for (int i = 0; i < stops.length; i++) {
            stops[i] = random.nextInt(Reels.STOPS);
        }
        return of(stops, lineBet, lineSet, paytable);
    }

    /**
     * リーチになる、賭けたライン（左の 2 本が 7 か BAR でそろう）。右のリールを長く回して見せる。
     */
    public static List<Line> reachLines(int[] stops, LineSet lineSet) {
        List<Line> lines = new ArrayList<>();
        for (Line line : lineSet.lines()) {
            Symbol[] s = line.symbols(stops);
            if (s[0] == s[1] && (s[0] == Symbol.SEVEN || s[0] == Symbol.BAR)) {
                lines.add(line);
            }
        }
        return lines;
    }

    public List<Line> reachLines() {
        return reachLines(stops, lineSet);
    }

    /** 大当たり（7 か BAR が 3 つ、またはチェリー 3 つ）のラインがあるか。 */
    public boolean big() {
        return wins.stream().anyMatch(w -> w.win().big());
    }

    /** 一番配当の大きい当たり（外れは null）。 */
    public LineWin best() {
        LineWin best = null;
        for (LineWin w : wins) {
            if (best == null || w.win().times() > best.win().times()) {
                best = w;
            }
        }
        return best;
    }

    /**
     * 当たったライン。
     *
     * @param line ライン
     * @param win  役（times は 1 ラインの賭け金の何倍か）
     */
    public record LineWin(Line line, Paytable.Win win) {
    }
}
