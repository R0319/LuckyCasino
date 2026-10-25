package io.github.r0319.luckycasino.slot;

import java.util.Random;

/**
 * 1 回の回転の結果。止まる位置を先に決め、リールの動きはそこに止まるように逆算する。
 *
 * @param stops  リールごとの、中央のラインに止まるコマ
 * @param bet    賭け金
 * @param win    役
 * @param payout 戻ってくるチップ（賭け金を含む。外れは 0）
 */
public record Spin(int[] stops, long bet, Paytable.Win win, long payout) {

    public static Spin of(int[] stops, long bet, Paytable paytable) {
        if (stops.length != Reels.REELS || bet <= 0) {
            throw new IllegalArgumentException("invalid spin");
        }
        Paytable.Win win = paytable.evaluate(line(stops));
        return new Spin(stops.clone(), bet, win, Math.multiplyExact(bet, win.times()));
    }

    public static Spin random(Random random, long bet, Paytable paytable) {
        int[] stops = new int[Reels.REELS];
        for (int i = 0; i < stops.length; i++) {
            stops[i] = random.nextInt(Reels.STOPS);
        }
        return of(stops, bet, paytable);
    }

    /** 中央のラインの絵柄。 */
    public static Symbol[] line(int[] stops) {
        Symbol[] line = new Symbol[Reels.REELS];
        for (int i = 0; i < line.length; i++) {
            line[i] = Reels.at(i, stops[i]);
        }
        return line;
    }

    public Symbol[] line() {
        return line(stops);
    }
}
