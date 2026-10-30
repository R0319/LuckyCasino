package io.github.r0319.luckycasino.paper;

/**
 * チンチロの座敷。4 x 4 の畳の真ん中にどんぶりを置き、まわりに座布団を輪に並べる（子 5 枚と、奥に親の 1 枚）。
 * 子も親も床に座る。チップは各座布団の前の畳に積む。
 * 畳・座布団・どんぶりはリソースパックのモデル（tools/gen_games.py の chinchiro_zashiki_model など）で、
 * 位置の数値はそちらと合わせること。
 */
final class ChinchiroLayout implements TableShape {

    static final int SEATS = 5;
    /** 座敷のモデルの表示倍率（モデル 8 単位 = 1 ブロック）。 */
    static final float ZASHIKI_SCALE = 2f;
    /** サイコロの表示倍率。 */
    static final float DIE_SCALE = 0.12f;
    /** 畳の表面の高さ。 */
    static final double MAT = 0.1;
    private static final double RING = 1.55;
    /** チップを置く輪の半径。 */
    private static final double CHIP_RING = 0.7;
    /** サイコロを投げる手の輪の半径（どんぶりの中心から）。 */
    private static final double HAND_RING = 1.0;
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
        return "chinchiro/zashiki";
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

    /** 席 seat の前の、チップを積む位置。 */
    double[] chip(int seat) {
        double a = angle(seat);
        return new double[]{CHIP_RING * Math.sin(a), MAT + 0.001 + seat * 0.0005, CHIP_RING * Math.cos(a)};
    }

    /** 席 seat（-1 は親）がサイコロを投げる手の、サイコロの基準（dice）からのずれ。 */
    static float[] hand(int seat) {
        double a = seat < 0 ? Math.PI : angle(seat);
        return new float[]{(float) (HAND_RING * Math.sin(a)), 0.4f, (float) (HAND_RING * Math.cos(a))};
    }

    /** 席 seat（-1 は親）の役を出す位置（チップの上）。 */
    double[] handLabel(int seat) {
        double a = seat < 0 ? Math.PI : angle(seat);
        return new double[]{0.7 * Math.sin(a), 0.45, 0.7 * Math.cos(a)};
    }

    double[] croupierChips() {
        return new double[]{0, MAT + 0.001, -0.72};
    }
}
