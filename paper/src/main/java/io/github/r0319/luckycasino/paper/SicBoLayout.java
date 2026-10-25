package io.github.r0319.luckycasino.paper;

/**
 * 大小の座敷。4 x 4 の畳の真ん中にどんぶりを置き、まわりに座布団を輪に並べる（客 5 枚と、奥に親の 1 枚）。
 * 客も親も床に座る。チップは各座布団の前の畳に、賭けの種類ごとに 4 x 2 に並べる。
 * 畳・座布団・どんぶりはリソースパックのモデル（tools/gen_games.py の sicbo_zashiki_model など）で、
 * 位置の数値はそちらと合わせること。
 */
final class SicBoLayout implements TableShape {

    static final int SEATS = 5;
    /** 座敷のモデルの表示倍率（モデル 8 単位 = 1 ブロック）。 */
    static final float ZASHIKI_SCALE = 2f;
    /** サイコロの表示倍率。 */
    static final float DIE_SCALE = 0.12f;
    /** 畳の表面の高さ。 */
    static final double MAT = 0.1;
    private static final double RING = 1.55;
    /** チップを置く輪の半径。 */
    private static final double CHIP_RING = 0.72;
    /** 当たり判定のバリアは真ん中の 2 x 2 ブロック（どんぶりのまわり）だけ。 */
    private static final double[][] CELLS = {{-0.5, 0.5, -0.5}, {0.5, 0.5, -0.5}, {-0.5, 0.5, 0.5}, {0.5, 0.5, 0.5}};

    /** 座布団 i の角度（ラジアン、手前が 0）。0〜4 が客（右から）。 */
    static double angle(int seat) {
        return Math.toRadians(120 - 60 * seat);
    }

    /** 座布団の上に座る高さ（ふつうの椅子の座面 0.38 から下げる）。 */
    @Override
    public double seatRise() {
        return MAT - 0.38;
    }

    @Override
    public int seats() {
        return SEATS;
    }

    @Override
    public double[] seat(int index) {
        double a = angle(index);
        return new double[]{RING * Math.sin(a), 0, RING * Math.cos(a)};
    }

    @Override
    public double[] dealer() {
        return new double[]{0, 0, -RING};
    }

    @Override
    public boolean dealerSeated() {
        return true;
    }

    @Override
    public double[] status() {
        return new double[]{0, 1.95, -1.8};
    }

    @Override
    public String model() {
        return "sicbo/zashiki";
    }

    @Override
    public double[] modelAt() {
        return new double[]{0, 0.5, 0};
    }

    @Override
    public float modelScale() {
        return ZASHIKI_SCALE;
    }

    @Override
    public double[][] cells() {
        return CELLS;
    }

    @Override
    public boolean needsHeadroom() {
        return false;
    }

    @Override
    public String spaceText() {
        return "4 x 4 ブロックの畳を敷く平らな場所が必要です（真ん中の 2 x 2 ブロックは空き）";
    }

    /** どんぶりのモデルを置く位置（底の中心）。 */
    double[] bowl() {
        return new double[]{0, MAT, 0};
    }

    /** どんぶりの底に乗ったサイコロの中心の基準（ここから translation でずらす）。 */
    double[] dice() {
        return new double[]{0, MAT + 0.04 + DIE_SCALE / 2, 0};
    }

    double[] resultLabel() {
        return new double[]{0, 0.75, 0};
    }

    /**
     * 席 seat の前の、賭けの種類 group（0〜7）の置き場所。手前の段が 小・大・奇数・偶数、
     * どんぶり側の段が 目・合計・ダブル・ゾロ目。同じ種類の別の賭けは k で少しずらして重ねる。
     */
    double[] chip(int seat, int group, int k) {
        double a = angle(seat);
        double col = (group % 4 - 1.5) * 0.12 + k * 0.012;
        double row = CHIP_RING - (group / 4) * 0.12;
        double tx = Math.cos(a), tz = -Math.sin(a);
        return new double[]{row * Math.sin(a) + col * tx, MAT + 0.001 + seat * 0.0005, row * Math.cos(a) + col * tz};
    }

    double[] croupierChips() {
        return new double[]{0, MAT + 0.001, -0.72};
    }
}
