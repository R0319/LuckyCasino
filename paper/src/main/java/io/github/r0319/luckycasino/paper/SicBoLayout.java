package io.github.r0319.luckycasino.paper;

/**
 * 大小のテーブル上の位置。賭けのマスはフェルトの絵（tools/gen_games.py の sicbo_felt）と数値を合わせること。
 * 手前から 小・奇数・偶数・大、1 つの目、合計、ダブルとゾロ目の段。サイコロの皿は奥の右。
 */
final class SicBoLayout implements TableShape {

    static final int SEATS = 5;
    static final double FRONT_Z = 0.73;
    static final double SINGLE_Z = 0.38;
    static final double TOTAL_Z = 0.06;
    static final double COMBO_Z = -0.26;
    /** 皿・ふた・サイコロの表示倍率。 */
    static final float TRAY_SCALE = 0.56f;
    static final float DIE_SCALE = 0.13f;

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
        return new double[]{0, 0, -1.55};
    }

    @Override
    public String model() {
        return "table/sicbo";
    }

    /** 皿とふたのモデルを置く位置（モデルの中心。皿の底がフェルトに乗る）。 */
    double[] tray() {
        return new double[]{0.75, TOP + TRAY_SCALE / 2, -0.68};
    }

    /** 皿に乗ったサイコロの中心の基準（ここから translation でずらす）。 */
    double[] dice() {
        return new double[]{0.75, TOP + TRAY_SCALE / 16 + DIE_SCALE / 2, -0.68};
    }

    double[] resultLabel() {
        return new double[]{0.05, TOP + 0.45, -0.68};
    }

    /** 手前の段の i 番目（小・奇数・偶数・大）。 */
    double[] front(int i) {
        return new double[]{-1.44 + 0.96 * i, TOP + 0.001, FRONT_Z};
    }

    /** 目 face（1〜6）に賭けるマス。 */
    double[] single(int face) {
        return new double[]{-1.6 + 0.64 * (face - 1), TOP + 0.001, SINGLE_Z};
    }

    /** 合計 total（4〜17）。 */
    double[] total(int total) {
        return new double[]{-1.783 + 0.2743 * (total - 4), TOP + 0.001, TOTAL_Z};
    }

    /** 0〜5 はダブル 1〜6、6 はゾロ目どれでも、7〜12 はゾロ目 1〜6。 */
    double[] combo(int i) {
        return new double[]{-1.74 + 0.29 * i, TOP + 0.001, COMBO_Z};
    }

    double[] croupierChips() {
        return new double[]{-1.5, TOP + 0.001, -0.72};
    }
}
