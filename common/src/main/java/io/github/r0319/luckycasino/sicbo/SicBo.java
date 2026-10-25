package io.github.r0319.luckycasino.sicbo;

import java.util.Arrays;
import java.util.Random;

/**
 * 大小（シックボー）。サイコロ 3 個の出目に賭ける。賭けの名前は次のとおり（n は出目 1〜6、t は合計 4〜17）。
 * small / big / odd / even、any_triple、triple_n（その目のゾロ目）、double_n（その目が 2 個以上）、
 * single_n（その目が 1 個以上。出た個数ぶん配当）、total_t（合計）。
 */
public final class SicBo {

    private final int[] dice;

    public SicBo(int a, int b, int c) {
        for (int d : new int[]{a, b, c}) {
            if (d < 1 || d > 6) {
                throw new IllegalArgumentException("die " + d);
            }
        }
        this.dice = new int[]{a, b, c};
    }

    public static SicBo roll(Random random) {
        return new SicBo(random.nextInt(6) + 1, random.nextInt(6) + 1, random.nextInt(6) + 1);
    }

    public int die(int i) {
        return dice[i];
    }

    public int total() {
        return dice[0] + dice[1] + dice[2];
    }

    public boolean triple() {
        return dice[0] == dice[1] && dice[1] == dice[2];
    }

    public int count(int face) {
        return (int) Arrays.stream(dice).filter(d -> d == face).count();
    }

    /** 合計 t の配当（t:1 の t）。 */
    public static int totalPays(int total) {
        return switch (total) {
            case 4, 17 -> 60;
            case 5, 16 -> 30;
            case 6, 15 -> 17;
            case 7, 14 -> 12;
            case 8, 13 -> 8;
            case 9, 10, 11, 12 -> 6;
            default -> throw new IllegalArgumentException("total " + total);
        };
    }

    public static boolean valid(String bet) {
        try {
            payoutOf(bet, 1, new SicBo(1, 2, 3));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** bet に amount 賭けていたときに戻るチップ（賭け金を含む。負けは 0）。 */
    public long payout(String bet, long amount) {
        return payoutOf(bet, amount, this);
    }

    private static long payoutOf(String bet, long amount, SicBo r) {
        int total = r.total();
        switch (bet) {
            case "small":
                return !r.triple() && total <= 10 ? amount * 2 : 0;
            case "big":
                return !r.triple() && total >= 11 ? amount * 2 : 0;
            case "odd":
                return !r.triple() && total % 2 == 1 ? amount * 2 : 0;
            case "even":
                return !r.triple() && total % 2 == 0 ? amount * 2 : 0;
            case "any_triple":
                return r.triple() ? amount * 31 : 0;
            default:
                break;
        }
        int sep = bet.lastIndexOf('_');
        if (sep < 0) {
            throw new IllegalArgumentException("bet " + bet);
        }
        String kind = bet.substring(0, sep);
        int n;
        try {
            n = Integer.parseInt(bet.substring(sep + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bet " + bet);
        }
        if (kind.equals("total")) {
            int pays = totalPays(n);
            return total == n ? amount * (pays + 1) : 0;
        }
        if (n < 1 || n > 6) {
            throw new IllegalArgumentException("bet " + bet);
        }
        int count = r.count(n);
        return switch (kind) {
            case "triple" -> count == 3 ? amount * 181 : 0;
            case "double" -> count >= 2 ? amount * 11 : 0;
            case "single" -> count > 0 ? amount * (1 + count) : 0;
            default -> throw new IllegalArgumentException("bet " + bet);
        };
    }

    @Override
    public String toString() {
        return dice[0] + "-" + dice[1] + "-" + dice[2];
    }
}
