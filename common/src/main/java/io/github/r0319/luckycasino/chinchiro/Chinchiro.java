package io.github.r0319.luckycasino.chinchiro;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * チンチロ（チンチロリン）。親と子がそれぞれサイコロ 3 個をどんぶりに振り、出た役の強さで勝負する。
 * 1 回の番では役が出るまで 3 回まで振れ、3 回とも役なしなら「目なし」。
 *
 * <p>役（強い順）: ピンゾロ（1・1・1、5 倍）、ゾロ目（2〜6 の 3 個そろい、3 倍。数が大きいほど強い）、
 * シゴロ（4・5・6、2 倍）、目（2 個そろいの残りの 1 個の数。6 が最強、1 倍）、目なし、ヒフミ（1・2・3、2 倍払い）。
 *
 * <p>親が先に振る。親がピンゾロ・ゾロ目・シゴロ・6 の目なら子の負け（その倍率を払う）、ヒフミなら子に 2 倍、
 * 1 の目か目なしなら子に 1 倍を払い、子は振らない。親が 2〜5 の目のときだけ子が振り、強いほうが勝つ
 * （同じなら引き分け）。勝った側の役の倍率で払い、負けた側がヒフミなら少なくとも 2 倍。
 * 子が負けると賭け金の最大 5 倍を払うので、賭けるときは賭け金の 4 倍を手元に残しておく（RESERVE）。
 */
public final class Chinchiro {

    /** 役の種類。並びは弱い順。 */
    public enum Kind {
        HIFUMI("ヒフミ", 2), MENASHI("目なし", 1), ME("目", 1), SHIGORO("シゴロ", 2), ZORO("ゾロ目", 3), PINZORO("ピンゾロ", 5);

        private final String label;
        private final int multiplier;

        Kind(String label, int multiplier) {
            this.label = label;
            this.multiplier = multiplier;
        }

        public String label() {
            return label;
        }

        /** この役で勝ったとき（ヒフミは負けたとき）の倍率。 */
        public int multiplier() {
            return multiplier;
        }
    }

    /** 子が負けたときの最大の倍率（親のピンゾロ）から、賭け金のほかに手元に残しておく倍数。 */
    public static final int RESERVE = 4;
    /** 1 回の番で振れる回数。 */
    public static final int TRIES = 3;

    /** 役。value は目の数（目）か、そろった数（ゾロ目）。ほかは 0。 */
    public record Hand(Kind kind, int value) implements Comparable<Hand> {

        @Override
        public int compareTo(Hand o) {
            int c = kind.compareTo(o.kind);
            return c != 0 ? c : Integer.compare(value, o.value);
        }

        public String label() {
            return switch (kind) {
                case ME -> value + " の目";
                case ZORO -> value + " のゾロ目";
                default -> kind.label();
            };
        }
    }

    /** 1 回振った出目と役。 */
    public record Roll(int a, int b, int c, Hand hand) {

        public int die(int i) {
            return switch (i) {
                case 0 -> a;
                case 1 -> b;
                case 2 -> c;
                default -> throw new IndexOutOfBoundsException(i);
            };
        }
    }

    private Chinchiro() {
    }

    public static Hand hand(int a, int b, int c) {
        for (int d : new int[]{a, b, c}) {
            if (d < 1 || d > 6) {
                throw new IllegalArgumentException("die " + d);
            }
        }
        int[] s = {a, b, c};
        Arrays.sort(s);
        if (s[0] == s[2]) {
            return s[0] == 1 ? new Hand(Kind.PINZORO, 0) : new Hand(Kind.ZORO, s[0]);
        }
        if (s[0] == 4 && s[1] == 5 && s[2] == 6) {
            return new Hand(Kind.SHIGORO, 0);
        }
        if (s[0] == 1 && s[1] == 2 && s[2] == 3) {
            return new Hand(Kind.HIFUMI, 0);
        }
        if (s[0] == s[1]) {
            return new Hand(Kind.ME, s[2]);
        }
        if (s[1] == s[2]) {
            return new Hand(Kind.ME, s[0]);
        }
        return new Hand(Kind.MENASHI, 0);
    }

    public static Roll roll(Random random) {
        int a = 1 + random.nextInt(6);
        int b = 1 + random.nextInt(6);
        int c = 1 + random.nextInt(6);
        return new Roll(a, b, c, hand(a, b, c));
    }

    /** 1 回の番。役が出るまで 3 回まで振る（最後の役が、その番の役）。 */
    public static List<Roll> turn(Random random) {
        List<Roll> rolls = new ArrayList<>();
        do {
            rolls.add(roll(random));
        } while (rolls.getLast().hand().kind() == Kind.MENASHI && rolls.size() < TRIES);
        return rolls;
    }

    /**
     * 親の役だけで決まるときの、子から見た倍率（勝ちは正、負けは負）。子が振る必要があるとき（親が 2〜5 の目）は null。
     */
    public static Integer settledByDealer(Hand dealer) {
        return switch (dealer.kind()) {
            case PINZORO, ZORO, SHIGORO -> -dealer.kind().multiplier();
            case HIFUMI -> 2;
            case MENASHI -> 1;
            case ME -> switch (dealer.value()) {
                case 6 -> Integer.valueOf(-1);
                case 1 -> Integer.valueOf(1);
                default -> null;
            };
        };
    }

    /** 親が 2〜5 の目のとき、子の役 player との勝負の、子から見た倍率。 */
    public static int versus(Hand dealer, Hand player) {
        int c = player.compareTo(dealer);
        if (c > 0) {
            return player.kind().multiplier();
        }
        if (c == 0) {
            return 0;
        }
        return -(player.kind() == Kind.HIFUMI ? 2 : dealer.kind().multiplier());
    }

    /** 賭け金 amount と倍率 m のとき戻るチップ（賭け金を含む。負けが賭け金を超えると負）。 */
    public static long back(long amount, int m) {
        return amount * (1 + m);
    }
}
