package io.github.r0319.luckycasino.paper;

import io.github.r0319.luckycasino.roulette.BetType;
import io.github.r0319.luckycasino.roulette.RouletteBet;

/**
 * ルーレットのテーブル上の位置。ベットマットとホイールの位置はフェルトの絵（tools/gen_table.py の
 * MAT_U など、1 ブロック = 48 px）と合わせること。
 */
final class RouletteLayout implements TableShape {

    static final int SEATS = 6;
    private static final double PX = 48;
    private static final int MAT_U = 58;
    private static final int MAT_V = 30;
    private static final int CELL = 9;
    private static final int WHEEL_U = 28;
    private static final int WHEEL_V = 48;

    /** ホイールの円盤の半径（ブロック）と、ボールが回る外周・止まるポケットの半径。 */
    static final float WHEEL_SCALE = 1.0f;
    static final double BALL_TRACK = 0.44;
    static final double BALL_POCKET = 0.32;

    @Override
    public int seats() {
        return SEATS;
    }

    @Override
    public double[] seat(int index) {
        return new double[]{1.75 - 0.7 * index, 0, 1.55};
    }

    @Override
    public double[] dealer() {
        return new double[]{-1.2, 0, -1.55};
    }

    @Override
    public String model() {
        return "table/roulette";
    }

    double[] wheel() {
        return new double[]{WHEEL_U / PX - 2, TOP + 0.002, WHEEL_V / PX - 1};
    }

    /** ホイールの中心から angle 度（上から見て時計回り、奥 = 0）、半径 radius の位置。 */
    double[] aroundWheel(double angle, double radius, double height) {
        double[] c = wheel();
        double rad = Math.toRadians(angle);
        return new double[]{c[0] + radius * Math.sin(rad), TOP + height, c[2] - radius * Math.cos(rad)};
    }

    double[] resultLabel() {
        double[] c = wheel();
        return new double[]{c[0], TOP + 0.9, c[2]};
    }

    double[] history() {
        return new double[]{-0.4, TOP + 0.75, -0.75};
    }

    double[] croupierChips() {
        return new double[]{-0.4, TOP + 0.001, -0.75};
    }

    private static double[] px(double u, double v) {
        return new double[]{u / PX - 2, TOP + 0.001, v / PX - 1};
    }

    /** 数字のマスの中心（px）。 */
    private static double[] cellCenter(int n) {
        if (n == 0) {
            return new double[]{MAT_U + CELL / 2.0, MAT_V + CELL * 1.5};
        }
        int column = (n - 1) / 3;
        int row = 2 - (n - 1) % 3;
        return new double[]{MAT_U + CELL + CELL * column + CELL / 2.0, MAT_V + CELL * row + CELL / 2.0};
    }

    /** 賭けのチップを置く位置（マスの中心、境目、角など）。 */
    double[] spot(RouletteBet bet) {
        return switch (bet.type()) {
            case STRAIGHT -> {
                double[] c = cellCenter(bet.anchor());
                yield px(c[0], c[1]);
            }
            case SPLIT -> {
                int lo = bet.anchor() / 100;
                int hi = bet.anchor() % 100;
                double[] a = cellCenter(lo);
                double[] b = cellCenter(hi);
                if (lo == 0) {
                    yield px(MAT_U + CELL, b[1]);
                }
                yield px((a[0] + b[0]) / 2, (a[1] + b[1]) / 2);
            }
            case STREET -> {
                double[] c = cellCenter((bet.anchor() - 1) * 3 + 1);
                yield px(c[0], MAT_V + CELL * 3);
            }
            case CORNER -> {
                if (bet.anchor() == 0) {
                    yield px(MAT_U + CELL, MAT_V + CELL * 3);
                }
                double[] a = cellCenter(bet.anchor());
                double[] d = cellCenter(bet.anchor() + 4);
                yield px((a[0] + d[0]) / 2, (a[1] + d[1]) / 2);
            }
            case LINE -> {
                double[] c = cellCenter((bet.anchor() - 1) * 3 + 1);
                yield px(c[0] + CELL / 2.0, MAT_V + CELL * 3);
            }
            case DOZEN -> px(MAT_U + CELL + 36 * (bet.anchor() - 1) + 18, MAT_V + CELL * 3 + CELL / 2.0);
            case COLUMN -> px(MAT_U + CELL * 13 + CELL / 2.0, MAT_V + CELL * (3 - bet.anchor()) + CELL / 2.0);
            default -> {
                int index = switch (bet.type()) {
                    case LOW -> 0;
                    case EVEN -> 1;
                    case RED -> 2;
                    case BLACK -> 3;
                    case ODD -> 4;
                    default -> 5;
                };
                yield px(MAT_U + CELL + 18 * index + 9, MAT_V + CELL * 4 + CELL / 2.0);
            }
        };
    }

    /** 外側の賭けの種類の並び（メニューと同じ）。 */
    static final BetType[] OUTSIDE = {BetType.LOW, BetType.EVEN, BetType.RED, BetType.BLACK, BetType.ODD, BetType.HIGH};
}
