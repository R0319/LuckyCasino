package io.github.r0319.luckycasino.paper;

/**
 * ビッグホイールのステージ。奥に赤い幕と直径 3 ブロックのホイール、手前にバースツールを扇形に並べ、
 * 各席の前にベット台を置く。ステージ・ベット台・電球はリソースパックのモデル（tools/gen_games.py の bigwheel_stage_model など）で、
 * 位置の数値はそちらと合わせること。
 */
final class BigWheelLayout implements TableShape {

    static final int SEATS = 5;
    /** ホイールの直径（ブロック）。モデルは 16 単位 = 1 ブロック。 */
    static final float WHEEL_SCALE = 3f;
    /** ステージ・電球のモデルの表示倍率（モデル 8 単位 = 1 ブロック）。 */
    static final float STAGE_SCALE = 2f;
    private static final double[] WHEEL = {0, 2.2, -1.55};
    private static final double SEAT_R = 3.0;
    private static final double STAND_R = 2.3;
    private static final double SPREAD = 1.2;
    /** 当たり判定のバリアは幕と柱の列だけ（4 x 1 ブロック）。 */
    private static final double[][] CELLS = {{-1.5, 0.5, -1.5}, {-0.5, 0.5, -1.5}, {0.5, 0.5, -1.5}, {1.5, 0.5, -1.5}};

    /** 席 i（0 が右端）の、ホイールの中心から見た角度（ラジアン、手前が 0）。 */
    private static double angle(int i) {
        return (0.5 - i / 4.0) * SPREAD;
    }

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
        double a = angle(index);
        return new double[]{SEAT_R * Math.sin(a), 0, WHEEL[2] + SEAT_R * Math.cos(a)};
    }

    @Override
    public double[] dealer() {
        return new double[]{1.55, 0, -1.0};
    }

    @Override
    public double[] status() {
        return new double[]{-2.5, 2.0, -0.9};
    }

    @Override
    public String model() {
        return "bigwheel/stage";
    }

    @Override
    public double[] modelAt() {
        return new double[]{0, 2.1, -1.1};
    }

    @Override
    public float modelScale() {
        return STAGE_SCALE;
    }

    @Override
    public double[][] cells() {
        return CELLS;
    }

    @Override
    public int headroom() {
        return 4;
    }

    @Override
    public String spaceText() {
        return "幕の列（4 x 1 ブロック）に高さ 5 ブロックの空きと、手前に椅子を並べる場所が必要です";
    }

    /** ホイールの中心。 */
    double[] wheel() {
        return WHEEL.clone();
    }

    /** 電球のモデルを置く位置（ステージと同じ）。 */
    double[] bulbs() {
        return modelAt();
    }

    /** 針の指すマスの絵柄を出す位置（針の上）。 */
    double[] pointerLabel() {
        return new double[]{WHEEL[0], WHEEL[1] + WHEEL_SCALE / 2 + 0.45, WHEEL[2] + 0.1};
    }

    /** 席 seat のベット台の天板の中心。 */
    double[] stand(int seat) {
        double a = angle(seat);
        return new double[]{STAND_R * Math.sin(a), 1.001, WHEEL[2] + STAND_R * Math.cos(a)};
    }

    /** ベット台の上の、賭け場所 k（0〜6）のチップの位置。4 + 3 個に並べる。 */
    double[] chip(int seat, int k) {
        double[] s = stand(seat);
        double dx = k < 4 ? -0.165 + 0.11 * k : -0.11 + 0.11 * (k - 4);
        double dz = k < 4 ? -0.07 : 0.08;
        return new double[]{s[0] + dx, s[1] + seat * 0.0005, s[2] + dz};
    }

    double[] croupierChips() {
        return new double[]{1.1, 0.201, -0.75};
    }
}
