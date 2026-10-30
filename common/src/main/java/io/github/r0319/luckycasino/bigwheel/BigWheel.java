package io.github.r0319.luckycasino.bigwheel;

import java.util.List;
import java.util.Random;

/**
 * ビッグホイール（ビッグシックス）。54 マスの縦のホイールを回し、上の針が指したマスの絵柄に賭けていたら当たり。
 * マスの並びはリソースパック（tools/gen_games.py の BIGWHEEL_ORDER）と同じにすること。
 */
public final class BigWheel {

    /** 絵柄と配当（pays:1。当たると賭け金の pays + 1 倍が戻る）。 */
    public enum Segment {
        ONE(1), TWO(2), FIVE(5), TEN(10), TWENTY(20), JOKER(40), LOGO(40);

        private final int pays;

        Segment(int pays) {
            this.pays = pays;
        }

        public int pays() {
            return pays;
        }
    }

    public static final int SEGMENTS = 54;

    private static final Segment J = Segment.JOKER;
    private static final Segment L = Segment.LOGO;
    private static final Segment A = Segment.ONE;
    private static final Segment B = Segment.TWO;
    private static final Segment E = Segment.FIVE;
    private static final Segment T = Segment.TEN;
    private static final Segment W = Segment.TWENTY;

    /** 針の位置（上）から時計回り（正面から見て）の並び。 */
    private static final List<Segment> ORDER = List.of(
            J, A, B, A, E, B, A, T, A, B, A, E, A, B, W, A, B, A, A, E, T, B, A, B, A, A, B,
            L, E, A, A, B, A, B, T, E, A, A, B, A, W, B, E, A, A, B, A, T, A, B, E, A, B, A);

    private BigWheel() {
    }

    public static Segment at(int index) {
        return ORDER.get(Math.floorMod(index, SEGMENTS));
    }

    /** 止まるマスを決める（どのマスも同じ確率）。 */
    public static int spin(Random random) {
        return random.nextInt(SEGMENTS);
    }

    /** 絵柄 bet に amount 賭けていて、マス index に止まったときに戻るチップ（賭け金を含む。負けは 0）。 */
    public static long payout(Segment bet, long amount, int index) {
        return at(index) == bet ? amount * (bet.pays() + 1) : 0;
    }

    /** 絵柄のマスの数。 */
    public static int count(Segment segment) {
        return (int) ORDER.stream().filter(s -> s == segment).count();
    }
}
