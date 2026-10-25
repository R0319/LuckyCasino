package io.github.r0319.luckycasino.paper;

/**
 * ビッグホイールのテーブル上の位置。ホイールはテーブルの奥（ディーラーの右）に縦に立てる。
 * 賭けのマスはフェルトの絵（tools/gen_games.py の bigwheel_felt）と数値を合わせること。
 */
final class BigWheelLayout implements TableShape {

    static final int SEATS = 5;
    /** ホイールの直径（ブロック）。モデルは 16 単位 = 1 ブロック。 */
    static final float WHEEL_SCALE = 1.7f;
    static final double BOX_Z = 0.3;

    @Override
    public double seatRise() {
        return RAISED_SEAT;
    }

    @Override
    public int seats() {
        return SEATS;
    }

    @Override
    public double[] seat(int index) {
        return new double[]{1.6 - 0.8 * index, 0, 1.55};
    }

    @Override
    public double[] dealer() {
        return new double[]{-0.9, 0, -1.55};
    }

    @Override
    public String model() {
        return "table/bigwheel";
    }

    /** ホイールの中心。 */
    double[] wheel() {
        return new double[]{0.75, 1.95, -1.5};
    }

    /** 台のモデルを置く位置（モデルの中心。床から柱、上の針まで入る高さ）。 */
    double[] stand() {
        return new double[]{0.75, 1.45, -1.5};
    }

    /** 針の指すマスの絵柄を出す位置（針の上）。 */
    double[] pointerLabel() {
        double[] c = wheel();
        return new double[]{c[0], c[1] + WHEEL_SCALE / 2 + 0.2, c[2]};
    }

    /** 賭けのマス i（左から）の中心。 */
    double[] box(int i) {
        return new double[]{-1.71 + 0.57 * i, TOP + 0.001, BOX_Z};
    }

    double[] croupierChips() {
        return new double[]{-0.9, TOP + 0.001, -0.6};
    }
}
