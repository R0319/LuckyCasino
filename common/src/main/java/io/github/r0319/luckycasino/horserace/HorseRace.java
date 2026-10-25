package io.github.r0319.luckycasino.horserace;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 競馬（単勝）。毎回、馬ごとの強さ（勝つ確率）を決め直し、それに合わせた配当を出してから賭けを受ける。
 * 着順は強さの重みで 1 着から順に引いて決める。配当は勝つ確率の逆数の 90%（1 % 未満は切り捨て）。
 */
public final class HorseRace {

    public static final int HORSES = 6;

    private final double[] chance;
    private final int[] percent;

    private HorseRace(double[] chance) {
        this.chance = chance;
        this.percent = new int[chance.length];
        for (int i = 0; i < chance.length; i++) {
            percent[i] = (int) Math.floor(90 / chance[i]);
        }
    }

    /** 強さを 1〜6 の重みでばらつかせた出走表。 */
    public static HorseRace card(Random random) {
        double[] weight = new double[HORSES];
        double sum = 0;
        for (int i = 0; i < HORSES; i++) {
            weight[i] = 1 + random.nextDouble() * 5;
            sum += weight[i];
        }
        for (int i = 0; i < HORSES; i++) {
            weight[i] /= sum;
        }
        return new HorseRace(weight);
    }

    /** 確率を直接決めた出走表（テスト用）。 */
    public static HorseRace of(double... chance) {
        return new HorseRace(chance.clone());
    }

    /** 馬 horse（0 から）が勝つ確率。 */
    public double chance(int horse) {
        return chance[horse];
    }

    /** 馬 horse が勝ったときに戻る倍率（百分率、賭け金を含む）。 */
    public int percent(int horse) {
        return percent[horse];
    }

    public int horses() {
        return chance.length;
    }

    /** 着順（馬の番号、0 から）を決める。強いほど前に来やすい。 */
    public int[] run(Random random) {
        List<Integer> left = new ArrayList<>();
        for (int i = 0; i < chance.length; i++) {
            left.add(i);
        }
        int[] order = new int[chance.length];
        for (int place = 0; place < order.length; place++) {
            double total = 0;
            for (int h : left) {
                total += chance[h];
            }
            double pick = random.nextDouble() * total;
            int chosen = left.get(left.size() - 1);
            for (int h : left) {
                pick -= chance[h];
                if (pick < 0) {
                    chosen = h;
                    break;
                }
            }
            order[place] = chosen;
            left.remove(Integer.valueOf(chosen));
        }
        return order;
    }

    /** 馬 horse の単勝に amount 賭けていて、winner が勝ったときに戻るチップ（賭け金を含む。負けは 0）。 */
    public long payout(int horse, long amount, int winner) {
        return horse == winner ? amount * percent[horse] / 100 : 0;
    }

    /** 倍率の表示（"3.40 倍"）。 */
    public static String percentText(int percent) {
        return percent / 100 + "." + String.format("%02d", percent % 100) + " 倍";
    }
}
