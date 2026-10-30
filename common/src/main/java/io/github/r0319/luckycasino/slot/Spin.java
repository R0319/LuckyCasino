package io.github.r0319.luckycasino.slot;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1 回の回転の結果。止まる位置を先に決め、リールの動きはそこに止まるように逆算する。
 * 賭け金は 5 本のラインに等分し（1 ライン = 賭け金 ÷ 5）、ラインごとの配当を足したものが戻る。
 *
 * @param stops  リールごとの、中央の段に止まるコマ
 * @param bet    賭け金（5 で割り切れること）
 * @param wins   当たったライン（Line の順）
 * @param payout 戻ってくるチップ（賭け金を含む。外れは 0）
 */
public record Spin(int[] stops, long bet, List<LineWin> wins, long payout) {

    public static Spin of(int[] stops, long bet, Paytable paytable) {
        if (stops.length != Reels.REELS || !validBet(bet)) {
            throw new IllegalArgumentException("invalid spin");
        }
        long lineBet = bet / Line.COUNT;
        List<LineWin> wins = new ArrayList<>();
        long payout = 0;
        for (Line line : Line.values()) {
            Paytable.Win win = paytable.evaluate(line.symbols(stops));
            if (win.times() > 0) {
                wins.add(new LineWin(line, win));
                payout = Math.addExact(payout, Math.multiplyExact(lineBet, win.times()));
            }
        }
        return new Spin(stops.clone(), bet, List.copyOf(wins), payout);
    }

    public static Spin random(Random random, long bet, Paytable paytable) {
        int[] stops = new int[Reels.REELS];
        for (int i = 0; i < stops.length; i++) {
            stops[i] = random.nextInt(Reels.STOPS);
        }
        return of(stops, bet, paytable);
    }

    /** 5 本のラインに等分できる賭け金か。 */
    public static boolean validBet(long bet) {
        return bet > 0 && bet % Line.COUNT == 0;
    }

    /**
     * リーチになるライン（左の 2 本が 7 か BAR でそろう）。右のリールを長く回して見せる。
     */
    public static List<Line> reachLines(int[] stops) {
        List<Line> lines = new ArrayList<>();
        for (Line line : Line.values()) {
            Symbol[] s = line.symbols(stops);
            if (s[0] == s[1] && (s[0] == Symbol.SEVEN || s[0] == Symbol.BAR)) {
                lines.add(line);
            }
        }
        return lines;
    }

    public List<Line> reachLines() {
        return reachLines(stops);
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
