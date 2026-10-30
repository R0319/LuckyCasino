package io.github.r0319.luckycasino.roulette;

import java.util.Set;
import java.util.TreeSet;

/**
 * 1 つの賭け。numbers は当たりになる数字。ベットマットは 1〜36 を 3 段 × 12 列に並べ、
 * 列 r（0〜11）に 3r+1, 3r+2, 3r+3 が入る（3r+1 が手前の段）。
 *
 * @param anchor 賭けた場所を表す番号（表示位置に使う。外側の賭けは 1〜3 などの区分番号）
 */
public record RouletteBet(BetType type, int anchor, Set<Integer> numbers, long amount) {

    public RouletteBet {
        numbers = Set.copyOf(numbers);
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    public boolean wins(int result) {
        return numbers.contains(result);
    }

    /** 戻ってくるチップ（賭け金を含む）。外れは 0。 */
    public long payout(int result) {
        return wins(result) ? amount * (type.payout() + 1) : 0;
    }

    public RouletteBet withAmount(long newAmount) {
        return new RouletteBet(type, anchor, numbers, newAmount);
    }

    /** 同じ場所への賭けか（額は見ない）。 */
    public boolean samePlace(RouletteBet other) {
        return type == other.type && anchor == other.anchor;
    }

    // ---- 作り方。置けない場所なら IllegalArgumentException ----

    public static RouletteBet straight(int number, long amount) {
        check(number >= 0 && number <= 36, "number");
        return new RouletteBet(BetType.STRAIGHT, number, Set.of(number), amount);
    }

    /** 隣り合う 2 つ（anchor と右隣 = +3、または anchor と上隣 = +1）。0 は 1・2・3 と組める。 */
    public static RouletteBet split(int a, int b, long amount) {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        boolean ok;
        if (lo == 0) {
            ok = hi >= 1 && hi <= 3;
        } else {
            ok = hi <= 36 && (hi - lo == 3 || (hi - lo == 1 && (lo - 1) / 3 == (hi - 1) / 3));
        }
        check(ok, "split " + a + "-" + b);
        return new RouletteBet(BetType.SPLIT, lo * 100 + hi, Set.of(lo, hi), amount);
    }

    /** 列 row（1〜12）の 3 つ。 */
    public static RouletteBet street(int row, long amount) {
        check(row >= 1 && row <= 12, "street row");
        int first = (row - 1) * 3 + 1;
        return new RouletteBet(BetType.STREET, row, Set.of(first, first + 1, first + 2), amount);
    }

    /** n, n+1, n+3, n+4 の 4 つ（n は上の段ではない、最後の列ではない）。0 を渡すと 0・1・2・3。 */
    public static RouletteBet corner(int n, long amount) {
        if (n == 0) {
            return new RouletteBet(BetType.CORNER, 0, Set.of(0, 1, 2, 3), amount);
        }
        check(n >= 1 && n <= 32 && (n - 1) % 3 != 2, "corner " + n);
        return new RouletteBet(BetType.CORNER, n, Set.of(n, n + 1, n + 3, n + 4), amount);
    }

    /** 列 row と row+1（row は 1〜11）の 6 つ。 */
    public static RouletteBet line(int row, long amount) {
        check(row >= 1 && row <= 11, "line row");
        int first = (row - 1) * 3 + 1;
        Set<Integer> numbers = new TreeSet<>();
        for (int i = 0; i < 6; i++) {
            numbers.add(first + i);
        }
        return new RouletteBet(BetType.LINE, row, numbers, amount);
    }

    /** 1: 1〜12、2: 13〜24、3: 25〜36。 */
    public static RouletteBet dozen(int dozen, long amount) {
        check(dozen >= 1 && dozen <= 3, "dozen");
        Set<Integer> numbers = new TreeSet<>();
        for (int n = (dozen - 1) * 12 + 1; n <= dozen * 12; n++) {
            numbers.add(n);
        }
        return new RouletteBet(BetType.DOZEN, dozen, numbers, amount);
    }

    /** 1: 1, 4, 7, …（手前の段）、2: 2, 5, 8, …、3: 3, 6, 9, …（奥の段）。 */
    public static RouletteBet column(int column, long amount) {
        check(column >= 1 && column <= 3, "column");
        Set<Integer> numbers = new TreeSet<>();
        for (int n = column; n <= 36; n += 3) {
            numbers.add(n);
        }
        return new RouletteBet(BetType.COLUMN, column, numbers, amount);
    }

    /** 赤・黒・偶数・奇数・1〜18・19〜36。 */
    public static RouletteBet outside(BetType type, long amount) {
        Set<Integer> numbers = new TreeSet<>();
        for (int n = 1; n <= 36; n++) {
            boolean in = switch (type) {
                case RED -> Wheel.red(n);
                case BLACK -> Wheel.black(n);
                case EVEN -> n % 2 == 0;
                case ODD -> n % 2 == 1;
                case LOW -> n <= 18;
                case HIGH -> n >= 19;
                default -> throw new IllegalArgumentException("not an outside bet: " + type);
            };
            if (in) {
                numbers.add(n);
            }
        }
        return new RouletteBet(type, 0, numbers, amount);
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new IllegalArgumentException("invalid bet: " + what);
        }
    }
}
