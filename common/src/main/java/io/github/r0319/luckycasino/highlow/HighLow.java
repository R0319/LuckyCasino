package io.github.r0319.luckycasino.highlow;

import io.github.r0319.luckycasino.card.Card;
import io.github.r0319.luckycasino.card.Rank;
import io.github.r0319.luckycasino.card.Suit;

import java.util.Random;

/**
 * ハイ＆ロー。表向きの 1 枚（基準）より、次の 1 枚が上か下か同じかを当てる。A が 1 で K が 13、マークは関係ない。
 * 毎回 52 枚から引き直す（何枚出ても確率は変わらない）。配当は当たる確率から決め、還元率は 95% 以下。
 */
public final class HighLow {

    public enum Bet {
        HIGH, LOW, SAME
    }

    private HighLow() {
    }

    public static int value(Card card) {
        return card.rank().ordinal() + 1;
    }

    public static Card draw(Random random) {
        return new Card(Suit.values()[random.nextInt(Suit.values().length)], Rank.values()[random.nextInt(Rank.values().length)]);
    }

    /** 基準が base（1〜13）のとき、bet が当たる数の種類（13 種類のうち）。 */
    public static int wins(Bet bet, int base) {
        return switch (bet) {
            case HIGH -> 13 - base;
            case LOW -> base - 1;
            case SAME -> 1;
        };
    }

    /** 当たったときに戻る倍率（百分率、賭け金を含む）。当たりようがなければ 0。 */
    public static int percent(Bet bet, int base) {
        int wins = wins(bet, base);
        // 13 / wins 倍の 95%（1 % 未満は切り捨て）
        return wins == 0 ? 0 : 1235 / wins;
    }

    public static boolean won(Bet bet, int base, int next) {
        return switch (bet) {
            case HIGH -> next > base;
            case LOW -> next < base;
            case SAME -> next == base;
        };
    }

    /** 戻るチップ（賭け金を含む。負けは 0）。端数は切り捨て。 */
    public static long payout(Bet bet, int base, int next, long amount) {
        return won(bet, base, next) ? amount * percent(bet, base) / 100 : 0;
    }

    /** 倍率の表示（"2.05 倍"）。 */
    public static String percentText(int percent) {
        return percent / 100 + "." + String.format("%02d", percent % 100) + " 倍";
    }
}
